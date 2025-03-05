package com.gmwapp.hima.agora

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.MainActivity
import com.gmwapp.hima.agora.audio.MaleAudioCallingActivity
import com.gmwapp.hima.agora.video.MaleCallingActivty
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.viewmodels.FemaleUsersViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AgoraRandomCallActivity : AppCompatActivity() {
    private val femaleUsersViewModel: FemaleUsersViewModel by viewModels()
    private var maleUserId: Int? = null
    private var callListener: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance()
    private var callTimeoutTimer: CountDownTimer? = null
    private var femaleUserId: Int? = null
    private var usersCount = 0
    private lateinit var callType: String
    private val triedUserIds = mutableSetOf<Int>()
    private var apiCallCount = 0


    private val PERMISSION_REQ_ID = 22
    private val REQUESTED_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agora_random_call)

        maleUserId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id
        callType = intent.getStringExtra(DConstants.CALL_TYPE).orEmpty()

        if (!checkSelfPermission()) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_REQ_ID)
        } else {
            maleUserId?.let {
                listenForCallStatusChanges(it)
                getRandomUser()
            } ?: run {
                Log.e("AgoraRandomCall", "User ID is null")
                goToMainActivity()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                rejectCall()
                stopCall()
            }
        })
    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, update Firestore for the call

                maleUserId?.let {
                    listenForCallStatusChanges(it)
                    getRandomUser()
                } ?: run {
                    Log.e("AgoraRandomCall", "User ID is null")
                    goToMainActivity()
                }

                } else {
                    Log.e("MaleCallConnectActivity", "User ID or Receiver ID is null")
                }
            } else {
                Log.e("Permission", "Audio permission denied")
            }
        }


    private fun checkSelfPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, REQUESTED_PERMISSIONS[0]) == PackageManager.PERMISSION_GRANTED
    }

    private fun getRandomUser() {
        if (usersCount >= 4) {
            stopCall()
            return
        }

        maleUserId?.let { userId ->
            femaleUsersViewModel.getRandomUser(userId, callType)
            apiCallCount++
            Log.d("API_TRACKER", "API call count: $apiCallCount") // Log count
            observeRandomUser()
        }
    }

    private fun observeRandomUser() {
        femaleUsersViewModel.randomUsersResponseLiveData.removeObservers(this) // Remove previous observers

        femaleUsersViewModel.randomUsersResponseLiveData.observe(this) { response ->
            try {
                if (response == null || !response.success || response.data == null) {
                    Log.e("AgoraRandomCall", "API response is null or failed, retrying...")

                    if (usersCount >= 4) {
                        Log.e("AgoraRandomCall", "Max attempts reached, going to MainActivity")
                        goToMainActivity()
                        return@observe
                    }

                    retryNextUser()
                    return@observe
                }

                val newFemaleUserId = response.data.call_user_id
                Log.d("getRandomUser","$newFemaleUserId")

                if (newFemaleUserId == null) {
                    Log.e("AgoraRandomCall", "call_user_id is null, retrying...")

                    if (usersCount >= 4) {
                        goToMainActivity()
                        return@observe
                    }

                    retryNextUser()
                    return@observe
                }

                if (triedUserIds.contains(newFemaleUserId)) {
                    Log.d("AgoraRandomCall", "Already tried user $newFemaleUserId, fetching another user")
                    femaleUsersViewModel.getRandomUser(maleUserId!!, callType) // Explicitly call API again
                    return@observe
                }

                triedUserIds.add(newFemaleUserId)
                femaleUserId = newFemaleUserId

                val channel = generateChannelName(maleUserId)
                response.data.call_id?.let { callId ->
                    updateFirestoreForCallConnect(maleUserId!!, femaleUserId.toString(), channel, callId, response.data.type)
                }
                startCallTimeout()
            } catch (e: Exception) {
                Log.e("AgoraRandomCall", "Exception in observeRandomUser: ${e.message}")
                goToMainActivity()
            }
        }
    }


    private fun updateFirestoreForCallConnect(maleUserId: Int, femaleUserId: String, channel: String, callId: Int, callType: String) {
        val femaleUserDocRef = db.collection("femaleUsers").document(femaleUserId)
        val maleUserDocRef = db.collection("maleUsers").document(maleUserId.toString())

        db.runTransaction { transaction ->
            val femaleSnapshot = transaction.get(femaleUserDocRef)
            val isFemaleCalling = femaleSnapshot.getBoolean("isCalling") ?: false

            if (isFemaleCalling) throw Exception("User is already in a call")

            transaction.update(femaleUserDocRef, mapOf(
                "isCalling" to true,
                "maleUserId" to maleUserId.toString(),
                "channelName" to channel,
                "callType" to callType,
                "callId" to callId,
                "isRandomCalling" to true
            ))

            transaction.update(maleUserDocRef, mapOf(
                "isCalling" to true,
                "channelName" to channel,
                "callType" to callType,
                "callId" to callId
            ))
        }.addOnSuccessListener {
            Log.d("FirestoreTransaction", "Call setup successfully")
            startCallTimeout()
        }.addOnFailureListener {
            Log.e("FirestoreTransaction", "Failed to start call: ${it.message}")
            Toast.makeText(this, "User is busy", Toast.LENGTH_LONG).show()
            retryNextUser()
        }
    }

    private fun startCallTimeout() {
        callTimeoutTimer?.cancel() // Ensure previous timer is stopped before starting a new one

        callTimeoutTimer = object : CountDownTimer(getCallDuration(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                Log.d("CallTimeout", "Waiting for connection: ${millisUntilFinished / 1000} sec remaining")
            }

            override fun onFinish() {
                Log.d("CallTimeout", "Timeout reached, rejecting call.")
                rejectCall()
            }
        }.start()
    }

    private fun rejectCall() {
        femaleUserId?.let { id ->
            db.collection("femaleUsers").document(id.toString())
                .update(mapOf("isCalling" to false, "channelName" to null, "maleUserId" to null, "callType" to null, "callId" to null))
                .addOnSuccessListener {
                    retryNextUser()
                }
                .addOnFailureListener {
                    Log.e("FirestoreUpdate", "Failed to update Firestore: ", it)
                    retryNextUser()
                }
        }
    }

    private fun retryNextUser() {
        callTimeoutTimer?.cancel()
        usersCount++

        if (usersCount >= 4) {
            stopCall()
        } else {
            Log.d("AgoraRandomCallCallAttempt", "Retrying with a new user. Attempt: $usersCount")
            femaleUsersViewModel.getRandomUser(maleUserId!!, callType) // Ensure new API call
            apiCallCount++
        }
    }


    private fun listenForCallStatusChanges(maleUserId: Int) {
        val callDocRef = db.collection("maleUsers").document(maleUserId.toString())
        callListener = callDocRef.addSnapshotListener { snapshot, _ ->
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val isConnected = snapshot.getBoolean("isConnected") ?: false
            val latestChannelName = snapshot.getString("channelName") ?: ""

            if (isConnected) {
                callTimeoutTimer?.cancel()
                navigateToCallingActivity(latestChannelName)
            }
        }
    }

    private fun navigateToCallingActivity(channelName: String?) {
        if (callTimeoutTimer != null) {
            callTimeoutTimer?.cancel() // Stop the timer before navigating
            callTimeoutTimer = null    // Clear reference to avoid memory leaks
        }

        val intent = if (callType == "audio") {
            Intent(this, MaleAudioCallingActivity::class.java)
        } else {
            Intent(this, MaleCallingActivty::class.java)
        }

        Log.d("channelNamecheck", "Navigating with femaleUserId: $femaleUserId, channelName: $channelName")
        intent.putExtra("channelName", channelName)
        intent.putExtra("femaleUserId", femaleUserId.toString())

        startActivity(intent)
        finish()
    }


    private fun stopCall() {
        maleUserId?.let { id ->
            db.collection("maleUsers").document(id.toString())
                .update(mapOf("isCalling" to false, "channelName" to null, "femaleUserId" to null, "callType" to null, "callId" to null))
                .addOnSuccessListener {

                }
                .addOnFailureListener {
                    Log.e("FirestoreUpdate", "Failed to update Firestore: ", it)
                    retryNextUser()
                }
        }

        goToMainActivity()
    }

    private fun goToMainActivity() {
        Log.d("API_TRACKER", "Total API calls before MainActivity: $apiCallCount")

        callTimeoutTimer?.cancel() // Stop any timers
        callListener?.remove() // Remove Firestore listener
        femaleUsersViewModel.randomUsersResponseLiveData.removeObservers(this) // Remove observers


        finish()
    }



    private fun generateChannelName(maleUserId: Int?) = "${maleUserId}_${System.currentTimeMillis()}"

    private fun getCallDuration() = 7000L

    override fun onDestroy() {
        super.onDestroy()
        Log.d("destroyedTag","destroyed")
    }
}
