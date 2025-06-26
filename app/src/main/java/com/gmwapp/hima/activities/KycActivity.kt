package com.gmwapp.hima.activities

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityKycBinding
import com.gmwapp.hima.retrofit.responses.UserData
import com.gmwapp.hima.verification.PanRepository
import com.gmwapp.hima.verification.PanViewModel
import com.gmwapp.hima.verification.PanViewModelFactory
import com.gmwapp.hima.viewmodels.FcmNotificationViewModel
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.gmwapp.hima.viewmodels.PanCardViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KycActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKycBinding
    private val panCardViewModel: PanCardViewModel by viewModels()

    private val loginViewModel: LoginViewModel by viewModels()



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding= ActivityKycBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

        userData?.let { loginViewModel.login(it.mobile) }


        panObserver()
        setupPanValidation()
        binding.tvCurrentBalance.text = "₹" + userData?.balance.toString()

        binding.btnSubmit.setOnClickListener {
            val panName = binding.etPanName.text.toString().trim()
            val panNumber = binding.etPanNumber.text.toString().trim()

            when {
                panName.isEmpty() -> {
                    Snackbar.make(binding.root, "Name cannot be empty", Snackbar.LENGTH_SHORT).show()
                }

                panNumber.isEmpty() || panNumber.length < 10 -> {
                    Snackbar.make(binding.root, "Enter a valid 10-digit PAN number", Snackbar.LENGTH_SHORT).show()
                }

                else -> {
                  //  panVerification(panNumber,panName)
                   userData?.let { it1 -> panCardViewModel.updatePanCard(it1.id, panName, panNumber) }
                }
            }
        }

    }

    private fun setupPanValidation() {
        val button = binding.btnSubmit
        val nameField = binding.etPanName
        val numberField = binding.etPanNumber

        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val nameNotEmpty = nameField.text.toString().trim().isNotEmpty()
                val numberNotEmpty = numberField.text.toString().trim().isNotEmpty()
                button.isEnabled = nameNotEmpty && numberNotEmpty
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        }

        nameField.addTextChangedListener(textWatcher)
        numberField.addTextChangedListener(textWatcher)
    }

    fun panObserver(){
        panCardViewModel.panUpdateLiveData.observe(this) { response ->
            if (response != null && response.success) {
                val data = response.data
                Toast.makeText(this, response.message, Toast.LENGTH_SHORT).show()
                val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()

                userData?.let { loginViewModel.login(it.mobile) }

            } else {
                Snackbar.make(binding.root, response?.message ?: "Update failed", Snackbar.LENGTH_SHORT).show()
            }
        }

        panCardViewModel.panUpdateErrorLiveData.observe(this) { error ->
            Snackbar.make(binding.root, error ?: "Something went wrong", Snackbar.LENGTH_SHORT).show()
        }

        loginViewModel.loginResponseLiveData.observe(this, Observer {

            if (it.success) {
                if (!it.data?.pancard_name.isNullOrEmpty()&& !it.data?.pancard_number.isNullOrEmpty()){
                    val pancardName = it.data?.pancard_name
                    val pancardNumber = it.data?.pancard_number
                    binding.etPanName.setText(pancardName)
                    binding.etPanNumber.setText(pancardNumber)
                }

            }
        })

    }

    fun panVerification(panNumber:String,panName:String){

        val repository = PanRepository()
        val viewModelFactory = PanViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, viewModelFactory).get(PanViewModel::class.java)

        viewModel.verifyPan(panNumber, panName)

        viewModel.response.observe(this) { response ->
            if (response.isSuccessful) {
                val body = response.body()
                println("PAN Verified: ${body?.pan}, Name: ${body?.name}")
                Log.d("PanResponse","$body")
            } else {
                println("Error: ${response.code()}")
            }
        }

    }

}