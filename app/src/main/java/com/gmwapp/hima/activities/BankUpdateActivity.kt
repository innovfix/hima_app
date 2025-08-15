package com.gmwapp.hima.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityBankUpdateBinding
import com.gmwapp.hima.retrofit.responses.UserData
import com.gmwapp.hima.viewmodels.BankViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class BankUpdateActivity : BaseActivity() {

    private lateinit var binding: ActivityBankUpdateBinding
    val profileViewModel: ProfileViewModel by viewModels()

    val viewModel: BankViewModel by viewModels()

    private var loadingDialog: AlertDialog? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBankUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initUI()

    }



    private fun initUI() {


        val prefs = BaseApplication.getInstance()?.getPrefs()
        val userData = prefs?.getUserData()

        val accountHolderName = userData?.holder_name
        val accountNumber = userData?.account_num
        val ifscCode = userData?.ifsc
        val bankName = userData?.bank
        val branchName = userData?.branch

        // set text
        binding.etHolderName.setText(accountHolderName)
        binding.etAccountNumber.setText(accountNumber)
        binding.etIfsccode.setText(ifscCode)
        binding.etBankName.setText(bankName)
        binding.etBranchName.setText(branchName)


//
//       disableCopyPaste(binding.etAccountNumber)
//        disableCopyPaste(binding.etReEnterAccountNumber)

        val blockSuggestionPaste = InputFilter { source, _, _, _, _, _ ->
            // If more than 1 character is being inserted at once, block it
            if (source != null && source.length > 1) {
                ""
            } else {
                null
            }
        }

        binding.etAccountNumber.filters = arrayOf(blockSuggestionPaste)
        binding.etReEnterAccountNumber.filters = arrayOf(blockSuggestionPaste)


        var userdata  =BaseApplication.getInstance()?.getPrefs()?.getUserData()
        if (!userdata?.holder_name.isNullOrEmpty()){
            binding.tvHolderName.visibility= View.VISIBLE
            binding.etHolderName.visibility= View.VISIBLE
        }else{
            binding.etReEnterAccountNumber.visibility = View.VISIBLE
            binding.tvReEnterAccountNumber.visibility = View.VISIBLE
        }





        binding.ivBack.setOnClickListener {
            onBackPressed()
        }

        val reEnterVisibilityWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (binding.tvHolderName.visibility == View.VISIBLE) {
                    binding.etReEnterAccountNumber.visibility = View.VISIBLE
                    binding.tvReEnterAccountNumber.visibility = View.VISIBLE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        binding.etAccountNumber.addTextChangedListener(reEnterVisibilityWatcher)
        binding.etIfsccode.addTextChangedListener(reEnterVisibilityWatcher)



//
//        viewModel.bankResponseLiveData.observe(this, Observer {
//            if (it.success) {
//                showToast(it.message)
//            } else {
//                showToast(it.message)
//            }
//
//        })


        // Set up TextWatcher for all fields
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateFields()
            }

            override fun afterTextChanged(s: Editable?) {}
        }

        // Attach TextWatcher to all EditTexts
        binding.etHolderName.addTextChangedListener(textWatcher)
        binding.etAccountNumber.addTextChangedListener(textWatcher)
        binding.etIfsccode.addTextChangedListener(textWatcher)
        binding.etBankName.addTextChangedListener(textWatcher)
        binding.etBranchName.addTextChangedListener(textWatcher)
        binding.etReEnterAccountNumber.addTextChangedListener(textWatcher)

        // Disable button initially
        binding.btnUpdate.isEnabled = false

        // Handle button click
        binding.btnUpdate.setOnClickListener {

            val accNum = binding.etAccountNumber.text.toString().trim()
            val reAccNum = binding.etReEnterAccountNumber.text.toString().trim()

            // 1. Check account numbers match
            if (binding.etReEnterAccountNumber.visibility == View.VISIBLE && accNum != reAccNum) {
                binding.etReEnterAccountNumber.error = "Account numbers do not match"
                return@setOnClickListener
            }


            // Perform the update action
            BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let { it1 ->

                viewModel.updatedBank(
                    it1,
                    binding.etHolderName.text.toString(),
                    binding.etAccountNumber.text.toString(),
                    binding.etIfsccode.text.toString(),
                    "Bank",
                    "Branch"
                )

                showLoading()

            }


        }

        viewModel.bankResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {
                hideLoading()
                showToast(it.message)


                BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id?.let {
                    profileViewModel.getUsers(it)
                }

                if (!it.data.holder_name.isNullOrEmpty()){
                    binding.tvHolderName.visibility= View.VISIBLE
                    binding.etHolderName.visibility= View.VISIBLE
                }

            }
        })

        viewModel.bankErrorLiveData.observe(this, Observer { errorMessage ->
            hideLoading()
            binding.etAccountNumber.text?.clear()
            binding.etReEnterAccountNumber.text?.clear()
            binding.etIfsccode.text.clear()
            binding.etHolderName.text.clear()
            errorMessage?.let {
                showToast(it) // Show the error message as a Toast
            }
        })


        profileViewModel.getUserLiveData.observe(this, Observer {
            it.data?.let { it1 ->
                BaseApplication.getInstance()?.getPrefs()?.setUserData(it1)
            }
            onBackPressed()
            finish()
        })




    }

    private fun validateFields() {
        val accountHolderName = binding.etHolderName.text.toString().trim()
        val accountNumber = binding.etAccountNumber.text.toString().trim()
        val ifscCode = binding.etIfsccode.text.toString().trim()
        val bankName = binding.etBankName.text.toString().trim()
        val branchName = binding.etBranchName.text.toString().trim()
        val reEnterAccountNumber = binding.etReEnterAccountNumber.text.toString().trim()


        // Regex to detect special characters
        val specialCharRegex = "[^a-zA-Z0-9 ]".toRegex()

        var isFieldsValid = true

        // Validate each field and show error if invalid
        if (specialCharRegex.containsMatchIn(accountHolderName)) {
            binding.etHolderName.error = "Special characters are not allowed"
            isFieldsValid = false
        } else {
            binding.etHolderName.error = null
        }

        if (specialCharRegex.containsMatchIn(accountNumber)) {
            binding.etAccountNumber.error = "Special characters are not allowed"
            isFieldsValid = false
        } else {
            binding.etAccountNumber.error = null
        }

        if (specialCharRegex.containsMatchIn(ifscCode)) {
            binding.etIfsccode.error = "Special characters are not allowed"
            isFieldsValid = false
        } else {
            binding.etIfsccode.error = null
        }

        if (specialCharRegex.containsMatchIn(bankName)) {
            binding.etBankName.error = "Special characters are not allowed"
            isFieldsValid = false
        } else {
            binding.etBankName.error = null
        }

        if (specialCharRegex.containsMatchIn(branchName)) {
            binding.etBranchName.error = "Special characters are not allowed"
            isFieldsValid = false
        } else {
            binding.etBranchName.error = null
        }

        // Check if all fields are non-empty
        isFieldsValid = isFieldsValid &&
                accountNumber.isNotEmpty() &&
                ifscCode.isNotEmpty()&&
                reEnterAccountNumber.isNotEmpty()


        // Enable or disable the update button
        binding.btnUpdate.isEnabled = isFieldsValid
    }


    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }


    private fun showLoading() {
        if (loadingDialog == null) {
            val progressBar = ProgressBar(this).apply {
                isIndeterminate = true
            }
            loadingDialog = AlertDialog.Builder(this)
                .setView(progressBar)
                .setCancelable(false)
                .create()
        }
        loadingDialog?.show()
    }

    private fun hideLoading() {
        loadingDialog?.dismiss()
    }

    private fun disableCopyPaste(editText: EditText) {
        editText.customInsertionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }
        editText.isLongClickable = false
        editText.setTextIsSelectable(false)
    }

}
