package com.gmwapp.hima.utils

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.gmwapp.hima.R

/**
 * [B11] #544 — shared "Turn off Do Not Disturb?" confirm card.
 *
 * DND can be turned OFF from several places (the DND toggle itself, and turning
 * Audio or Video availability ON while DND is active). All of them must show the
 * same confirmation so the creator can't disable DND — and start receiving calls —
 * by accident. Reuses the B16 [R.layout.dialog_dnd_confirm] card with turn-off text.
 *
 * onConfirmed → caller proceeds with the disable.
 * onCancelled → caller reverts the toggle that triggered this (keep DND on).
 * Back / outside-tap counts as cancel.
 */
object DndDisableConfirm {

    fun show(fragment: Fragment, onConfirmed: () -> Unit, onCancelled: () -> Unit) {
        if (!fragment.isAdded) { onCancelled(); return }
        val view = fragment.layoutInflater.inflate(R.layout.dialog_dnd_confirm, null)
        view.findViewById<TextView>(R.id.tv_dnd_confirm_title).setText(R.string.dnd_disable_confirm_title)
        view.findViewById<TextView>(R.id.tv_dnd_confirm_body).setText(R.string.dnd_disable_confirm_body)
        view.findViewById<TextView>(R.id.tv_dnd_confirm_warning).setText(R.string.dnd_disable_confirm_warning)
        view.findViewById<MaterialButton>(R.id.btn_dnd_confirm_positive).setText(R.string.dnd_disable_confirm_positive)

        val dialog = AlertDialog.Builder(fragment.requireContext())
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.5f)

        var decided = false
        view.findViewById<MaterialButton>(R.id.btn_dnd_confirm_cancel).setOnClickListener {
            decided = true
            dialog.dismiss()
            onCancelled()
        }
        view.findViewById<MaterialButton>(R.id.btn_dnd_confirm_positive).setOnClickListener {
            decided = true
            dialog.dismiss()
            onConfirmed()
        }
        // back / outside-tap → cancel (dismiss() from a button does NOT fire this)
        dialog.setOnCancelListener { if (!decided) onCancelled() }
        dialog.show()
    }
}
