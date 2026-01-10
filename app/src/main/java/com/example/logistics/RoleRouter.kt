package com.example.logistics

import androidx.compose.runtime.*
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@Composable
fun RoleRouter(auth: FirebaseAuth, db: FirebaseFirestore, navController: NavController) {
    var role by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(auth.currentUser?.uid) {
        isLoading = true
        auth.currentUser?.uid?.let { uid ->
            try {
                // Fetch the user's role from Firestore
                val doc = db.collection(Constants.COLLECTION_USERS).document(uid).get().await()
                role = doc.getString("role")
            } catch (e: Exception) {
                role = null
            }
        }
        isLoading = false
    }

    // Decide where to go based on role
    LaunchedEffect(role) {
        if (!isLoading) {
            when (role) {
                // Using Constants ensures these match AppNavigation.kt
                Constants.ROLE_ADMIN -> navController.navigate(Constants.ROUTE_ADMIN) {
                    popUpTo(Constants.ROUTE_LOADING) { inclusive = true }
                }
                Constants.ROLE_MANAGER -> navController.navigate(Constants.ROUTE_MANAGER) {
                    popUpTo(Constants.ROUTE_LOADING) { inclusive = true }
                }
                Constants.ROLE_DRIVER -> navController.navigate(Constants.ROUTE_DRIVER) {
                    popUpTo(Constants.ROUTE_LOADING) { inclusive = true }
                }
                null -> {
                    // Stay on loading or show error?
                    // Usually we navigate to an error screen or back to login if role is missing
                }
            }
        }
    }

    if (isLoading) {
        LoadingScreen()
    } else if (role == null) {
        ErrorScreen(
            message = "Unknown role or error. Contact admin.",
            navController = navController,
            auth = auth
        )
    }
}