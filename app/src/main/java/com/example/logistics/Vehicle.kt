package com.example.logistics

data class Vehicle(
    val id: String = "",
    val make: String = "",      // e.g., "Volvo"
    val model: String = "",     // e.g., "VNL 860"
    val plateNumber: String = "",
    val vin: String = "",       // Optional but good for maintenance tracking
    val currentMileage: Int = 0 // Updated by driver photos/trips later
) {
    // Helper for dropdowns (e.g., "Volvo - ABC 123")
    fun displayName(): String = "$make ($plateNumber)"
}