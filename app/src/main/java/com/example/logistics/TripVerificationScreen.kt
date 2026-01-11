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
import androidx.compose.material.icons.filled.AttachMoney
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
        // Load vehicles to get current mileage for validation
        if (state.vehicles.isEmpty()) {
            viewModel.loadDataForManager()
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
    var finalMileage by remember { mutableStateOf(trip?.finalMileage?.toString() ?: "") }
    var moneyEarned by remember { mutableStateOf(trip?.moneyEarned?.toString() ?: "") }
    var additionalCosts by remember { mutableStateOf(trip?.additionalCosts?.toString() ?: "") }
    var selectedPhotoUrl by remember { mutableStateOf<String?>(null) }
    var selectedPhotoLabel by remember { mutableStateOf<String?>(null) }
    
    // Update fields when trip data changes
    LaunchedEffect(trip?.finalMileage, trip?.moneyEarned, trip?.additionalCosts) {
        trip?.let {
            if (it.finalMileage != null) finalMileage = it.finalMileage.toString()
            if (it.moneyEarned != null) moneyEarned = it.moneyEarned.toString()
            if (it.additionalCosts != null) additionalCosts = it.additionalCosts.toString()
        }
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

    if (trip.status != "AWAITING_VERIFICATION" && trip.status != "COMPLETED") {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("This trip is not awaiting verification", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Status: ${trip.status}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    
    val isCompleted = trip.status == "COMPLETED"
    // Always show details by default - when navigating from list, details should be visible immediately
    var showDetails by remember { mutableStateOf(true) }
    
    // Get the vehicle for this trip to check current mileage
    val vehicle = trip.vehicleId?.let { vehicleId ->
        state.vehicles.find { it.id == vehicleId }
    }
    
    // Validate mileage: finalMileage must be >= vehicle.currentMileage
    val mileageValidationError = remember(finalMileage, vehicle) {
        if (!isCompleted && vehicle != null && finalMileage.isNotBlank()) {
            val mileage = finalMileage.toIntOrNull()
            if (mileage != null && mileage < vehicle.currentMileage) {
                "Final mileage ($mileage) cannot be less than vehicle's current mileage (${vehicle.currentMileage})"
            } else null
        } else null
    }
    
    // Initialize and update fields when trip data changes
    LaunchedEffect(trip.finalMileage, trip.moneyEarned, trip.additionalCosts) {
        if (trip.finalMileage != null) finalMileage = trip.finalMileage.toString()
        if (trip.moneyEarned != null) moneyEarned = trip.moneyEarned.toString()
        if (trip.additionalCosts != null) additionalCosts = trip.additionalCosts.toString()
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
            // Trip Finalized Message and View Details Button
            if (isCompleted && !showDetails) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Trip Finalized",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showDetails = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Details")
                        }
                    }
                }
            } else {
                // All details below (shown when not completed OR when showDetails is true for completed trips)
                
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
                        onPhotoClick = { label: String, url: String ->
                            if (!isCompleted) {
                                selectedPhotoLabel = label
                                selectedPhotoUrl = url
                            }
                        },
                        isDisabled = isCompleted
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
                        onPhotoClick = { label: String, url: String ->
                            if (!isCompleted) {
                                selectedPhotoLabel = label
                                selectedPhotoUrl = url
                            }
                        },
                        isDisabled = isCompleted
                    )
                }

                // Driver Expenses Section
                if (trip.expenses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Driver Expenses", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    trip.expenses.forEach { expense ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .then(
                                if (expense.receiptPhotoUrl != null && !isCompleted) {
                                    Modifier.clickable {
                                        selectedPhotoLabel = "${expense.type.replaceFirstChar { it.uppercase() }} Receipt"
                                        selectedPhotoUrl = expense.receiptPhotoUrl
                                    }
                                } else {
                                    Modifier
                                }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCompleted) 
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        )
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
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                                )
                                if (expense.description.isNotEmpty()) {
                                    Text(
                                        text = expense.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            if (expense.receiptPhotoUrl != null && !isCompleted) {
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
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Driver Expenses:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "$${String.format("%.2f", trip.expenses.sumOf { it.amount })}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Financial Information Section
                Text("Financial Information", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                // Money Earned Entry
                OutlinedTextField(
                    value = moneyEarned,
                    onValueChange = { if (!isCompleted) moneyEarned = it },
                    label = { Text("Money Earned ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("e.g., 1500.00") },
                    leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                    enabled = !isCompleted,
                    readOnly = isCompleted
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Additional Costs Entry
                OutlinedTextField(
                    value = additionalCosts,
                    onValueChange = { if (!isCompleted) additionalCosts = it },
                    label = { Text("Additional Costs ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("e.g., 50.00") },
                    leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                    enabled = !isCompleted,
                    readOnly = isCompleted
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Final Mileage Entry
                Text("Enter Final Mileage", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Show current vehicle mileage if available
                if (vehicle != null && !isCompleted) {
                    Text(
                        "Current Vehicle Mileage: ${vehicle.currentMileage}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                OutlinedTextField(
                    value = finalMileage,
                    onValueChange = { if (!isCompleted) finalMileage = it },
                    label = { Text("Final Mileage") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("e.g., 125000") },
                    enabled = !isCompleted,
                    readOnly = isCompleted,
                    isError = mileageValidationError != null,
                    supportingText = mileageValidationError?.let { { Text(it) } }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Finalize/Un-finalize Button
                if (isCompleted) {
                    // Un-finalize Button (only shown when viewing details)
                    OutlinedButton(
                        onClick = {
                            viewModel.unfinalizeTrip(trip.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text("Un-finalize Trip")
                        }
                    }
                } else {
                    // Finalize Button
                    Button(
                        onClick = {
                            val mileage = finalMileage.toIntOrNull()
                            if (mileage == null) {
                                // Show error - handled by viewModel
                                return@Button
                            }
                            // Additional validation: check mileage against vehicle
                            if (vehicle != null && mileage < vehicle.currentMileage) {
                                // Error will be shown in the text field
                                return@Button
                            }
                            val earned = moneyEarned.toDoubleOrNull() ?: 0.0
                            val additional = additionalCosts.toDoubleOrNull() ?: 0.0
                            viewModel.verifyAndFinalizeTrip(trip.id, mileage, earned, additional)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading && 
                                 finalMileage.toIntOrNull() != null && 
                                 trip.completionPhotos.isNotEmpty() &&
                                 mileageValidationError == null
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
                }
            }

            // Success/Error Messages
            LaunchedEffect(state.successMessage) {
                state.successMessage?.let {
                    // Reload trip data after successful finalization
                    viewModel.loadSingleTrip(tripId)
                }
            }
            
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
    onPhotoClick: (String, String) -> Unit,
    isDisabled: Boolean = false
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
                    .then(
                        if (!isDisabled) {
                            Modifier.clickable { onPhotoClick(label, photoUrl) }
                        } else {
                            Modifier
                        }
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDisabled) 
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle, 
                                contentDescription = null, 
                                tint = if (isDisabled) 
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) 
                                else 
                                    MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                label, 
                                style = MaterialTheme.typography.titleSmall, 
                                fontWeight = FontWeight.Bold,
                                color = if (isDisabled) 
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) 
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (!isDisabled) {
                            TextButton(onClick = { onPhotoClick(label, photoUrl) }) {
                                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("View")
                            }
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
