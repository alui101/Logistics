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
                        auth.signOut()
                        navController.navigate("login") {
                            popUpTo("error") { inclusive = true }
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
