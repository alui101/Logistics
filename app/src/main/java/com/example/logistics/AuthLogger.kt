package com.example.logistics

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await

object AuthLogger {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    /**
     * Logs a login event to Firestore
     * @param userId The user's Firebase Auth UID
     * @param email The user's email
     * @param role The user's role (admin, manager, driver)
     */
    fun logLogin(userId: String, email: String, role: String) {
        val logData = hashMapOf(
            "userId" to userId,
            "email" to email,
            "role" to role,
            "action" to "LOGIN",
            "timestamp" to Timestamp.now()
        )
        
        db.collection(Constants.COLLECTION_AUTH_LOGS)
            .add(logData)
            .addOnSuccessListener { docRef ->
                android.util.Log.d("AuthLogger", "Login logged successfully with ID: ${docRef.id}")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("AuthLogger", "Failed to log login: ${e.message}", e)
            }
    }
    
    /**
     * Logs a logout event to Firestore
     * @param userId The user's Firebase Auth UID (can be null if already logged out)
     * @param email The user's email (can be null if already logged out)
     * @param role The user's role (can be null if already logged out)
     * @param logoutMethod How the logout occurred: "BUTTON" for user-initiated, "APP_EXIT" for app closed
     * @param onComplete Callback when logging is complete (optional)
     */
    fun logLogout(userId: String?, email: String?, role: String?, logoutMethod: String = "BUTTON", onComplete: (() -> Unit)? = null) {
        val logData = hashMapOf(
            "action" to "LOGOUT",
            "logoutMethod" to logoutMethod,
            "timestamp" to Timestamp.now()
        )
        
        // Add user info if available (before signOut is called)
        userId?.let { logData["userId"] = it }
        email?.let { logData["email"] = it }
        role?.let { logData["role"] = it }
        
        db.collection(Constants.COLLECTION_AUTH_LOGS)
            .add(logData)
            .addOnSuccessListener { docRef ->
                android.util.Log.d("AuthLogger", "Logout logged successfully with ID: ${docRef.id}, method: $logoutMethod")
                onComplete?.invoke()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("AuthLogger", "Failed to log logout: ${e.message}", e)
                // Still call onComplete even on failure so signOut can proceed
                onComplete?.invoke()
            }
    }
    
    /**
     * Gets user role from Firestore before logout
     */
    suspend fun getUserRole(userId: String): String? {
        return try {
            val doc = db.collection(Constants.COLLECTION_USERS)
                .document(userId)
                .get()
                .await()
            doc.getString("role")
        } catch (e: Exception) {
            android.util.Log.e("AuthLogger", "Failed to get user role: ${e.message}")
            null
        }
    }
}
