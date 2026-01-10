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

        val storageRef = FirebaseStorage.getInstance().reference
        val db = FirebaseFirestore.getInstance()
        val uploadedUrls = mutableMapOf<String, String>()

        try {
            // 2. Loop through files
            for (i in filePaths.indices) {
                val path = filePaths[i]
                val label = labels[i]
                val file = File(path)

                if (file.exists()) {
                    // --- SMART COMPRESSION ---
                    // Compress the image to ~20% quality before uploading
                    val compressedData = compressImage(file)

                    // Upload to Storage
                    val fileRef = storageRef.child("trips/$tripId/start_photos/$label.jpg")
                    fileRef.putBytes(compressedData).await() // Upload the small byte array
                    val downloadUrl = fileRef.downloadUrl.await()

                    uploadedUrls[label] = downloadUrl.toString()
                }
            }

            // 3. Update Firestore (Only after all uploads are done)
            val updates = mapOf(
                "startPhotos" to uploadedUrls,
                "status" to "IN_PROGRESS"
            )

            db.collection(Constants.COLLECTION_TRIPS).document(tripId).update(updates).await()

            return Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            // If it fails (bad internet), WorkManager will automatically RETRY later
            return Result.retry()
        }
    }

    // Helper: Turns a huge file into a tiny JPEG byte array
    private fun compressImage(file: File): ByteArray {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val outputStream = ByteArrayOutputStream()
        // Quality 20 = High compression, perfectly fine for inspection photos
        bitmap.compress(Bitmap.CompressFormat.JPEG, 20, outputStream)
        return outputStream.toByteArray()
    }
}