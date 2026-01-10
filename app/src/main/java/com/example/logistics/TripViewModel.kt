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

    fun updateTrip(tripId: String, origin: String, destination: String, description: String, loadType: String, selectedDriver: UserItem?, selectedVehicle: Vehicle?) {
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

    fun createTrip(origin: String, destination: String, description: String, loadType: String, selectedDriver: UserItem?, selectedVehicle: Vehicle?) {
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
                    _uiState.value = _uiState.value.copy(isLoading = false, trips = listOf(trip))
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

        // 1. Convert URIs to File Paths so the Worker can read them
        val labels = photoUris.keys.toTypedArray()
        val paths = photoUris.values.map { uri ->
            val file = File(context.cacheDir, uri.lastPathSegment ?: "temp")
            file.absolutePath
        }.toTypedArray()

        val workData = workDataOf(
            "tripId" to tripId,
            "labels" to labels,
            "filePaths" to paths
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

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}