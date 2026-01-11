@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.logistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth

@Composable
fun ErrorScreen(
    message: String,
    navController: NavController,
    auth: FirebaseAuth
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sirat Logistics") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    TextButton(onClick = {
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
                                        navController.navigate("login") {
                                            popUpTo("error") { inclusive = true }
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    // Log with unknown role, then signOut
                                    AuthLogger.logLogout(currentUser.uid, currentUser.email, null, "BUTTON") {
                                        auth.signOut()
                                        navController.navigate("login") {
                                            popUpTo("error") { inclusive = true }
                                        }
                                    }
                                }
                        } else {
                            // No user, just signOut
                            auth.signOut()
                            navController.navigate("login") {
                                popUpTo("error") { inclusive = true }
                            }
                        }
                    }) {
                        Text("Logout", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}
