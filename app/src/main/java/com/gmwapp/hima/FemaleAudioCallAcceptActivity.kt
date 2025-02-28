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
import com.gmwapp.hima.databinding.ActivityFemaleAudioCallAcceptBinding
import com.gmwapp.hima.databinding.ActivityFemaleCallAcceptBinding
import com.google.firebase.firestore.FirebaseFirestore

class FemaleAudioCallAcceptActivity : AppCompatActivity() {
    lateinit var binding: ActivityFemaleAudioCallAcceptBinding
    private var channelName: String? = null
    private var maleUserId: String? = null
    private var femaleUserId: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityFemaleAudioCallAcceptBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // Handle the back press


        channelName = intent.getStringExtra("channelName")
        maleUserId = intent.getStringExtra("maleUserId")
        Log.d("FemaleCallAcceptActivity", "Received channelName: $channelName, callerUserId: $maleUserId")

        val userData = getInstance()?.getPrefs()?.getUserData()
        femaleUserId = userData?.id.toString()



        binding.accpet.setOnClickListener {
            val intent = Intent(this@FemaleAudioCallAcceptActivity, FemaleAudioCallingActivity::class.java).apply {
                putExtra("channelName", channelName)
                putExtra("maleUserId", maleUserId)
            }
            callIsconnected()
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            startActivity(intent)
            finish()
        }

        binding.reject.setOnClickListener {
            rejectCall()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("FemaleCallAcceptActivity", "onBackPressed called via Dispatcher")
                showExitDialog()
            }
        })


    }

    private fun callIsconnected() {

        if (maleUserId != null && femaleUserId !=null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("maleUsers").document(maleUserId.toString())
                .update(mapOf(
                    "isConnected" to true
                ))
                .addOnSuccessListener {

                }
                .addOnFailureListener { e ->
                    Log.e("FirestoreUpdate", "Failed to update Firestore: ", e)
                }


            db.collection("femaleUsers").document(femaleUserId!!)
                .update(
                    mapOf(
                        "isConnected" to true,
                    )
                )
                .addOnSuccessListener {

                }
                .addOnFailureListener { e ->
                    Log.e("FirestoreUpdate", "Failed to update Firestore: ", e)
                }




        } else {
            Log.e("FemaleCallAcceptActivity", "callerUserId is null, cannot update Firestore")
        }
    }

    private fun rejectCall() {


        val userData = getInstance()?.getPrefs()?.getUserData()
        val userid = userData?.id


        if (maleUserId != null && userid !=null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("maleUsers").document(maleUserId!!)
                .update(mapOf(
                    "isCalling" to false,
                    "channelName" to null,
                    "femaleUserId" to null,
                    "callType" to null


                ))
                .addOnSuccessListener {
                    Log.d("FirestoreUpdate", "isCalling set to false successfully")
                    finish() // Close activity after rejection
                }
                .addOnFailureListener { e ->
                    Log.e("FirestoreUpdate", "Failed to update Firestore: ", e)
                }


            db.collection("femaleUsers").document(userid.toString())
                .update(
                    mapOf(
                        "isCalling" to false,
                        "channelName" to null,
                        "callerUserId" to null,
                        "callType" to null

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


    private fun showExitDialog() {
        val builder = AlertDialog.Builder(this@FemaleAudioCallAcceptActivity)
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

}