package com.gmwapp.hima

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gmwapp.hima.BaseApplication.Companion.getInstance
import com.gmwapp.hima.constants.DConstants
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MaleCallConnectActivity : AppCompatActivity() {
    private var femaleUserId: String? = null
    private var callType: String? = null
    private var maleUserid: Int? = null
    private var callListener: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance()
    private lateinit var channel: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_male_call_connect)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val userData = getInstance()?.getPrefs()?.getUserData()

        maleUserid = userData?.id
        femaleUserId = intent.getStringExtra(DConstants.RECEIVER_ID)
        callType = intent.getStringExtra(DConstants.CALL_TYPE)

        channel = generateChannelName(maleUserid)

        // If both user IDs exist, update Firestore to mark the call as connected
        if (maleUserid != null && femaleUserId != null) {
            updateFirestoreForCallConnect(maleUserid!!, femaleUserId!!, channel)
        } else {
            Log.e("MaleCallConnectActivity", "User ID or Receiver ID is null")
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("FemaleCallAcceptActivity", "onBackPressed called via Dispatcher")
                showExitDialog()
            }
        })

        maleUserid?.let { listenForCallStatusChanges(it) }
    }


    private fun listenForCallStatusChanges(maleUserId: Int) {
        val callDocRef = db.collection("maleUsers").document(maleUserId.toString())
        callListener = callDocRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("FirestoreListenercheck", "Error listening for call status changes", e)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                Log.e("FirestoreListenercheck", "Snapshot does not exist")
                return@addSnapshotListener
            }
            val isConnected = snapshot.getBoolean("isConnected") ?: false
            Log.d("FirestoreListenercheck", "isConnected updated: $isConnected")

            if (isConnected) {
                navigateToCallingActivity(channel)
            }
        }

    }

    private fun navigateToCallingActivity(channelName: String?) {
        val intent = Intent(this, MaleCallingActivty::class.java).apply {
            putExtra("channelName", channelName)
            putExtra("femaleUserId", femaleUserId)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        startActivity(intent)
        finish()  // Close this activity to avoid stacking
    }
    private fun showExitDialog() {
        val builder = AlertDialog.Builder(this@MaleCallConnectActivity)
        builder.setTitle("Reject Call")
        builder.setMessage("Do you want to reject the call?")

        builder.setPositiveButton("Yes") { dialog, _ ->
            rejectCall()
            dialog.dismiss()
        }

        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }

        builder.setCancelable(false)
        builder.show()
    }

    private fun rejectCall() {

        if (maleUserid != null && femaleUserId !=null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("maleUsers").document(maleUserid.toString())
                .update(mapOf(
                    "isCalling" to false,
                    "channelName" to null,
                    "femaleUserId" to null

                ))
                .addOnSuccessListener {
                    Log.d("FirestoreUpdate", "isCalling set to false successfully")
                    finish() // Close activity after rejection
                }
                .addOnFailureListener { e ->
                    Log.e("FirestoreUpdate", "Failed to update Firestore: ", e)
                }


            db.collection("femaleUsers").document(femaleUserId!!)
                .update(
                    mapOf(
                        "isCalling" to false,
                        "channelName" to null,
                        "maleUserId" to null
                    )
                )
                .addOnSuccessListener {
                    Log.d("FirestoreUpdate", "isCalling set to false and channelName set to null successfully")
                    finish() // Close activity after rejection
                }
                .addOnFailureListener { e ->
                    Log.e("FirestoreUpdate", "Failed to update Firestore: ", e)
                }




        } else {
            Log.e("FemaleCallAcceptActivity", "callerUserId is null, cannot update Firestore")
        }
    }


    private fun updateFirestoreForCallConnect(
        maleUserId: Int,
        femaleUserId: String,
        channel: String
    ) {
        val db = FirebaseFirestore.getInstance()

        // Update male user's Firestore document
        db.collection("maleUsers").document(maleUserId.toString())
            .update(
                mapOf(
                    "isCalling" to true,
                    "channelName" to channel
                )
            )
            .addOnSuccessListener {
                Log.d("FirestoreUpdate", "Male user isInCall set to true")
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreUpdate", "Failed to update male user: ", e)
            }

        // Update female user's Firestore document
        db.collection("femaleUsers").document(femaleUserId)
            .update(
                mapOf(
                    "isCalling" to true,
                    "maleUserId" to maleUserId.toString(),
                    "channelName" to channel
                )
            )
            .addOnSuccessListener {
                Log.d("FirestoreUpdate", "Female user isInCall and callerUserId updated")
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreUpdate", "Failed to update female user: ", e)
            }
    }

    fun generateChannelName(maleUserId: Int?): String {
        return "${maleUserId}_${System.currentTimeMillis()}"
    }
}