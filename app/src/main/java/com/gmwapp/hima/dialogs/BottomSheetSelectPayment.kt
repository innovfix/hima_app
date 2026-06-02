package com.gmwapp.hima.dialogs

import com.gmwapp.hima.utils.showAppToast

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
        setupContinueButton()
        setDefaultSelection()

        // Options are pre-loaded by EarningsActivity and passed in as arguments,
        // so the sheet opens ready. Fall back to fetching only if not provided.
        val argBank = arguments?.getString("bank")
        val argUpi = arguments?.getString("upi")
        if (argBank != null || argUpi != null) {
            applyPaymentOptions(argBank ?: "1", argUpi ?: "1")
        } else {
            viewModel.appUpdate()
        }

        viewModel.appUpdateResponseLiveData.observe(this, Observer {
            if (it != null && it.success && it.data.isNotEmpty()) {
                applyPaymentOptions(it.data[0].bank.toString(), it.data[0].upi.toString())
            }
        })





        return binding.root
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    private fun setupListeners() {
        // Whole card tappable — selects that option and highlights it
        binding.upiOption.setOnClickListener { selectOption("upi") }
        binding.bankOption.setOnClickListener { selectOption("bank") }

        // Radio buttons still work as secondary touch targets
        binding.rbUpi.setOnCheckedChangeListener { _, isChecked -> if (isChecked) selectOption("upi") }
        binding.rbBank.setOnCheckedChangeListener { _, isChecked -> if (isChecked) selectOption("bank") }
    }

    private fun selectOption(option: String) {
        if (option == "upi") {
            binding.rbUpi.isChecked = true
            binding.rbBank.isChecked = false
            binding.upiOption.strokeColor = requireContext().getColor(R.color.colorAccent)
            binding.upiOption.strokeWidth = 6
            binding.bankOption.strokeColor = requireContext().getColor(android.R.color.transparent)
            binding.bankOption.strokeWidth = 0
        } else {
            binding.rbBank.isChecked = true
            binding.rbUpi.isChecked = false
            binding.bankOption.strokeColor = requireContext().getColor(R.color.colorAccent)
            binding.bankOption.strokeWidth = 6
            binding.upiOption.strokeColor = requireContext().getColor(android.R.color.transparent)
            binding.upiOption.strokeWidth = 0
        }
    }

    // Continue button
    private fun setupContinueButton() {
        binding.btnSelectPayment.setOnClickListener {
            val selectedOption = when {
                binding.rbUpi.isChecked  -> "upi_transfer"
                binding.rbBank.isChecked -> "bank_transfer"
                else -> null
            }
            if (selectedOption == null) {
                requireContext().showAppToast("Please select any one", Toast.LENGTH_SHORT)
            } else {
                val intent = Intent(requireContext(), WithdrawActivity::class.java)
                intent.putExtra("payment_method", selectedOption)
                startActivity(intent)
                dismiss()
            }
        }
    }

    /** Show/hide bank & UPI options based on the loaded flags ("1" = enabled). */
    private fun applyPaymentOptions(bankFlag: String, upiFlag: String) {
        bank = bankFlag
        upi = upiFlag
        binding.bankOption.visibility = if (bank == "1") View.VISIBLE else View.GONE
        if (upi == "1") {
            binding.upiOption.visibility = View.VISIBLE
            binding.tvUpiInfo.visibility = View.GONE
        } else {
            binding.upiOption.visibility = View.GONE
            binding.tvUpiInfo.visibility = View.GONE
        }
    }

    private fun setDefaultSelection() {
        binding.rbUpi.isChecked = false
        binding.rbBank.isChecked = false
        binding.btnSelectPayment.isEnabled = true
    }
}
