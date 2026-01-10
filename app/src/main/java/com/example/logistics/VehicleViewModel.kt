package com.example.logistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class VehicleState(
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class VehicleViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val _uiState = MutableStateFlow(VehicleState())
    val uiState: StateFlow<VehicleState> = _uiState.asStateFlow()

    init {
        listenToVehicles()
    }

    // Real-time listener: If a mileage updates, the list updates instantly
    private fun listenToVehicles() {
        db.collection(Constants.COLLECTION_VEHICLES)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _uiState.value = _uiState.value.copy(error = e.localizedMessage)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(Vehicle::class.java) }
                    _uiState.value = _uiState.value.copy(vehicles = list)
                }
            }
    }

    fun addVehicle(make: String, model: String, plate: String, mileageStr: String) {
        val mileage = mileageStr.toIntOrNull()

        if (make.isBlank() || plate.isBlank() || mileage == null) {
            _uiState.value = _uiState.value.copy(error = "Please fill Make, Plate, and valid Mileage")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)

        viewModelScope.launch {
            try {
                val ref = db.collection(Constants.COLLECTION_VEHICLES).document()
                val vehicle = Vehicle(
                    id = ref.id,
                    make = make,
                    model = model,
                    plateNumber = plate,
                    currentMileage = mileage
                )
                ref.set(vehicle).await()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Vehicle Added!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage)
            }
        }
    }
    // Add this function inside your VehicleViewModel class

    fun deleteVehicle(vehicle: Vehicle) {
        // 1. Snapshot the current list in case we need to revert
        val oldList = _uiState.value.vehicles

        // 2. Optimistic Update: Remove it from the UI immediately
        val newList = oldList.filter { it.id != vehicle.id }
        _uiState.value = _uiState.value.copy(vehicles = newList)

        viewModelScope.launch {
            try {
                // 3. Perform the actual delete in Firestore
                db.collection(Constants.COLLECTION_VEHICLES).document(vehicle.id).delete().await()
            } catch (e: Exception) {
                // 4. If it fails, put the item back and show error
                _uiState.value = _uiState.value.copy(
                    vehicles = oldList,
                    error = "Failed to delete: ${e.localizedMessage}"
                )
            }
        }
    }
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}