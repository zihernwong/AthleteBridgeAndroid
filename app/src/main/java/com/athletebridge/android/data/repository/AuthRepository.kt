package com.athletebridge.android.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signUp(email: String, password: String, userType: String, name: String = ""): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(Exception("User is null after signup"))

            // Write userType document (matches iOS: userType/{uid} -> { type: "COACH"/"CLIENT" })
            firestore.collection("userType").document(user.uid)
                .set(mapOf("type" to userType))
                .await()

            // Write name into the profile document so iOS can find it immediately
            if (name.isNotBlank()) {
                val parts = name.trim().split(" ", limit = 2)
                val firstName = parts.getOrElse(0) { "" }
                val lastName = parts.getOrElse(1) { "" }
                val profileData = mapOf(
                    "name" to name,
                    "Name" to name,
                    "FirstName" to firstName,
                    "LastName" to lastName
                )
                val collection = if (userType.uppercase() == "COACH") "coaches" else "clients"
                firestore.collection(collection).document(user.uid)
                    .set(profileData, SetOptions.merge())
                    .await()
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(Exception("User is null after login"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Write the current user's lastSeen timestamp to their profile document (matches iOS updateLastSeen).
     *  Uses update (not set) so a missing profile document fails silently instead of being created bare. */
    suspend fun updateLastSeen() {
        val uid = auth.currentUser?.uid ?: return
        val timestamp = com.google.firebase.firestore.FieldValue.serverTimestamp()
        for (collection in listOf("clients", "coaches")) {
            try {
                firestore.collection(collection).document(uid)
                    .update("lastSeen", timestamp).await()
            } catch (_: Exception) {}
        }
    }

    suspend fun fetchUserType(uid: String): String? {
        return try {
            val doc = firestore.collection("userType").document(uid).get().await()
            // Check multiple field name variants for iOS compatibility
            doc.getString("type")
                ?: doc.getString("userType")
                ?: doc.getString("Type")
                ?: doc.getString("UserType")
        } catch (e: Exception) {
            null
        }
    }

    fun logout() {
        auth.signOut()
    }

    /**
     * Permanently deletes the current user's account.
     * Removes Firestore profile data and the Firebase Auth user record.
     * Note: Firebase requires recent authentication for this operation; if the
     * user's session is stale, this will fail with an error asking them to re-login.
     */
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
            val uid = user.uid

            // Determine user type to delete the correct profile document
            val userTypeDoc = firestore.collection("userType").document(uid).get().await()
            val userType = userTypeDoc.getString("type")?.uppercase()

            // Delete profile document
            val profileCollection = if (userType == "COACH") "coaches" else "clients"
            firestore.collection(profileCollection).document(uid).delete().await()

            // Delete userType document
            firestore.collection("userType").document(uid).delete().await()

            // Delete Firebase Auth account (must be last — invalidates the session)
            user.delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
