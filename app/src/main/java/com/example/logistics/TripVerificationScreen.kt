@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.logistics

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.request.CachePolicy
import coil.size.Size
import coil.size.Scale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader

@Composable
fun TripVerificationScreen(
    navController: NavController,
    tripId: String,
    viewModel: TripViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val imageLoader = remember { ImageLoaderConfig.createImageLoader(context) }
    
    LaunchedEffect(tripId) {
        val cachedTrip = state.trips.find { it.id == tripId }
        if (cachedTrip == null) {
            viewModel.loadSingleTrip(tripId)
        }
    }

    val trip = state.trips.find { it.id == tripId }
    
    // Preload images when trip data is available
    LaunchedEffect(trip?.id) {
        trip?.let { currentTrip ->
            // Preload start photos
            currentTrip.startPhotos.values.forEach { url ->
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        .size(Size(800, 600))
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                )
            }
            // Preload completion photos
            currentTrip.completionPhotos.values.forEach { url ->
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        .size(Size(800, 600))
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                )
            }
            // Preload expense receipt photos
            currentTrip.expenses.forEach { expense ->
                expense.receiptPhotoUrl?.let { url ->
                    imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            .size(Size(800, 600))
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    )
                }
            }
        }
    }
    var finalMileage by remember { mutableStateOf("") }
    var selectedPhotoUrl by remember { mutableStateOf<String?>(null) }
    var selectedPhotoLabel by remember { mutableStateOf<String?>(null) }

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

    if (trip.status != "AWAITING_VERIFICATION") {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("This trip is not awaiting verification", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Status: ${trip.status}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify Trip Completion") },
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
            // Trip Info
            Text("Trip #${trip.id.take(4).uppercase()}", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text("From: ${trip.origin}", style = MaterialTheme.typography.bodyLarge)
            Text("To: ${trip.destination}", style = MaterialTheme.typography.bodyLarge)
            
            if (trip.startedAt != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Started: ${trip.startedAt.toDate().toString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (trip.stoppedAt != null) {
                Text(
                    "Stopped: ${trip.stoppedAt.toDate().toString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Start Photos Section
            if (trip.startPhotos.isNotEmpty()) {
                Text("Start Photos", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                PhotoGrid(
                    photos = trip.startPhotos,
                    onPhotoClick = { label, url ->
                        selectedPhotoLabel = label
                        selectedPhotoUrl = url
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Completion Photos
            Text("Completion Photos", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (trip.completionPhotos.isEmpty()) {
                Text("No completion photos available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            } else {
                PhotoGrid(
                    photos = trip.completionPhotos,
                    onPhotoClick = { label, url ->
                        selectedPhotoLabel = label
                        selectedPhotoUrl = url
                    }
                )
            }

            // Expense Receipts Section
            if (trip.expenses.isNotEmpty() && trip.expenses.any { it.receiptPhotoUrl != null }) {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Expense Receipts", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                trip.expenses.forEach { expense ->
                    if (expense.receiptPhotoUrl != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedPhotoLabel = "${expense.type.replaceFirstChar { it.uppercase() }} Receipt"
                                    selectedPhotoUrl = expense.receiptPhotoUrl
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${expense.type.replaceFirstChar { it.uppercase() }} - $${String.format("%.2f", expense.amount)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (expense.description.isNotEmpty()) {
                                        Text(
                                            text = expense.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        selectedPhotoLabel = "${expense.type.replaceFirstChar { it.uppercase() }} Receipt"
                                        selectedPhotoUrl = expense.receiptPhotoUrl
                                    }
                                ) {
                                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("View Receipt")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Final Mileage Entry
            Text("Enter Final Mileage", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = finalMileage,
                onValueChange = { finalMileage = it },
                label = { Text("Final Mileage") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("e.g., 125000") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Finalize Button
            Button(
                onClick = {
                    val mileage = finalMileage.toIntOrNull()
                    if (mileage == null) {
                        // Show error - handled by viewModel
                        return@Button
                    }
                    viewModel.verifyAndFinalizeTrip(trip.id, mileage)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading && finalMileage.toIntOrNull() != null && trip.completionPhotos.isNotEmpty()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text("Finalize Trip")
                }
            }

            // Success/Error Messages
            if (state.successMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        state.successMessage!!,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        state.errorMessage!!,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    // Photo Dialog
    if (selectedPhotoUrl != null && selectedPhotoLabel != null) {
        PhotoDialog(
            photoUrl = selectedPhotoUrl!!,
            label = selectedPhotoLabel!!,
            onDismiss = {
                selectedPhotoUrl = null
                selectedPhotoLabel = null
            }
        )
    }
}

@Composable
fun PhotoGrid(
    photos: Map<String, String>,
    onPhotoClick: (String, String) -> Unit
) {
    val context = LocalContext.current
    val imageLoader = remember { ImageLoaderConfig.createImageLoader(context) }
    
    val photoLabels = listOf("Odometer", "Front", "Back", "Left Side", "Right Side")
    photoLabels.forEach { label ->
        val photoUrl = photos[label]
        if (photoUrl != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onPhotoClick(label, photoUrl) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { onPhotoClick(label, photoUrl) }) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photoUrl)
                            .size(Size(800, 600)) // Resize for thumbnails - smaller = faster
                            .scale(Scale.FIT)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentScale = ContentScale.Fit,
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Failed to load image",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoDialog(
    photoUrl: String,
    label: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val imageLoader = remember { ImageLoaderConfig.createImageLoader(context) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photoUrl)
                        .size(Size(1200, 1200)) // Higher resolution for full view
                        .scale(Scale.FIT)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Loading image...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    },
                    error = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                "Failed to load image",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
