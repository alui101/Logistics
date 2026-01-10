package com.example.logistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun DashboardScreen(onLogout: () -> Unit, onAddUser: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val isAdmin = user?.email == "admin@example.com" // Simple admin check

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Welcome to Sirat Dashboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (isAdmin) {
            Button(onClick = onAddUser) {
                Text("Add User")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(onClick = onLogout) {
            Text("Logout")
        }
    }
}