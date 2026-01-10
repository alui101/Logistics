@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.logistics

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import java.io.File

@Composable
fun DriverTripDetailScreen(
    navController: NavController,
    tripId: String,
    viewModel: TripViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    // 1. Ensure we have the trip data loaded
    LaunchedEffect(tripId) {
        val cachedTrip = state.trips.find { it.id == tripId }
        if (cachedTrip == null) {
            viewModel.loadSingleTrip(tripId)
        }
    }

    // Find the specific trip from the list
    val trip = state.trips.find { it.id == tripId }

    // State for the 5 required photos
    val requiredPhotos = listOf("Odometer", "Front", "Back", "Left Side", "Right Side")
    val takenPhotos = remember { mutableStateMapOf<String, Uri>() }

    // Temp variables for camera logic
    var currentPhotoLabel by remember { mutableStateOf("") }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // --- HELPER: Create a URI that points to a specific file in Cache ---
    // This naming convention allows the Background Worker to find the file later.
    fun createTempPictureUri(label: String): Uri {
        val fileName = "photo_${label}_${tripId}.jpg"
        val tempFile = File(context.cacheDir, fileName)

        // Clear old file if exists to avoid conflicts
        if (tempFile.exists()) tempFile.delete()
        tempFile.createNewFile()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
    }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null && currentPhotoLabel.isNotEmpty()) {
            takenPhotos[currentPhotoLabel] = tempPhotoUri!!
        }
    }

    fun launchCamera(label: String) {
        currentPhotoLabel = label
        val uri = createTempPictureUri(label)
        tempPhotoUri = uri
        cameraLauncher.launch(uri)
    }

    if (trip == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                Text(state.errorMessage ?: "Trip not found")
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Inspection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Info
            Text("Trip #${trip.id.take(4).uppercase()}", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text("From: ${trip.origin}", style = MaterialTheme.typography.bodyLarge)
            Text("To: ${trip.destination}", style = MaterialTheme.typography.bodyLarge)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Logic: PENDING vs IN PROGRESS
            if (trip.status == "PENDING") {
                Text(
                    "Pre-Trip Photos Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    "Please take all 5 photos to start the trip. These will upload in the background so you can start driving immediately.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Render Photo Buttons
                requiredPhotos.forEach { label ->
                    PhotoSlot(
                        label = label,
                        currentUri = takenPhotos[label],
                        onTakeClick = { launchCamera(label) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- UPLOAD BUTTON ---
                Button(
                    onClick = {
                        if (takenPhotos.size == requiredPhotos.size) {
                            // Call the Background Worker function
                            viewModel.uploadStartPhotosAndBeginTrip(
                                context = context,
                                tripId = trip.id,
                                photoUris = takenPhotos,
                                onSuccess = {
                                    Toast.makeText(context, "Trip Started! Uploading in background...", Toast.LENGTH_LONG).show()
                                    onNavigateBack()
                                }
                            )
                        } else {
                            Toast.makeText(context, "Please take all 5 photos first.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Start Trip (Background Upload)")
                    }
                }

            } else {
                // Trip is already started
                Text("Status: ${trip.status}", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                if (trip.status == "IN_PROGRESS") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "You are currently in transit. Drive safely!",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else if (trip.status == "COMPLETED") {
                    Text("This trip has been completed.", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
fun PhotoSlot(label: String, currentUri: Uri?, onTakeClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTakeClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentUri != null) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = Color(0xFF4CAF50)) // Green
                    Spacer(modifier = Modifier.width(8.dp))
                    // Thumbnail
                    Card(modifier = Modifier.size(40.dp)) {
                        Image(
                            painter = rememberAsyncImagePainter(currentUri),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Take Photo")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }

            if (currentUri == null) {
                Text("Tap to take", style = MaterialTheme.typography.labelSmall)
            } else {
                Text("Retake", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}