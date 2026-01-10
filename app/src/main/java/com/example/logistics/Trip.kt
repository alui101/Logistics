package com.example.logistics

import com.google.firebase.Timestamp

data class Trip(
    val id: String = "",

    // People
    val driverId: String? = null,
    val driverName: String? = null,

    // Equipment (NEW)
    val vehicleId: String? = null,
    val vehicleInfo: String? = null, // e.g., "Volvo (ABC-123)"

    // Route Details
    val origin: String = "",
    val destination: String = "",
    val description: String = "",
    val loadType: String = "Dry Van", // Dry Van, Vegetable, Frozen, Ambient

    // Status & Time
    val status: String = "PENDING", // PENDING, IN_PROGRESS, COMPLETED
    val scheduledDate: Timestamp? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val startPhotos: Map<String, String> = emptyMap()
)