@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.logistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material.icons.filled.Menu

@Composable
fun DriverDashboard(
    navController: NavController,
    auth: FirebaseAuth,
    viewModel: TripViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDriverTrips()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Trips") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary // White icon
                ),
                actions = {
                    // CHANGED: Menu Icon instead of Logout Text
                    IconButton(onClick = { navController.navigate(Constants.ROUTE_DRIVER_MENU) }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.trips.isEmpty()) {
                Text("No trips assigned yet.", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.trips) { trip ->
                        DriverTripCard(
                            trip = trip,
                            // FIX: Pass the click event here, using the Constant
                            onViewDetails = {
                                navController.navigate("${Constants.ROUTE_DRIVER_TRIP_DETAIL}/${trip.id}")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DriverTripCard(trip: Trip, onViewDetails: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRIP #${trip.id.take(4).uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Badge(
                    containerColor = if (trip.status == "PENDING") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(trip.status, modifier = Modifier.padding(4.dp))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Route
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = "From", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(trip.origin, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }

            Text("  ↓", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = "To", tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(trip.destination, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onViewDetails, // FIX: Use the passed lambda
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Details / Update")
            }
        }
    }
}