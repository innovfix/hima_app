package com.gmwapp.hima.dialogs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.gmwapp.hima.activities.CancelFeedbackActivity
import com.gmwapp.hima.databinding.BottomSheetCancelConfirmBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BottomSheetCancelConfirm : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCancelConfirmBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCancelConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnKeep.setOnClickListener { dismiss() }

        binding.btnConfirmCancel.setOnClickListener {
            dismiss()
            activity?.finish()
            requireContext().startActivity(
                Intent(requireContext(), CancelFeedbackActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
