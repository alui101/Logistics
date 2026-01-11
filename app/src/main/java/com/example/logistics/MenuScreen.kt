package com.example.logistics

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.Lifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    navController: NavController,
    auth: FirebaseAuth
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Menu") },
                navigationIcon = {
                    IconButton(onClick = {
                        // FIX: Only pop if the screen is currently valid/resumed
                        if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                            navController.popBackStack()
                        }
                    }) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Option 1: Media Manager (Useful for checking upload status)
            MenuOptionCard(
                title = "Media Manager",
                subtitle = "Check status of photo uploads",
                icon = Icons.Filled.Cached,
                onClick = { navController.navigate(Constants.ROUTE_MEDIA_MANAGER) }
            )

            // Option 2: Clear Cache (Useful for everyone to save space)
            MenuOptionCard(
                title = "Clear Cache",
                subtitle = "Delete temporary files to free up space",
                icon = Icons.Filled.Delete,
                onClick = {
                    try {
                        context.cacheDir.deleteRecursively()
                        Toast.makeText(context, "Cache Cleared!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error clearing cache", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Option 3: Logout
            Button(
                onClick = {
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
                                    auth.signOut()
                                    navController.navigate(Constants.ROUTE_LOGIN) {
                                        popUpTo(0)
                                    }
                                }
                            }
                            .addOnFailureListener {
                                // Log with unknown role, then signOut
                                AuthLogger.logLogout(currentUser.uid, currentUser.email, null, "BUTTON") {
                                    auth.signOut()
                                    navController.navigate(Constants.ROUTE_LOGIN) {
                                        popUpTo(0)
                                    }
                                }
                            }
                    } else {
                        // No user, just signOut
                        auth.signOut()
                        navController.navigate(Constants.ROUTE_LOGIN) {
                            popUpTo(0)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out")
            }
        }
    }
}

@Composable
fun MenuOptionCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

