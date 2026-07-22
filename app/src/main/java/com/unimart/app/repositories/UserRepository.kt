package com.unimart.app.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.unimart.app.models.BlockedUser
import com.unimart.app.models.User
import com.unimart.app.utils.Resource
import kotlinx.coroutines.tasks.await

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("Users")

    /**
     * Blocks a user by adding them to the subcollection.
     */
    suspend fun blockUser(currentUserId: String, targetUserId: String): Resource<Unit> {
        return try {
            val blockData = BlockedUser(blockedUserId = targetUserId, blockedAt = Timestamp.now())
            usersCollection.document(currentUserId)
                .collection("BlockedUsers")
                .document(targetUserId)
                .set(blockData)
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Failure(e)
        }
    }

    /**
     * Unblocks a user.
     */
    suspend fun unblockUser(currentUserId: String, targetUserId: String): Resource<Unit> {
        return try {
            usersCollection.document(currentUserId)
                .collection("BlockedUsers")
                .document(targetUserId)
                .delete()
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Failure(e)
        }
    }

    /**
     * Checks if targetUserId is blocked by currentUserId.
     */
    suspend fun isUserBlocked(currentUserId: String, targetUserId: String): Boolean {
        return try {
            val doc = usersCollection.document(currentUserId)
                .collection("BlockedUsers")
                .document(targetUserId)
                .get()
                .await()
            doc.exists()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns a list of all User IDs blocked by the current user.
     */
    suspend fun getBlockedUserIds(currentUserId: String): List<String> {
        return try {
            val snapshot = usersCollection.document(currentUserId)
                .collection("BlockedUsers")
                .get()
                .await()
            snapshot.documents.mapNotNull { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches public data for a user profile.
     */
    suspend fun getPublicProfile(userId: String): Resource<User> {
        return try {
            val doc = usersCollection.document(userId).get().await()
            val user = doc.toObject(User::class.java)
            if (user != null) Resource.Success(user) else Resource.Failure(Exception("User not found"))
        } catch (e: Exception) {
            Resource.Failure(e)
        }
    }
}
