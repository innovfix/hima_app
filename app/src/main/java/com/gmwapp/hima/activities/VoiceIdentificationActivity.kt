package com.gmwapp.hima.activities

import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.callbacks.OnButtonClickListener
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityVoiceIdentificationBinding
import com.gmwapp.hima.dialogs.BottomSheetVoiceIdentification
import com.gmwapp.hima.viewmodels.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File


@AndroidEntryPoint
class VoiceIdentificationActivity : BaseActivity(), OnItemSelectionListener<String?> {
    lateinit var binding: ActivityVoiceIdentificationBinding
    private val profileViewModel: ProfileViewModel by viewModels()
    private val REQUEST_AUDIO_PERMISSION_CODE: Int = 1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceIdentificationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initUI()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_AUDIO_PERMISSION_CODE -> if (grantResults.isNotEmpty()) {
                val permissionToRecord = grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (permissionToRecord) {
                    openVoiceIdentificationPopup()
                } else {
                    Toast.makeText(applicationContext, "Permission Denied", Toast.LENGTH_LONG)
                        .show()
                    requestPermissions()
                }
            }
        }
    }

    private fun openVoiceIdentificationPopup() {
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
            intent.getStringExtra(DConstants.LANGUAGE)?.let { it1 ->
                profileViewModel.getSpeechText(
                    it, it1
                )
            }
        }
        profileViewModel.speechTextLiveData.observe(this, Observer {
            val data = it?.data
            if (data != null && it.success && data.size > 0) {
                val bottomSheet = BottomSheetVoiceIdentification()
                val bundle = Bundle()
                bundle.putString(DConstants.TEXT, data[0].text)
                bottomSheet.arguments = bundle
                bottomSheet.isCancelable = false
                bottomSheet.show(
                    supportFragmentManager, "BottomSheetVoiceIdentification"
                )
            } else {
                Toast.makeText(
                    this@VoiceIdentificationActivity, it?.message, Toast.LENGTH_LONG
                ).show()
            }
        })
        profileViewModel.speechTextErrorLiveData.observe(this, Observer {
            Toast.makeText(
                this@VoiceIdentificationActivity,
                getString(R.string.please_try_again_later),
                Toast.LENGTH_LONG
            ).show()
        })
    }

    private fun initUI() {
        if (checkPermissions()) {
            openVoiceIdentificationPopup()
        } else {
            requestPermissions()
        }
        profileViewModel.voiceUpdateLiveData.observe(this, Observer {
            if (it!=null && it.success) {
                BaseApplication.getInstance()?.getPrefs()?.setUserData(it.data)
                val gender = it.data?.gender
                val status = it.data?.status
                
                if (gender.equals(DConstants.MALE, ignoreCase = true)) {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                } else if (gender.equals(DConstants.FEMALE, ignoreCase = true) && status == 2) {
                    val intent = Intent(this, YoutubeActivity::class.java)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                } else if (gender.equals(DConstants.FEMALE, ignoreCase = true) && status == 1) {
                    val intent = Intent(this, YoutubeActivity::class.java)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                }
            } else {
                Toast.makeText(
                    this@VoiceIdentificationActivity, it?.message, Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun checkPermissions(): Boolean {
        val recordPermission = ContextCompat.checkSelfPermission(this, RECORD_AUDIO)
        return recordPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ActivityCompat.requestPermissions(
                this, arrayOf<String>(
                    RECORD_AUDIO, WRITE_EXTERNAL_STORAGE,
                ), REQUEST_AUDIO_PERMISSION_CODE
            )
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf<String>(
                    RECORD_AUDIO,
                    WRITE_EXTERNAL_STORAGE,
                ), REQUEST_AUDIO_PERMISSION_CODE
            )

        }
    }

    override fun onItemSelected(path: String?) {
        val file: File? = path?.let { File(it) }
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
            file?.asRequestBody()?.let { it1 ->
                MultipartBody.Part.createFormData(
                    "voice", file.name, it1
                )
            }?.let { it2 ->
                profileViewModel.updateVoice(
                    it, it2
                )
            }
        }
    }
}