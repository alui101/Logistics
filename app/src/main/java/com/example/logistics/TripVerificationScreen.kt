@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.logistics

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import coil.compose.rememberAsyncImagePainter

@Composable
fun TripVerificationScreen(
    navController: NavController,
    tripId: String,
    viewModel: TripViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(tripId) {
        val cachedTrip = state.trips.find { it.id == tripId }
        if (cachedTrip == null) {
            viewModel.loadSingleTrip(tripId)
        }
    }

    val trip = state.trips.find { it.id == tripId }
    var finalMileage by remember { mutableStateOf("") }

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

            // Completion Photos
            Text("Completion Photos", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (trip.completionPhotos.isEmpty()) {
                Text("No completion photos available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            } else {
                val photoLabels = listOf("Odometer", "Front", "Back", "Left Side", "Right Side")
                photoLabels.forEach { label ->
                    val photoUrl = trip.completionPhotos[label]
                    if (photoUrl != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Image(
                                    painter = rememberAsyncImagePainter(photoUrl),
                                    contentDescription = label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentScale = ContentScale.Fit
                                )
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
}
