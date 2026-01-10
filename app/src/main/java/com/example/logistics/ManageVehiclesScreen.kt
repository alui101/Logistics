@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.logistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete // NEW IMPORT
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@Composable
fun ManageVehiclesScreen(
    navController: NavController,
    viewModel: VehicleViewModel = viewModel(),onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    // Form Inputs
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var mileage by remember { mutableStateOf("0") }

    // State for Delete Dialog
    var vehicleToDelete by remember { mutableStateOf<Vehicle?>(null) }

    // Success Message Dialog
    if (state.successMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearMessages() },
            title = { Text("Success") },
            text = { Text(state.successMessage!!) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearMessages()
                    make = ""; model = ""; plate = ""; mileage = "0"
                }) { Text("OK") }
            }
        )
    }

    // Error Message Dialog
    if (state.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearMessages() },
            title = { Text("Error") },
            text = { Text(state.error!!) },
            confirmButton = { TextButton(onClick = { viewModel.clearMessages() }) { Text("OK") } }
        )
    }

    // Delete Confirmation Dialog
    if (vehicleToDelete != null) {
        AlertDialog(
            onDismissRequest = { vehicleToDelete = null },
            title = { Text("Delete Vehicle?") },
            text = { Text("Are you sure you want to delete the ${vehicleToDelete?.make} (${vehicleToDelete?.plateNumber})?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteVehicle(vehicleToDelete!!)
                        vehicleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { vehicleToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Vehicles") },
                navigationIcon = {
                    IconButton(onClick = { navController.safePopBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            // --- ADD VEHICLE SECTION ---
            Card(elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Add New Vehicle", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = make, onValueChange = { make = it },
                            label = { Text("Make") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = model, onValueChange = { model = it },
                            label = { Text("Model") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = plate, onValueChange = { plate = it },
                            label = { Text("Plate #") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = mileage, onValueChange = { mileage = it },
                            label = { Text("Odometer") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.addVehicle(make, model, plate, mileage) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    ) {
                        Text("Add Vehicle")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text("Fleet List", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            // --- VEHICLE LIST SECTION ---
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.vehicles) { vehicle ->
                    VehicleCard(
                        vehicle = vehicle,
                        onDeleteClick = { vehicleToDelete = vehicle }
                    )
                }
            }
        }
    }
}

@Composable
fun VehicleCard(vehicle: Vehicle, onDeleteClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween // Push delete button to right
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalShipping, contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = "${vehicle.make} ${vehicle.model}", style = MaterialTheme.typography.titleMedium)
                    Text(text = "Plate: ${vehicle.plateNumber}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Mileage: ${vehicle.currentMileage}", style = MaterialTheme.typography.bodySmall)
                }
            }
            // Delete Button
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}