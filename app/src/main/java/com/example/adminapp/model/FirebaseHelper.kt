package com.example.adminapp.model

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object FirebaseHelper {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun getPasswordPolicy(): PasswordPolicy? {
        return try {
            val doc = firestore.collection("settings").document("password_policy").get().await()
            val policy = doc.toObject(PasswordPolicy::class.java)
            Log.d("NEha's Log", "Fetched policy from Firestore: $policy")
            doc.toObject(PasswordPolicy::class.java)
        } catch (e: Exception) {
            Log.e("NEha's Log", "Error fetching policy: ${e.message}")

            e.printStackTrace()
            null
        }
    }
}