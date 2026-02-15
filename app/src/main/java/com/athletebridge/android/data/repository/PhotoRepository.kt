package com.athletebridge.android.data.repository

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepository @Inject constructor(
    private val storage: FirebaseStorage
) {
    suspend fun uploadProfileImage(uid: String, imageBytes: ByteArray): Result<String> {
        return try {
            val ref = storage.reference.child("profile_images/$uid.jpg")
            ref.putBytes(imageBytes).await()
            val downloadUrl = ref.downloadUrl.await()
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadChatImage(chatId: String, imageBytes: ByteArray, filename: String): Result<String> {
        return try {
            val ref = storage.reference.child("chat_images/$chatId/$filename")
            ref.putBytes(imageBytes).await()
            val downloadUrl = ref.downloadUrl.await()
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
