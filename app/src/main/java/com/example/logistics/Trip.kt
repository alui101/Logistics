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
    val foodLimit: Double = 0.0, // Food expense limit set by admin/manager

    // Status & Time
    val status: String = "PENDING", // PENDING, IN_PROGRESS, AWAITING_VERIFICATION, COMPLETED
    val scheduledDate: Timestamp? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val startedAt: Timestamp? = null, // When driver started the trip
    val stoppedAt: Timestamp? = null, // When driver stopped/completed the trip
    val startPhotos: Map<String, String> = emptyMap(), // Pre-trip inspection photos
    val completionPhotos: Map<String, String> = emptyMap(), // End-of-trip inspection photos
    val finalMileage: Int? = null, // Entered by admin/manager after verification
    val expenses: List<Expense> = emptyList(), // List of expenses for this trip
    val moneyEarned: Double? = null, // Money earned through trip (entered by admin/manager)
    val additionalCosts: Double? = null // Additional costs (entered by admin/manager)
)

data class Expense(
    val id: String = "",
    val type: String = "", // fuel, visas, tips, hotel, food, repairs
    val amount: Double = 0.0,
    val description: String = "",
    val receiptPhotoUrl: String? = null, // URL to photo in Firebase Storage
    val createdAt: Timestamp = Timestamp.now()
)