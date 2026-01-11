package com.example.logistics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File

class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 1. Get Input Data (Trip ID and File Paths)
        val tripId = inputData.getString("tripId") ?: return Result.failure()
        val filePaths = inputData.getStringArray("filePaths") ?: return Result.failure()
        val labels = inputData.getStringArray("labels") ?: return Result.failure()
        val photoType = inputData.getString("photoType") ?: "start" // "start", "completion", or "expense_receipt"
        val updateStatus = inputData.getBoolean("updateStatus", true) // Whether to update trip status
        val expenseId = inputData.getString("expenseId") // For expense receipt uploads

        val storageRef = FirebaseStorage.getInstance().reference
        val db = FirebaseFirestore.getInstance()
        val uploadedUrls = mutableMapOf<String, String>()

        // Set progress data so MediaManagerScreen can display upload type even while in progress
        setProgress(workDataOf(
            "photoType" to photoType,
            "tripId" to tripId
        ))

        try {
            // Bug 1 Fix: Validate expenseId before using it in storage path
            if (photoType == "expense_receipt" && expenseId == null) {
                return Result.failure()
            }
            
            // 2. Loop through files
            for (i in filePaths.indices) {
                val path = filePaths[i]
                val label = labels[i]
                val file = File(path)

                if (file.exists()) {
                    // --- SMART COMPRESSION ---
                    // Bug 3 Fix: Check for null bitmap before compressing
                    val compressedData = compressImage(file) ?: continue // Skip if compression fails
                    
                    // Upload to Storage - different path based on photo type
                    val storagePath = when (photoType) {
                        "completion" -> "trips/$tripId/completion_photos/$label.jpg"
                        "expense_receipt" -> {
                            // expenseId is guaranteed non-null here due to check above
                            "trips/$tripId/expenses/${expenseId!!}_receipt.jpg"
                        }
                        else -> "trips/$tripId/start_photos/$label.jpg"
                    }
                    val fileRef = storageRef.child(storagePath)
                    fileRef.putBytes(compressedData).await() // Upload the small byte array
                    val downloadUrl = fileRef.downloadUrl.await()

                    uploadedUrls[label] = downloadUrl.toString()
                }
            }

            // 3. Update Firestore (Only after all uploads are done)
            if (photoType == "expense_receipt" && expenseId != null) {
                // Update the specific expense in the expenses list
                val tripDoc = db.collection(Constants.COLLECTION_TRIPS).document(tripId).get().await()
                val expenses = tripDoc.get("expenses") as? List<Map<String, Any>> ?: emptyList()
                
                val updatedExpenses = expenses.map { expense ->
                    if (expense["id"] == expenseId) {
                        val mutableExpense = expense.toMutableMap()
                        mutableExpense["receiptPhotoUrl"] = uploadedUrls["receipt"] ?: ""
                        mutableExpense
                    } else {
                        expense
                    }
                }
                
                db.collection(Constants.COLLECTION_TRIPS).document(tripId)
                    .update("expenses", updatedExpenses).await()
            } else {
                // Update trip photos (start or completion)
                // Bug 2 Fix: Merge with existing photos instead of replacing
                val tripDoc = db.collection(Constants.COLLECTION_TRIPS).document(tripId).get().await()
                val updates = mutableMapOf<String, Any>()
                
                if (photoType == "completion") {
                    val existingPhotos = tripDoc.get("completionPhotos") as? Map<String, String> ?: emptyMap()
                    val mergedPhotos = existingPhotos.toMutableMap()
                    mergedPhotos.putAll(uploadedUrls)
                    updates["completionPhotos"] = mergedPhotos
                    if (updateStatus) {
                        updates["status"] = "AWAITING_VERIFICATION"
                    }
                } else {
                    val existingPhotos = tripDoc.get("startPhotos") as? Map<String, String> ?: emptyMap()
                    val mergedPhotos = existingPhotos.toMutableMap()
                    mergedPhotos.putAll(uploadedUrls)
                    updates["startPhotos"] = mergedPhotos
                    if (updateStatus) {
                        updates["status"] = "IN_PROGRESS"
                    }
                }

                db.collection(Constants.COLLECTION_TRIPS).document(tripId).update(updates).await()
            }

            // Set output data for MediaManagerScreen to display
            val outputData = workDataOf(
                "photoType" to photoType,
                "tripId" to tripId
            )

            return Result.success(outputData)

        } catch (e: Exception) {
            e.printStackTrace()
            // If it fails (bad internet), WorkManager will automatically RETRY later
            return Result.retry()
        }
    }

    // Helper: Turns a huge file into a tiny JPEG byte array
    // Bug 3 Fix: Return null if bitmap decoding fails
    private fun compressImage(file: File): ByteArray? {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val outputStream = ByteArrayOutputStream()
        // Quality 20 = High compression, perfectly fine for inspection photos
        bitmap.compress(Bitmap.CompressFormat.JPEG, 20, outputStream)
        return outputStream.toByteArray()
    }
}