package com.athletebridge.android.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** Cross-collection user lookups (photo URLs, presence) matching iOS FirestoreManager caches. */
@Singleton
class UserDirectoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    private val photoCache = mutableMapOf<String, String?>()

    /** Resolve a user's profile photo from coaches/ then clients/, handling gs:// storage paths. */
    suspend fun fetchUserPhotoUrl(uid: String): String? {
        if (uid.isEmpty()) return null
        photoCache[uid]?.let { return it }
        if (photoCache.containsKey(uid)) return null

        val raw = readPhotoField("coaches", uid) ?: readPhotoField("clients", uid)
        val resolved = resolvePhotoUrl(raw)
        photoCache[uid] = resolved
        return resolved
    }

    private suspend fun readPhotoField(collection: String, uid: String): String? {
        return try {
            val data = firestore.collection(collection).document(uid).get().await().data ?: return null
            listOf("photoURLString", "PhotoURLString", "photoURL", "PhotoURL", "photoUrl")
                .firstNotNullOfOrNull { data[it] as? String }
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolvePhotoUrl(raw: String?): String? {
        val s = raw ?: return null
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        return try {
            val ref = if (s.startsWith("gs://")) storage.getReferenceFromUrl(s) else storage.reference.child(s)
            ref.downloadUrl.await().toString()
        } catch (_: Exception) {
            null
        }
    }

    /** Last-seen timestamps for a set of clients (clients/{id}.lastSeen, matches iOS fetchLastSeen). */
    suspend fun fetchLastSeen(clientIds: List<String>): Map<String, Date> {
        if (clientIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Date>()
        for (chunk in clientIds.distinct().chunked(10)) {
            try {
                firestore.collection("clients")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get().await()
                    .documents.forEach { doc ->
                        (doc.data?.get("lastSeen") as? Timestamp)?.let {
                            result[doc.id] = it.toDate()
                        }
                    }
            } catch (_: Exception) {}
        }
        return result
    }
}
