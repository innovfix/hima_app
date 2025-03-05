package com.gmwapp.hima.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class FirebaseViewModel :ViewModel() {


    private val db = FirebaseFirestore.getInstance()

    // LiveData to track Firestore update status
    private val _maleUserStatus = MutableLiveData<Boolean>()
    val maleUserStatus: LiveData<Boolean> get() = _maleUserStatus

    private val _femaleUserStatus = MutableLiveData<Boolean>()
    val femaleUserStatus: LiveData<Boolean> get() = _femaleUserStatus

    fun addMaleUserInDB(maleUserId: String?, femaleUserId: String?, isCalling: Boolean?) {
        val maleData = mapOf(
            "maleUserId" to maleUserId, // Nullable values will be stored
            "femaleUserId" to femaleUserId,
            "isCalling" to isCalling,
            "channelName" to null,
            "isConnected" to false,
            "callType" to null,
            "callId" to null

        )


        // Update male user document
        db.collection("maleUsers").document(maleUserId.toString())
            .set(maleData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("Firestore", "Male user updated successfully")
                _maleUserStatus.value = true // Notify UI of success
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error updating male user", e)
                _maleUserStatus.value = false // Notify UI of failure
            }


    }


    fun addFemaleUserInDB(maleUserId: String?, femaleUserId: String?, isCalling: Boolean?) {
        val femaleData = mapOf(
            "femaleUserId" to femaleUserId,
            "maleUserId" to maleUserId,
            "isCalling" to isCalling,
            "channelName" to null,
            "isConnected" to false,
            "callType" to null,
            "remainingTime" to  null,
            "callId" to null,
            "isRandomCalling" to false

        )

        // Update female user document
        db.collection("femaleUsers").document(femaleUserId.toString())
            .set(femaleData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("Firestore", "Female user updated successfully")
                _femaleUserStatus.value = true // Notify UI of success
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error updating female user", e)
                _femaleUserStatus.value = false // Notify UI of failure
            }


    }


}