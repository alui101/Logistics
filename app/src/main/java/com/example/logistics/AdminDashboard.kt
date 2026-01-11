@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.logistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.ImageLoader
import com.google.firebase.auth.FirebaseAuth
import android.widget.Toast
@Composable
fun AdminDashboard(navController: NavController, auth: FirebaseAuth,onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val imageLoader = remember { ImageLoaderConfig.createImageLoader(context) }
    var showCacheClearDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sirat Logistics - Admin") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { showCacheClearDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Clear Cache", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    TextButton(onClick = {
                        auth.signOut()
                        navController.navigate(Constants.ROUTE_LOGIN) {
                            popUpTo(0)
                        }
                    }) {
                        Text("Logout", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()), // Make screen scrollable
            verticalArrangement = Arrangement.spacedBy(16.dp) // Space between cards
        ) {

            // --- CARD 1: USER MANAGEMENT ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("User Management", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Button: Add User
                    Button(
                        onClick = { navController.navigate(Constants.ROUTE_ADD_USER) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Driver/Admin/Manager")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Button: Delete/Manage Users
                    OutlinedButton(
                        onClick = { navController.navigate(Constants.ROUTE_MANAGE_USERS) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete / Manage Users")
                    }
                }
            }

            // --- CARD 2: FLEET MANAGEMENT ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Fleet Management", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { navController.navigate(Constants.ROUTE_MANAGE_VEHICLES) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Manage Vehicles")
                    }
                }
            }

            // --- CARD 3: TRIP LOGISTICS (NEW) ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Trip Logistics", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { navController.navigate(Constants.ROUTE_ADMIN_TRIPS) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create & Assign Trips")
                    }
                }
            }
        }
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
}