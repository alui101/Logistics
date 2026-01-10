@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.logistics

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import java.io.File

@Composable
fun DriverTripDetailScreen(
    navController: NavController,
    tripId: String,
    viewModel: TripViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    // 1. Ensure we have the trip data loaded
    LaunchedEffect(tripId) {
        val cachedTrip = state.trips.find { it.id == tripId }
        if (cachedTrip == null) {
            viewModel.loadSingleTrip(tripId)
        }
    }

    // Find the specific trip from the list
    val trip = state.trips.find { it.id == tripId }

    // Show error and success messages
    LaunchedEffect(state.errorMessage, state.successMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
        state.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    // State for the 5 required photos (start or completion)
    val requiredPhotos = listOf("Odometer", "Front", "Back", "Left Side", "Right Side")
    val takenPhotos = remember { mutableStateMapOf<String, Uri>() }
    val completionPhotos = remember { mutableStateMapOf<String, Uri>() }

    // Expense entry state
    var showExpenseDialog by remember { mutableStateOf(false) }
    var selectedExpenseType by remember { mutableStateOf("fuel") }
    var expenseAmount by remember { mutableStateOf("") }
    var expenseDescription by remember { mutableStateOf("") }
    var expenseReceiptUri by remember { mutableStateOf<Uri?>(null) }
    val expenseTypes = listOf("fuel", "visas", "tips", "hotel", "food", "repairs")
    val expenseTypesRequiringPhoto = listOf("fuel", "visas", "repairs")

    // Temp variables for camera logic
    var currentPhotoLabel by remember { mutableStateOf("") }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isTakingExpenseReceipt by remember { mutableStateOf(false) }

    // --- HELPER: Create a URI that points to a specific file in Cache ---
    // This naming convention allows the Background Worker to find the file later.
    fun createTempPictureUri(label: String): Uri {
        val fileName = "photo_${label}_${tripId}.jpg"
        val tempFile = File(context.cacheDir, fileName)

        // Clear old file if exists to avoid conflicts
        if (tempFile.exists()) tempFile.delete()
        tempFile.createNewFile()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
    }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            if (isTakingExpenseReceipt) {
                expenseReceiptUri = tempPhotoUri
                isTakingExpenseReceipt = false
            } else if (currentPhotoLabel.isNotEmpty()) {
                if (trip?.status == "PENDING") {
                    takenPhotos[currentPhotoLabel] = tempPhotoUri!!
                } else if (trip?.status == "IN_PROGRESS") {
                    completionPhotos[currentPhotoLabel] = tempPhotoUri!!
                }
            }
        }
    }

    fun launchCamera(label: String) {
        currentPhotoLabel = label
        val uri = createTempPictureUri(label)
        tempPhotoUri = uri
        cameraLauncher.launch(uri)
    }

    fun launchExpenseReceiptCamera() {
        isTakingExpenseReceipt = true
        val uri = createTempPictureUri("expense_receipt_${System.currentTimeMillis()}")
        tempPhotoUri = uri
        cameraLauncher.launch(uri)
    }

    if (trip == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                Text(state.errorMessage ?: "Trip not found")
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Inspection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Info
            Text("Trip #${trip.id.take(4).uppercase()}", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text("From: ${trip.origin}", style = MaterialTheme.typography.bodyLarge)
            Text("To: ${trip.destination}", style = MaterialTheme.typography.bodyLarge)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Logic: PENDING vs IN PROGRESS
            if (trip.status == "PENDING") {
                Text(
                    "Pre-Trip Photos Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    "Please take all 5 photos to start the trip. These will upload in the background so you can start driving immediately.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Render Photo Buttons
                requiredPhotos.forEach { label ->
                    PhotoSlot(
                        label = label,
                        currentUri = takenPhotos[label],
                        onTakeClick = { launchCamera(label) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- UPLOAD BUTTON ---
                Button(
                    onClick = {
                        if (takenPhotos.size == requiredPhotos.size) {
                            // Call the Background Worker function
                            viewModel.uploadStartPhotosAndBeginTrip(
                                context = context,
                                tripId = trip.id,
                                photoUris = takenPhotos,
                                onSuccess = {
                                    Toast.makeText(context, "Trip Started! Uploading in background...", Toast.LENGTH_LONG).show()
                                    onNavigateBack()
                                }
                            )
                        } else {
                            Toast.makeText(context, "Please take all 5 photos first.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Start Trip (Background Upload)")
                    }
                }

            } else if (trip.status == "IN_PROGRESS") {
                // Trip is in progress - show expenses and completion
                Text("Status: IN PROGRESS", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "You are currently in transit. Drive safely!",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Expenses Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Expenses", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = { showExpenseDialog = true },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Expense")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Display existing expenses
                val totalExpenses = trip.expenses.sumOf { it.amount }
                val foodExpenses = trip.expenses.filter { it.type == "food" }.sumOf { it.amount }
                val foodLimit = trip.foodLimit
                
                if (trip.expenses.isNotEmpty()) {
                    trip.expenses.forEach { expense ->
                        ExpenseCard(
                            expense = expense,
                            onDelete = {
                                viewModel.deleteExpense(trip.id, expense.id)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Expenses:", style = MaterialTheme.typography.titleMedium)
                        Text("$${String.format("%.2f", totalExpenses)}", style = MaterialTheme.typography.titleMedium)
                    }
                    
                    if (foodLimit > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Food Expenses:", style = MaterialTheme.typography.bodyMedium)
                            Text("$${String.format("%.2f", foodExpenses)} / $${String.format("%.2f", foodLimit)}", 
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (foodExpenses > foodLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                } else {
                    Text("No expenses added yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                
                // Complete Trip Section
                Text("Complete Trip", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Take all 5 photos to complete the trip. These will be sent to admin/manager for verification.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                requiredPhotos.forEach { label ->
                    PhotoSlot(
                        label = label,
                        currentUri = completionPhotos[label],
                        onTakeClick = { 
                            currentPhotoLabel = label
                            val uri = createTempPictureUri("completion_${label}_${tripId}")
                            tempPhotoUri = uri
                            cameraLauncher.launch(uri)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        if (completionPhotos.size == requiredPhotos.size) {
                            viewModel.uploadCompletionPhotosAndStopTrip(
                                context = context,
                                tripId = trip.id,
                                photoUris = completionPhotos,
                                onSuccess = {
                                    Toast.makeText(context, "Trip completion submitted! Awaiting verification...", Toast.LENGTH_LONG).show()
                                    onNavigateBack()
                                }
                            )
                        } else {
                            Toast.makeText(context, "Please take all 5 photos first.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && completionPhotos.size == requiredPhotos.size
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Complete Trip (Background Upload)")
                    }
                }
                
            } else if (trip.status == "AWAITING_VERIFICATION") {
                Text("Status: AWAITING VERIFICATION", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Your trip completion photos have been submitted and are awaiting admin/manager verification.", 
                    style = MaterialTheme.typography.bodyMedium)
            } else if (trip.status == "COMPLETED") {
                Text("Status: COMPLETED", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("This trip has been completed and verified.", style = MaterialTheme.typography.bodyMedium)
                
                if (trip.finalMileage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Final Mileage: ${trip.finalMileage}", style = MaterialTheme.typography.bodyLarge)
                }
            }
            
            // Expense Dialog
            if (showExpenseDialog) {
                ExpenseDialog(
                    expenseType = selectedExpenseType,
                    onExpenseTypeChange = { selectedExpenseType = it },
                    expenseTypes = expenseTypes,
                    expenseAmount = expenseAmount,
                    onExpenseAmountChange = { expenseAmount = it },
                    expenseDescription = expenseDescription,
                    onExpenseDescriptionChange = { expenseDescription = it },
                    receiptUri = expenseReceiptUri,
                    requiresPhoto = expenseTypesRequiringPhoto.contains(selectedExpenseType),
                    onTakeReceiptPhoto = { launchExpenseReceiptCamera() },
                    onDismiss = { 
                        showExpenseDialog = false
                        expenseAmount = ""
                        expenseDescription = ""
                        expenseReceiptUri = null
                    },
                    onConfirm = {
                        if (expenseAmount.isBlank() || expenseAmount.toDoubleOrNull() == null) {
                            Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                            return@ExpenseDialog
                        }
                        
                        if (expenseTypesRequiringPhoto.contains(selectedExpenseType) && expenseReceiptUri == null) {
                            Toast.makeText(context, "Please take a photo of the receipt", Toast.LENGTH_SHORT).show()
                            return@ExpenseDialog
                        }
                        
                        viewModel.addExpense(
                            context = context,
                            tripId = trip.id,
                            expenseType = selectedExpenseType,
                            amount = expenseAmount.toDouble(),
                            description = expenseDescription,
                            receiptPhotoUri = expenseReceiptUri,
                            onSuccess = {
                                showExpenseDialog = false
                                expenseAmount = ""
                                expenseDescription = ""
                                expenseReceiptUri = null
                            }
                        )
                    },
                    isLoading = state.isLoading
                )
            }
        }
    }
}

@Composable
fun PhotoSlot(label: String, currentUri: Uri?, onTakeClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTakeClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentUri != null) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = Color(0xFF4CAF50)) // Green
                    Spacer(modifier = Modifier.width(8.dp))
                    // Thumbnail
                    Card(modifier = Modifier.size(40.dp)) {
                        Image(
                            painter = rememberAsyncImagePainter(currentUri),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Take Photo")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }

            if (currentUri == null) {
                Text("Tap to take", style = MaterialTheme.typography.labelSmall)
            } else {
                Text("Retake", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ExpenseCard(
    expense: Expense,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.type.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium
                )
                if (expense.description.isNotEmpty()) {
                    Text(
                        text = expense.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (expense.receiptPhotoUrl != null) {
                    Text(
                        text = "✓ Receipt attached",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$${String.format("%.2f", expense.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete expense",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun ExpenseDialog(
    expenseType: String,
    onExpenseTypeChange: (String) -> Unit,
    expenseTypes: List<String>,
    expenseAmount: String,
    onExpenseAmountChange: (String) -> Unit,
    expenseDescription: String,
    onExpenseDescriptionChange: (String) -> Unit,
    receiptUri: Uri?,
    requiresPhoto: Boolean,
    onTakeReceiptPhoto: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isLoading: Boolean
) {
    var expenseTypeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Expense") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Expense Type
                ExposedDropdownMenuBox(
                    expanded = expenseTypeExpanded,
                    onExpandedChange = { expenseTypeExpanded = !expenseTypeExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = expenseType.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Expense Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expenseTypeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expenseTypeExpanded,
                        onDismissRequest = { expenseTypeExpanded = false }
                    ) {
                        expenseTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    onExpenseTypeChange(type)
                                    expenseTypeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = expenseAmount,
                    onValueChange = onExpenseAmountChange,
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) }
                )

                // Description
                OutlinedTextField(
                    value = expenseDescription,
                    onValueChange = onExpenseDescriptionChange,
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // Receipt Photo (if required)
                if (requiresPhoto) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTakeReceiptPhoto() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (receiptUri != null) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = Color(0xFF4CAF50))
                                Spacer(modifier = Modifier.width(8.dp))
                                Card(modifier = Modifier.size(40.dp)) {
                                    Image(
                                        painter = rememberAsyncImagePainter(receiptUri),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Receipt photo taken", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Icon(Icons.Filled.CameraAlt, contentDescription = "Take Photo")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Take Receipt Photo (Required)", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading && expenseAmount.isNotBlank() && (!requiresPhoto || receiptUri != null)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}