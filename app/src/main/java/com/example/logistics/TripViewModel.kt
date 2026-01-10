package com.example.logistics

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream

data class TripState(
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val drivers: List<UserItem> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val trips: List<Trip> = emptyList()
)

class TripViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(TripState())
    val uiState: StateFlow<TripState> = _uiState.asStateFlow()

    // --- MANAGER FUNCTIONS ---
    fun loadDataForManager() {
        fetchDrivers()
        fetchVehicles()
    }

    private fun fetchDrivers() {
        viewModelScope.launch {
            try {
                val result = db.collection(Constants.COLLECTION_USERS)
                    .whereEqualTo("role", Constants.ROLE_DRIVER)
                    .get().await()
                val list = result.documents.mapNotNull { doc ->
                    val email = doc.getString("email")
                    if (email != null) UserItem(doc.id, email, "driver") else null
                }
                _uiState.value = _uiState.value.copy(drivers = list)
            } catch (e: Exception) { /* Handle error */ }
        }
    }

    private fun fetchVehicles() {
        viewModelScope.launch {
            try {
                val result = db.collection(Constants.COLLECTION_VEHICLES).get().await()
                val list = result.documents.mapNotNull { it.toObject(Vehicle::class.java) }
                _uiState.value = _uiState.value.copy(vehicles = list)
            } catch (e: Exception) { /* Handle error */ }
        }
    }

    fun loadAllTrips() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        db.collection(Constants.COLLECTION_TRIPS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.localizedMessage)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val trips = snapshot.toObjects(Trip::class.java)
                    _uiState.value = _uiState.value.copy(isLoading = false, trips = trips)
                }
            }
    }

    fun deleteTrip(trip: Trip) {
        viewModelScope.launch {
            try {
                db.collection(Constants.COLLECTION_TRIPS).document(trip.id).delete().await()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to delete: ${e.localizedMessage}")
            }
        }
    }

    fun updateTrip(tripId: String, origin: String, destination: String, description: String, loadType: String, foodLimit: Double, selectedDriver: UserItem?, selectedVehicle: Vehicle?) {
        if (origin.isBlank() || destination.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Origin and Destination are required")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val updates = mapOf(
                    "origin" to origin,
                    "destination" to destination,
                    "description" to description,
                    "loadType" to loadType,
                    "foodLimit" to foodLimit,
                    "driverId" to selectedDriver?.uid,
                    "driverName" to selectedDriver?.email,
                    "vehicleId" to selectedVehicle?.id,
                    "vehicleInfo" to selectedVehicle?.displayName()
                )
                db.collection(Constants.COLLECTION_TRIPS).document(tripId).update(updates).await()
                _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Trip Updated Successfully!")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.localizedMessage)
            }
        }
    }

    fun createTrip(origin: String, destination: String, description: String, loadType: String, foodLimit: Double, selectedDriver: UserItem?, selectedVehicle: Vehicle?) {
        if (origin.isBlank() || destination.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Origin and Destination are required")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val ref = db.collection(Constants.COLLECTION_TRIPS).document()
                val newTrip = Trip(
                    id = ref.id,
                    origin = origin,
                    destination = destination,
                    description = description,
                    loadType = loadType,
                    foodLimit = foodLimit,
                    driverId = selectedDriver?.uid,
                    driverName = selectedDriver?.email,
                    vehicleId = selectedVehicle?.id,
                    vehicleInfo = selectedVehicle?.displayName()
                )
                ref.set(newTrip).await()
                _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Trip Created!")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.localizedMessage)
            }
        }
    }

    // --- DRIVER FUNCTIONS ---

    fun loadDriverTrips() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Not logged in")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true)

        db.collection(Constants.COLLECTION_TRIPS)
            .whereEqualTo("driverId", currentUser.uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Failed to load trips: ${e.localizedMessage}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val trips = snapshot.toObjects(Trip::class.java)
                    val sortedTrips = trips.sortedByDescending { it.createdAt }
                    _uiState.value = _uiState.value.copy(isLoading = false, trips = sortedTrips)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, trips = emptyList())
                }
            }
    }

    fun loadSingleTrip(tripId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        db.collection(Constants.COLLECTION_TRIPS).document(tripId)
            .get()
            .addOnSuccessListener { document ->
                val trip = document.toObject(Trip::class.java)
                if (trip != null) {
                    // Merge with existing trips list instead of replacing
                    val currentTrips = _uiState.value.trips.toMutableList()
                    val existingIndex = currentTrips.indexOfFirst { it.id == tripId }
                    if (existingIndex >= 0) {
                        currentTrips[existingIndex] = trip
                    } else {
                        currentTrips.add(trip)
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, trips = currentTrips)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Trip document not found")
                }
            }
            .addOnFailureListener { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Failed to load trip: ${e.localizedMessage}")
            }
    }

    // --- NEW: FIRE-AND-FORGET BACKGROUND UPLOAD ---
    fun uploadStartPhotosAndBeginTrip(
        context: Context, // Required for WorkManager
        tripId: String,
        photoUris: Map<String, Uri>,
        onSuccess: () -> Unit
    ) {
        _uiState.value = _uiState.value.copy(isLoading = true)

        // Set startedAt timestamp immediately
        viewModelScope.launch {
            try {
                db.collection(Constants.COLLECTION_TRIPS).document(tripId)
                    .update("startedAt", com.google.firebase.Timestamp.now()).await()
            } catch (e: Exception) {
                // Continue even if timestamp update fails
            }
        }

        // 1. Convert URIs to File Paths so the Worker can read them
        val labels = photoUris.keys.toTypedArray()
        val paths = photoUris.values.map { uri ->
            val file = File(context.cacheDir, uri.lastPathSegment ?: "temp")
            file.absolutePath
        }.toTypedArray()

        val workData = workDataOf(
            "tripId" to tripId,
            "labels" to labels,
            "filePaths" to paths,
            "photoType" to "start",
            "updateStatus" to true
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWork = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workData)
            .setConstraints(constraints)
            .addTag("trip_upload")
            .build()

        WorkManager.getInstance(context).enqueue(uploadWork)

        _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Upload started in background")
        onSuccess()
    }

    // Upload completion photos and stop trip
    fun uploadCompletionPhotosAndStopTrip(
        context: Context,
        tripId: String,
        photoUris: Map<String, Uri>,
        onSuccess: () -> Unit
    ) {
        _uiState.value = _uiState.value.copy(isLoading = true)

        // Set stoppedAt timestamp immediately
        viewModelScope.launch {
            try {
                db.collection(Constants.COLLECTION_TRIPS).document(tripId)
                    .update("stoppedAt", com.google.firebase.Timestamp.now()).await()
            } catch (e: Exception) {
                // Continue even if timestamp update fails
            }
        }

        val labels = photoUris.keys.toTypedArray()
        val paths = photoUris.values.map { uri ->
            val file = File(context.cacheDir, uri.lastPathSegment ?: "temp")
            file.absolutePath
        }.toTypedArray()

        val workData = workDataOf(
            "tripId" to tripId,
            "labels" to labels,
            "filePaths" to paths,
            "photoType" to "completion",
            "updateStatus" to true
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWork = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workData)
            .setConstraints(constraints)
            .addTag("trip_upload")
            .build()

        WorkManager.getInstance(context).enqueue(uploadWork)

        _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Completion photos uploading in background")
        onSuccess()
    }

    // Add expense to trip
    fun addExpense(
        context: Context,
        tripId: String,
        expenseType: String,
        amount: Double,
        description: String,
        receiptPhotoUri: Uri?,
        onSuccess: () -> Unit
    ) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        viewModelScope.launch {
            try {
                // Get current trip data to validate food limit with fresh data
                val tripDoc = db.collection(Constants.COLLECTION_TRIPS).document(tripId).get().await()
                val currentExpenses = tripDoc.get("expenses") as? List<Map<String, Any>> ?: emptyList()
                val foodLimit = tripDoc.getDouble("foodLimit") ?: 0.0
                
                // Validate food expense limit with fresh data
                if (expenseType == "food" && foodLimit > 0) {
                    val currentFoodExpenses = currentExpenses
                        .filter { it["type"] == "food" }
                        .sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
                    
                    if (currentFoodExpenses + amount > foodLimit) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, 
                            errorMessage = "Food expense would exceed the limit of $${String.format("%.2f", foodLimit)}. Current food expenses: $${String.format("%.2f", currentFoodExpenses)}"
                        )
                        return@launch
                    }
                }
                
                // Generate expense ID first
                val expenseId = db.collection(Constants.COLLECTION_TRIPS)
                    .document(tripId)
                    .collection("expenses")
                    .document()
                    .id
                
                // Create expense without photo URL initially
                val newExpenseMap = mutableMapOf<String, Any>(
                    "id" to expenseId,
                    "type" to expenseType,
                    "amount" to amount,
                    "description" to description,
                    "createdAt" to com.google.firebase.Timestamp.now()
                )
                
                // Save expense immediately (without photo URL)
                db.collection(Constants.COLLECTION_TRIPS).document(tripId)
                    .update("expenses", currentExpenses + newExpenseMap).await()

                // If receipt photo provided, queue it as background task
                if (receiptPhotoUri != null) {
                    // Copy the photo to cache with a predictable name for the worker
                    val fileName = "expense_receipt_${expenseId}.jpg"
                    val cacheFile = File(context.cacheDir, fileName)
                    
                    // Delete old file if exists
                    if (cacheFile.exists()) {
                        cacheFile.delete()
                    }
                    
                    // Copy the URI content to cache file
                    val inputStream = context.contentResolver.openInputStream(receiptPhotoUri)
                    if (inputStream != null) {
                        try {
                            FileOutputStream(cacheFile).use { output ->
                                inputStream.copyTo(output)
                            }
                        } finally {
                            inputStream.close()
                        }
                    }
                    
                    // Queue background upload
                    val workData = workDataOf(
                        "tripId" to tripId,
                        "expenseId" to expenseId,
                        "filePaths" to arrayOf(cacheFile.absolutePath),
                        "labels" to arrayOf("receipt"),
                        "photoType" to "expense_receipt"
                    )
                    
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                    
                    val uploadWork = OneTimeWorkRequestBuilder<UploadWorker>()
                        .setInputData(workData)
                        .setConstraints(constraints)
                        .addTag("trip_upload")
                        .build()
                    
                    WorkManager.getInstance(context).enqueue(uploadWork)
                }

                // Reload the trip to update UI immediately
                loadSingleTrip(tripId)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false, 
                    successMessage = if (receiptPhotoUri != null) 
                        "Expense added successfully. Receipt uploading in background." 
                    else 
                        "Expense added successfully"
                )
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Failed to add expense: ${e.localizedMessage}")
            }
        }
    }
    
    // Delete expense from trip
    fun deleteExpense(tripId: String, expenseId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        viewModelScope.launch {
            try {
                // Get current trip to update expenses list
                val tripDoc = db.collection(Constants.COLLECTION_TRIPS).document(tripId).get().await()
                val currentExpenses = tripDoc.get("expenses") as? List<Map<String, Any>> ?: emptyList()
                
                // Remove the expense with matching id
                val updatedExpenses = currentExpenses.filterNot { it["id"] == expenseId }
                
                // Update trip's expenses list
                db.collection(Constants.COLLECTION_TRIPS).document(tripId)
                    .update("expenses", updatedExpenses).await()

                // Reload the trip to update UI immediately
                loadSingleTrip(tripId)
                
                _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Expense deleted successfully")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Failed to delete expense: ${e.localizedMessage}")
            }
        }
    }

    private fun compressImage(file: File): ByteArray {
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 20, outputStream)
        return outputStream.toByteArray()
    }

    // Verify and finalize trip (Admin/Manager only)
    fun verifyAndFinalizeTrip(tripId: String, finalMileage: Int) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                db.collection(Constants.COLLECTION_TRIPS).document(tripId).update(
                    mapOf(
                        "status" to "COMPLETED",
                        "finalMileage" to finalMileage
                    )
                ).await()
                _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Trip finalized successfully")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Failed to finalize trip: ${e.localizedMessage}")
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}