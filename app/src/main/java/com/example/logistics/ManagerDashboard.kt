@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.logistics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth

@Composable
fun TripManagementScreen(
    navController: NavController,
    auth: FirebaseAuth,
    viewModel: TripViewModel = viewModel(),
    isManagerRoot: Boolean = true,
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

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

    fun resetForm() {
        origin = ""; destination = ""; description = ""
        selectedDriver = null; selectedVehicle = null; selectedLoadType = "Dry Van"
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
                        TextButton(onClick = {
                            auth.signOut()
                            navController.navigate(Constants.ROUTE_LOGIN) { popUpTo(0) }
                        }) { Text("Logout", color = MaterialTheme.colorScheme.onPrimary) }
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
                            if (isEditMode) {
                                viewModel.updateTrip(editingTripId, origin, destination, description, selectedLoadType, selectedDriver, selectedVehicle)
                            } else {
                                viewModel.createTrip(origin, destination, description, selectedLoadType, selectedDriver, selectedVehicle)
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
                            TripManagementCard(
                                trip = trip,
                                onEdit = { onEditClick(trip) },
                                onDelete = { viewModel.deleteTrip(trip) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripManagementCard(trip: Trip, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
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
                        Badge(containerColor = if(trip.status == "PENDING") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer) {
                            Text(trip.status, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${trip.origin} -> ${trip.destination}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Driver: ${trip.driverName ?: "Unassigned"}", style = MaterialTheme.typography.bodyMedium)
                    Text("Vehicle: ${trip.vehicleInfo ?: "None"}", style = MaterialTheme.typography.bodyMedium)
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}