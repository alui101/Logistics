@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.logistics

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
// FIX 1: Correct Import for the AutoMirrored icon
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun AddUserScreen(
    navController: NavController,
    auth: FirebaseAuth = FirebaseAuth.getInstance(),onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var selectedRoleDisplay by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val roles = listOf("admin", "manager", "driver")
    val roleDisplay = roles.map { it.replaceFirstChar { c -> c.uppercase() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add User") },
                navigationIcon = {
                    // FIX 2: Use safePopBackStack to prevent crashes
                    IconButton(onClick = { navController.safePopBackStack() }) {
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // Email Input
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Password Input
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Role Dropdown
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedRoleDisplay,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Role") },
                    placeholder = { Text("Select Role") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    roleDisplay.forEachIndexed { index, display ->
                        DropdownMenuItem(
                            text = { Text(display) },
                            onClick = {
                                selectedRoleDisplay = display
                                role = roles[index]
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isLoading = true
                    message = ""

                    // 1. Create a secondary Firebase App instance
                    // This prevents the main app (Admin) from being logged out
                    val appName = "SecondaryAppForUserCreation"
                    val currentOptions = FirebaseApp.getInstance().options

                    val secondaryApp = try {
                        FirebaseApp.getInstance(appName)
                    } catch (e: IllegalStateException) {
                        FirebaseApp.initializeApp(context, currentOptions, appName)
                    }

                    // 2. Get Auth for the secondary app
                    val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

                    // 3. Create the user on the secondary auth
                    secondaryAuth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { result ->
                            val newUid = result.user?.uid

                            if (newUid != null) {
                                // 4. Use the MAIN db instance (Admin) to write data
                                db.collection(Constants.COLLECTION_USERS).document(newUid).set(
                                    mapOf(
                                        "email" to email,
                                        "role" to role,
                                        "uid" to newUid // It is good practice to store the UID inside the doc too
                                    )
                                ).addOnSuccessListener {
                                    message = "User added successfully!"

                                    // Reset fields
                                    email = ""
                                    password = ""
                                    selectedRoleDisplay = ""
                                    role = ""

                                    // Sign out the secondary auth so it doesn't linger
                                    secondaryAuth.signOut()
                                    isLoading = false

                                    Toast.makeText(context, "User Created Successfully", Toast.LENGTH_SHORT).show()

                                    // FIX 3: Use safePopBackStack here too
                                    navController.safePopBackStack()
                                }.addOnFailureListener { e ->
                                    message = "User created, but DB failed: ${e.localizedMessage}"
                                    isLoading = false
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            message = "Failed to create user: ${e.localizedMessage}"
                            isLoading = false
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = role.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Add User")
                }
            }

            if (message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}