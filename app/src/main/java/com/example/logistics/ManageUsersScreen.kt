package com.example.logistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class UserItem(
    val uid: String,
    val email: String,
    val role: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUsersScreen(
    navController: NavController,
    auth: FirebaseAuth = FirebaseAuth.getInstance(),onNavigateBack: () -> Unit = {}
) {
    val db = FirebaseFirestore.getInstance()
    var userList by remember { mutableStateOf<List<UserItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) } // NEW: Error state
    var showDeleteDialog by remember { mutableStateOf<UserItem?>(null) }

    val currentUserUid = auth.currentUser?.uid

    LaunchedEffect(Unit) {
        try {
            // Fetch all users
            val result = db.collection("users").get().await()

            // Map documents to UserItem objects
            userList = result.documents.mapNotNull { doc ->
                val email = doc.getString("email")
                val role = doc.getString("role")

                // Debugging: Print to Logcat if a document is skipped
                if (email == null || role == null) {
                    println("Skipping doc ${doc.id}: Missing email or role")
                    null
                } else {
                    UserItem(doc.id, email, role)
                }
            }.filter {
                // Filter out the current user (you)
                it.uid != currentUserUid
            }

            isLoading = false
        } catch (e: Exception) {
            isLoading = false
            errorMessage = e.localizedMessage ?: "Unknown error occurred" // Show the error
            e.printStackTrace()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Users") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            else if (errorMessage != null) {
                // Display the Error if one exists
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Error loading users:", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage!!, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { navController.popBackStack() }) {
                        Text("Go Back")
                    }
                }
            }
            else {
                if (userList.isEmpty()) {
                    Text(
                        text = "No other users found.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(userList) { user ->
                            UserCard(user = user, onDeleteClick = { showDeleteDialog = user })
                        }
                    }
                }
            }
        }

        if (showDeleteDialog != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Delete User?") },
                text = { Text("Are you sure you want to remove ${showDeleteDialog?.email}?") },
                confirmButton = {
                    Button(
                        onClick = {
                            val userToDelete = showDeleteDialog!!
                            db.collection("users").document(userToDelete.uid)
                                .delete()
                                .addOnSuccessListener {
                                    userList = userList.filter { it.uid != userToDelete.uid }
                                    showDeleteDialog = null
                                }
                                .addOnFailureListener {
                                    showDeleteDialog = null
                                }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun UserCard(user: UserItem, onDeleteClick: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // User Icon
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))

                // Email and Role Text
                Column {
                    Text(text = user.email, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = user.role.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
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