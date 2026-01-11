@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.logistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.CachePolicy
import coil.size.Size
import com.google.firebase.auth.FirebaseAuth
import android.widget.Toast

@Composable
fun TripManagementScreen(
    navController: NavController,
    auth: FirebaseAuth,
    viewModel: TripViewModel = viewModel(),
    isManagerRoot: Boolean = true,
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val imageLoader = remember { ImageLoaderConfig.createImageLoader(context) }
    var showCacheClearDialog by remember { mutableStateOf(false) }
    var showMenuDropdown by remember { mutableStateOf(false) }

    // -- STATE VARIABLES --
    var showForm by remember { mutableStateOf(false) }

    // Form Data State
    var isEditMode by remember { mutableStateOf(false) }
    var editingTripId by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedDriver by remember { mutableStateOf<UserItem?>(null) }
    var selectedVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var selectedLoadType by remember { mutableStateOf("Dry Van") }
    var foodLimit by remember { mutableStateOf("") }

    // UI Visibility State
    var driverExpanded by remember { mutableStateOf(false) }
    var vehicleExpanded by remember { mutableStateOf(false) }
    var loadTypeExpanded by remember { mutableStateOf(false) }

    val loadTypes = listOf("Dry Van", "Vegetable", "Frozen", "Ambient")

    // Initialize Data
    LaunchedEffect(Unit) {
        viewModel.loadDataForManager()
        viewModel.loadAllTrips()
    }
    
    // Preload images when trips are loaded
    LaunchedEffect(state.trips.size) {
        state.trips.forEach { trip ->
            // Preload start photos
            trip.startPhotos.values.forEach { url ->
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
            trip.completionPhotos.values.forEach { url ->
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
            trip.expenses.forEach { expense ->
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

    fun resetForm() {
        origin = ""; destination = ""; description = ""
        selectedDriver = null; selectedVehicle = null; selectedLoadType = "Dry Van"
        foodLimit = ""
        isEditMode = false; editingTripId = ""
    }

    fun onEditClick(trip: Trip) {
        resetForm()
        isEditMode = true
        editingTripId = trip.id
        origin = trip.origin
        destination = trip.destination
        description = trip.description
        selectedLoadType = trip.loadType
        foodLimit = if (trip.foodLimit > 0) trip.foodLimit.toString() else ""
        selectedDriver = state.drivers.find { it.uid == trip.driverId }
        selectedVehicle = state.vehicles.find { it.id == trip.vehicleId }
        showForm = true
    }

    // Success Dialog logic
    if (state.successMessage != null) {
        AlertDialog(
            onDismissRequest = { /* Force choice */ },
            title = { Text(if (isEditMode) "Trip Updated" else "Trip Created") },
            text = { Text(state.successMessage!!) },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearMessages()
                    showForm = false
                    resetForm()
                }) {
                    Text("Exit to List")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.clearMessages()
                    resetForm()
                    isEditMode = false
                }) {
                    Text("Make Another")
                }
            }
        )
    }

    if (state.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearMessages() },
            title = { Text("Error") },
            text = { Text(state.errorMessage!!) },
            confirmButton = { TextButton(onClick = { viewModel.clearMessages() }) { Text("OK") } }
        )
    }
    
    // Cache clear confirmation dialog
    if (showCacheClearDialog) {
        AlertDialog(
            onDismissRequest = { showCacheClearDialog = false },
            title = { Text("Clear Image Cache") },
            text = { Text("This will clear all cached images. Images will need to be reloaded from the network. Continue?") },
            confirmButton = {
                Button(onClick = {
                    ImageLoaderConfig.clearCache(context, imageLoader)
                    Toast.makeText(context, "Image cache cleared", Toast.LENGTH_SHORT).show()
                    showCacheClearDialog = false
                }) {
                    Text("Clear Cache")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCacheClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showForm) {
                        Text(if (isEditMode) "Edit Trip" else "Create New Trip")
                    } else {
                        Text(if (isManagerRoot) "Manager Dashboard" else "All Trips")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    if (showForm) {
                        IconButton(onClick = { showForm = false; resetForm() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to List")
                        }
                    } else if (!isManagerRoot) {
                        // FIX IS HERE: Change .popBackStack() to .safePopBackStack()
                        IconButton(onClick = { navController.safePopBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Admin Dashboard")
                        }
                    }
                },
                actions = {
                    if (isManagerRoot && !showForm) {
                        Box {
                            IconButton(onClick = { showMenuDropdown = true }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            DropdownMenu(
                                expanded = showMenuDropdown,
                                onDismissRequest = { showMenuDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Clear Cache") },
                                    onClick = {
                                        showMenuDropdown = false
                                        showCacheClearDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Logout") },
                                    onClick = {
                                        showMenuDropdown = false
                                        // Log logout before signOut - must complete before signOut
                                        val currentUser = auth.currentUser
                                        if (currentUser != null) {
                                            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                            db.collection(Constants.COLLECTION_USERS)
                                                .document(currentUser.uid)
                                                .get()
                                                .addOnSuccessListener { doc ->
                                                    val role = doc.getString("role") ?: "unknown"
                                                    AuthLogger.logLogout(currentUser.uid, currentUser.email, role, "BUTTON") {
                                                        // Only signOut after logout is logged
                                                        viewModel.cleanupListeners()
                                                        auth.signOut()
                                                        navController.navigate(Constants.ROUTE_LOGIN) { popUpTo(0) }
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    // Log with unknown role, then signOut
                                                    AuthLogger.logLogout(currentUser.uid, currentUser.email, null, "BUTTON") {
                                                        viewModel.cleanupListeners()
                                                        auth.signOut()
                                                        navController.navigate(Constants.ROUTE_LOGIN) { popUpTo(0) }
                                                    }
                                                }
                                        } else {
                                            // No user, just signOut
                                            viewModel.cleanupListeners()
                                            auth.signOut()
                                            navController.navigate(Constants.ROUTE_LOGIN) { popUpTo(0) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!showForm) {
                FloatingActionButton(
                    onClick = {
                        resetForm()
                        showForm = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Trip")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {

            if (showForm) {
                // --- VIEW A: THE FORM ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Origin & Destination
                    OutlinedTextField(value = origin, onValueChange = { origin = it }, label = { Text("Origin") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = destination, onValueChange = { destination = it }, label = { Text("Destination") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))

                    // Load Type
                    ExposedDropdownMenuBox(
                        expanded = loadTypeExpanded,
                        onExpandedChange = { loadTypeExpanded = !loadTypeExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedLoadType, onValueChange = {}, readOnly = true, label = { Text("Load Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = loadTypeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = loadTypeExpanded, onDismissRequest = { loadTypeExpanded = false }) {
                            loadTypes.forEach { type ->
                                DropdownMenuItem(text = { Text(type) }, onClick = { selectedLoadType = type; loadTypeExpanded = false })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))

                    // Food Limit
                    OutlinedTextField(
                        value = foodLimit,
                        onValueChange = { foodLimit = it },
                        label = { Text("Food Expense Limit (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                        placeholder = { Text("e.g., 100.00") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Driver
                    ExposedDropdownMenuBox(
                        expanded = driverExpanded,
                        onExpandedChange = { driverExpanded = !driverExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedDriver?.email ?: "Unassigned", onValueChange = {}, readOnly = true, label = { Text("Assign Driver") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = driverExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = driverExpanded, onDismissRequest = { driverExpanded = false }) {
                            state.drivers.forEach { driver ->
                                DropdownMenuItem(text = { Text(driver.email) }, onClick = { selectedDriver = driver; driverExpanded = false })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Vehicle
                    ExposedDropdownMenuBox(
                        expanded = vehicleExpanded,
                        onExpandedChange = { vehicleExpanded = !vehicleExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedVehicle?.displayName() ?: "No Vehicle", onValueChange = {}, readOnly = true, label = { Text("Assign Vehicle") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = vehicleExpanded, onDismissRequest = { vehicleExpanded = false }) {
                            state.vehicles.forEach { vehicle ->
                                DropdownMenuItem(text = { Text(vehicle.displayName()) }, onClick = { selectedVehicle = vehicle; vehicleExpanded = false })
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val foodLimitValue = foodLimit.toDoubleOrNull() ?: 0.0
                            if (isEditMode) {
                                viewModel.updateTrip(editingTripId, origin, destination, description, selectedLoadType, foodLimitValue, selectedDriver, selectedVehicle)
                            } else {
                                viewModel.createTrip(origin, destination, description, selectedLoadType, foodLimitValue, selectedDriver, selectedVehicle)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    ) {
                        Text(if (isEditMode) "Update Trip" else "Create Trip")
                    }

                    if (state.isLoading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            } else {
                // --- VIEW B: THE LIST ---
                if (state.isLoading && state.trips.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (state.trips.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No trips found.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Click + to create one.", color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.trips) { trip ->
                            val onDeleteCallback: (() -> Unit)? = if (isManagerRoot) {
                                null
                            } else {
                                { viewModel.deleteTrip(trip) }
                            }
                            TripManagementCard(
                                trip = trip,
                                onEdit = { onEditClick(trip) },
                                onDelete = onDeleteCallback, // Only admins can delete
                                onVerify = if (trip.status == "AWAITING_VERIFICATION") {
                                    { navController.navigate("${Constants.ROUTE_TRIP_VERIFICATION}/${trip.id}") }
                                } else null,
                                onViewDetails = if (trip.status == "COMPLETED") {
                                    { navController.navigate("${Constants.ROUTE_TRIP_VERIFICATION}/${trip.id}") }
                                } else null,
                                onViewPhotos = {
                                    // Will be handled in the card
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripManagementCard(
    trip: Trip, 
    onEdit: () -> Unit, 
    onDelete: (() -> Unit)? = null, // Make nullable - only admins can delete
    onVerify: (() -> Unit)? = null,
    onViewDetails: (() -> Unit)? = null, // For finalized trips
    onViewPhotos: () -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPhotosDialog by remember { mutableStateOf(false) }
    var selectedPhotoUrl by remember { mutableStateOf<String?>(null) }
    var selectedPhotoLabel by remember { mutableStateOf<String?>(null) }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Trip?") },
            text = { Text("Are you sure you want to delete the trip from ${trip.origin} to ${trip.destination}?") },
            confirmButton = {
                Button(onClick = { onDelete(); showDeleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Trip #${trip.id.take(4).uppercase()}", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(
                            containerColor = when(trip.status) {
                                "PENDING" -> MaterialTheme.colorScheme.secondaryContainer
                                "IN_PROGRESS" -> MaterialTheme.colorScheme.primaryContainer
                                "AWAITING_VERIFICATION" -> MaterialTheme.colorScheme.tertiaryContainer
                                "COMPLETED" -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            }
                        ) {
                            Text(trip.status, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${trip.origin} -> ${trip.destination}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Driver: ${trip.driverName ?: "Unassigned"}", style = MaterialTheme.typography.bodyMedium)
                    Text("Vehicle: ${trip.vehicleInfo ?: "None"}", style = MaterialTheme.typography.bodyMedium)
                    
                    // Show start/stop times
                    if (trip.startedAt != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Started: ${trip.startedAt.toDate().toString().substring(0, 16)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (trip.stoppedAt != null) {
                        Text(
                            "Stopped: ${trip.stoppedAt.toDate().toString().substring(0, 16)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    
                    // Show expenses total if any
                    if (trip.expenses.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val totalExpenses = trip.expenses.sumOf { it.amount }
                        Text(
                            "Total Expenses: $${String.format("%.2f", totalExpenses)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Show View Details button for completed trips, or View Photos for others
                    if (trip.status == "COMPLETED" && onViewDetails != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = onViewDetails,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View Details", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        val hasPhotos = trip.startPhotos.isNotEmpty() || trip.completionPhotos.isNotEmpty() || trip.expenses.any { it.receiptPhotoUrl != null }
                        if (hasPhotos) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = { showPhotosDialog = true },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("View Photos", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Column {
                    if (trip.status == "AWAITING_VERIFICATION" && onVerify != null) {
                        Button(
                            onClick = onVerify,
                            modifier = Modifier.padding(bottom = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Text("Verify", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    // Don't show edit/delete buttons for completed trips
                    if (trip.status != "COMPLETED") {
                        Row {
                            IconButton(onClick = onEdit) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                            }
                            // Only show delete button if onDelete is provided (admins only)
                            if (onDelete != null) {
                                IconButton(onClick = { showDeleteConfirm = true }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Photos Dialog
    if (showPhotosDialog) {
        TripPhotosDialog(
            trip = trip,
            onDismiss = { showPhotosDialog = false },
            onPhotoClick = { label, url ->
                selectedPhotoLabel = label
                selectedPhotoUrl = url
            }
        )
    }
    
    // Individual Photo Dialog
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
fun TripPhotosDialog(
    trip: Trip,
    onDismiss: () -> Unit,
    onPhotoClick: (String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trip Photos") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Start Photos
                if (trip.startPhotos.isNotEmpty()) {
                    Text("Start Photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val photoLabels = listOf("Odometer", "Front", "Back", "Left Side", "Right Side")
                    photoLabels.forEach { label ->
                        val photoUrl = trip.startPhotos[label]
                        if (photoUrl != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPhotoClick(label, photoUrl) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { onPhotoClick(label, photoUrl) }) {
                                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("View")
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Completion Photos
                if (trip.completionPhotos.isNotEmpty()) {
                    if (trip.startPhotos.isNotEmpty()) {
                        HorizontalDivider()
                    }
                    Text("Completion Photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val photoLabels = listOf("Odometer", "Front", "Back", "Left Side", "Right Side")
                    photoLabels.forEach { label ->
                        val photoUrl = trip.completionPhotos[label]
                        if (photoUrl != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPhotoClick(label, photoUrl) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { onPhotoClick(label, photoUrl) }) {
                                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("View")
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Expense Receipts
                val expensesWithReceipts = trip.expenses.filter { it.receiptPhotoUrl != null }
                if (expensesWithReceipts.isNotEmpty()) {
                    if (trip.startPhotos.isNotEmpty() || trip.completionPhotos.isNotEmpty()) {
                        HorizontalDivider()
                    }
                    Text("Expense Receipts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    expensesWithReceipts.forEach { expense ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPhotoClick(
                                        "${expense.type.replaceFirstChar { it.uppercase() }} Receipt",
                                        expense.receiptPhotoUrl!!
                                    )
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${expense.type.replaceFirstChar { it.uppercase() }} - $${String.format("%.2f", expense.amount)}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (expense.description.isNotEmpty()) {
                                        Text(
                                            expense.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        onPhotoClick(
                                            "${expense.type.replaceFirstChar { it.uppercase() }} Receipt",
                                            expense.receiptPhotoUrl!!
                                        )
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
                
                if (trip.startPhotos.isEmpty() && trip.completionPhotos.isEmpty() && expensesWithReceipts.isEmpty()) {
                    Text("No photos available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}