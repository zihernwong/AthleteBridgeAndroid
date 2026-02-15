package com.athletebridge.android.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubjectRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun fetchSubjectIds(): List<String> {
        return try {
            val snapshot = firestore.collection("subjects").get().await()
            snapshot.documents.map { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
