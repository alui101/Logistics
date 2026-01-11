package com.example.logistics

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
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
    
    // Store listener registrations to allow cleanup
    private var tripsListenerRegistration: ListenerRegistration? = null
    private var driverTripsListenerRegistration: ListenerRegistration? = null

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
        // Remove old listener if exists
        tripsListenerRegistration?.remove()
        
        _uiState.value = _uiState.value.copy(isLoading = true)
        tripsListenerRegistration = db.collection(Constants.COLLECTION_TRIPS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    // Ignore permission errors if user is logged out
                    if (e.message?.contains("permission", ignoreCase = true) == true && auth.currentUser == null) {
                        return@addSnapshotListener
                    }
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
                val storage = FirebaseStorage.getInstance()
                
                // Delete all start photos
                trip.startPhotos.values.forEach { photoUrl ->
                    try {
                        if (photoUrl.isNotEmpty()) {
                            val photoRef = storage.getReferenceFromUrl(photoUrl)
                            photoRef.delete().await()
                        }
                    } catch (e: Exception) {
                        // Continue even if photo deletion fails
                        android.util.Log.w("TripViewModel", "Failed to delete start photo: ${e.message}")
                    }
                }
                
                // Delete all completion photos
                trip.completionPhotos.values.forEach { photoUrl ->
                    try {
                        if (photoUrl.isNotEmpty()) {
                            val photoRef = storage.getReferenceFromUrl(photoUrl)
                            photoRef.delete().await()
                        }
                    } catch (e: Exception) {
                        // Continue even if photo deletion fails
                        android.util.Log.w("TripViewModel", "Failed to delete completion photo: ${e.message}")
                    }
                }
                
                // Delete all expense receipt photos
                trip.expenses.forEach { expense ->
                    expense.receiptPhotoUrl?.let { photoUrl ->
                        try {
                            if (photoUrl.isNotEmpty()) {
                                val photoRef = storage.getReferenceFromUrl(photoUrl)
                                photoRef.delete().await()
                            }
                        } catch (e: Exception) {
                            // Continue even if photo deletion fails
                            android.util.Log.w("TripViewModel", "Failed to delete expense receipt photo: ${e.message}")
                        }
                    }
                }
                
                // Delete the trip document from Firestore
                db.collection(Constants.COLLECTION_TRIPS).document(trip.id).delete().await()
                
                _uiState.value = _uiState.value.copy(successMessage = "Trip and all photos deleted successfully")
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
        // Remove old listener if exists
        driverTripsListenerRegistration?.remove()
        
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Not logged in")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true)

        driverTripsListenerRegistration = db.collection(Constants.COLLECTION_TRIPS)
            .whereEqualTo("driverId", currentUser.uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    // Ignore permission errors if user is logged out
                    if (e.message?.contains("permission", ignoreCase = true) == true && auth.currentUser == null) {
                        return@addSnapshotListener
                    }
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

        // Set startedAt timestamp immediately but keep status as PENDING until photos upload
        // This allows us to track that photos are being uploaded
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
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.MINUTES
            )
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
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.MINUTES
            )
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
                
                // Bug 4 Fix: Use Firestore transaction or FieldValue.arrayUnion for atomic updates
                // Using runTransaction for atomic read-modify-write
                db.runTransaction { transaction ->
                    val tripRef = db.collection(Constants.COLLECTION_TRIPS).document(tripId)
                    val currentDoc = transaction.get(tripRef)
                    val existingExpenses = currentDoc.get("expenses") as? List<Map<String, Any>> ?: emptyList()
                    transaction.update(tripRef, "expenses", existingExpenses + newExpenseMap)
                }.await()

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
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            15,
                            TimeUnit.MINUTES
                        )
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
                // Get current trip to update expenses list and find receipt photo URL
                val tripDoc = db.collection(Constants.COLLECTION_TRIPS).document(tripId).get().await()
                val currentExpenses = tripDoc.get("expenses") as? List<Map<String, Any>> ?: emptyList()
                
                // Find the expense to get its receipt photo URL
                val expenseToDelete = currentExpenses.find { it["id"] == expenseId }
                val receiptPhotoUrl = expenseToDelete?.get("receiptPhotoUrl") as? String
                
                // Delete receipt photo from Firebase Storage if it exists
                if (receiptPhotoUrl != null && receiptPhotoUrl.isNotEmpty()) {
                    try {
                        val storage = FirebaseStorage.getInstance()
                        val photoRef = storage.getReferenceFromUrl(receiptPhotoUrl)
                        photoRef.delete().await()
                    } catch (e: Exception) {
                        // Continue even if photo deletion fails - log but don't block expense deletion
                        android.util.Log.w("TripViewModel", "Failed to delete receipt photo: ${e.message}")
                    }
                }
                
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

    // Cleanup listeners - call this before logout
    fun cleanupListeners() {
        tripsListenerRegistration?.remove()
        tripsListenerRegistration = null
        driverTripsListenerRegistration?.remove()
        driverTripsListenerRegistration = null
    }
    
    override fun onCleared() {
        super.onCleared()
        cleanupListeners()
    }

    // Verify and finalize trip (Admin/Manager only)
    fun verifyAndFinalizeTrip(tripId: String, finalMileage: Int, moneyEarned: Double = 0.0, additionalCosts: Double = 0.0) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                // Get the trip to access vehicleId
                val tripDoc = db.collection(Constants.COLLECTION_TRIPS).document(tripId).get().await()
                val trip = tripDoc.toObject(Trip::class.java)
                
                if (trip == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Trip not found")
                    return@launch
                }
                
                // Validate mileage if vehicle is assigned
                var vehicleUpdateFailed = false
                if (trip.vehicleId != null && trip.vehicleId.isNotBlank()) {
                    try {
                        val vehicleDoc = db.collection(Constants.COLLECTION_VEHICLES).document(trip.vehicleId).get().await()
                        val vehicle = vehicleDoc.toObject(Vehicle::class.java)
                        
                        if (vehicle != null) {
                            // Validate that final mileage is not less than current vehicle mileage
                            if (finalMileage < vehicle.currentMileage) {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    errorMessage = "Final mileage ($finalMileage) cannot be less than vehicle's current mileage (${vehicle.currentMileage})"
                                )
                                return@launch
                            }
                            
                            // Try to update vehicle mileage to match finalized mileage
                            try {
                                db.collection(Constants.COLLECTION_VEHICLES).document(trip.vehicleId)
                                    .update("currentMileage", finalMileage).await()
                            } catch (e: Exception) {
                                // If vehicle update fails (e.g., permission denied), continue with trip finalization
                                // but mark that vehicle update failed
                                vehicleUpdateFailed = true
                                // Log the error but don't block trip finalization
                                android.util.Log.w("TripViewModel", "Failed to update vehicle mileage: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        // If we can't even read the vehicle, still allow trip finalization
                        // but mark that vehicle update failed
                        vehicleUpdateFailed = true
                        android.util.Log.w("TripViewModel", "Failed to read vehicle for validation: ${e.message}")
                    }
                }
                
                val updates = mutableMapOf<String, Any>(
                    "status" to "COMPLETED",
                    "finalMileage" to finalMileage
                )
                
                if (moneyEarned > 0) {
                    updates["moneyEarned"] = moneyEarned
                }
                
                if (additionalCosts > 0) {
                    updates["additionalCosts"] = additionalCosts
                }
                
                db.collection(Constants.COLLECTION_TRIPS).document(tripId).update(updates).await()
                // Reload the trip to get updated status
                loadSingleTrip(tripId)
                
                // Show appropriate success message
                val successMsg = if (vehicleUpdateFailed) {
                    "Trip finalized successfully. Note: Vehicle mileage could not be updated (permission denied). Please update manually."
                } else {
                    "Trip finalized successfully"
                }
                _uiState.value = _uiState.value.copy(isLoading = false, successMessage = successMsg)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Failed to finalize trip: ${e.localizedMessage}")
            }
        }
    }
    
    fun unfinalizeTrip(tripId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                db.collection(Constants.COLLECTION_TRIPS).document(tripId).update(
                    mapOf("status" to "AWAITING_VERIFICATION")
                ).await()
                // Reload the trip to get updated status
                loadSingleTrip(tripId)
                _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Trip un-finalized successfully")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Failed to un-finalize trip: ${e.localizedMessage}")
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}