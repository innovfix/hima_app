package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.WithdrawActivity
import com.gmwapp.hima.databinding.BottomSheetSelectPaymentBinding
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class BottomSheetSelectPayment : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetSelectPaymentBinding
    val viewModel: LoginViewModel by viewModels()
    var bank = ""
    var upi = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetSelectPaymentBinding.inflate(inflater, container, false)

        setupListeners()
        viewModel.appUpdate()
        setDefaultSelection() // Set default selection here


        viewModel.appUpdateResponseLiveData.observe(this, Observer {
            if (it != null && it.success) {

                 bank = it.data[0].bank.toString()
                 upi = it.data[0].upi.toString()


                if(bank == "1"){
                    binding.bankOption.visibility = View.VISIBLE
                }
                else {
                    binding.bankOption.visibility = View.GONE

                }

                if(upi == "1"){
                    binding.upiOption.visibility = View.VISIBLE
                    binding.tvUpiInfo.visibility = View.VISIBLE

                }
                else {
                    binding.upiOption.visibility = View.GONE
                    binding.tvUpiInfo.visibility = View.GONE

                }

            }
        })





        return binding.root
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    private fun setupListeners() {
        // Set up listeners for the radio buttons
        binding.rbUpi.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.btnSelectPayment.isEnabled = true
                binding.rbBank.isChecked = false
            }
        }

        binding.rbBank.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.btnSelectPayment.isEnabled = true
                binding.rbUpi.isChecked = false
            }
        }

        // Set up the select button click listener
        binding.btnSelectPayment.setOnClickListener {
            val selectedOption = when {
                binding.rbUpi.isChecked -> "upi_transfer"
                binding.rbBank.isChecked -> "bank_transfer"
                else -> null
            }

            if (selectedOption == null) {
                Toast.makeText(requireContext(), "Please select any one", Toast.LENGTH_SHORT).show()
                // Do not dismiss if nothing is selected
            } else {
                val intent = Intent(requireContext(), WithdrawActivity::class.java)
                intent.putExtra("payment_method", selectedOption)
                startActivity(intent)
                dismiss()
            }

//            val intent = Intent(requireContext(), WithdrawActivity::class.java)
//
//            // Add optional extras if needed
//            intent.putExtra("payment_method", selectedOption)
//
//            startActivity(intent)
//
//            // Close the bottom sheet
//            dismiss()
        }
    }

    private fun setDefaultSelection() {
        // Set UPI as the default selected option
        binding.rbUpi.isChecked = false
        binding.btnSelectPayment.isEnabled = true // Enable the button since a default is selected
    }
}
