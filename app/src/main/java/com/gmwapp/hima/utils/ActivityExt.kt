package com.gmwapp.hima.utils

import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gmwapp.hima.R

/**
 * Apply system bar insets as padding to [rootView] and paint the status bar.
 *
 * Needed because AppTheme sets windowIsTranslucent=true, which makes Android
 * ignore the XML statusBarColor attribute AND break fitsSystemWindows cascading
 * through FrameLayout/ViewFlipper roots.
 *
 * Call from onCreate AFTER setContentView.
 *
 * @param rootView       top-level view to pad (usually binding.root)
 * @param statusBarColor color resource for the status bar (match the header)
 * @param applyBottom    pad bottom for nav/gesture bar (default true)
 * @param applyIme       also pad for the soft keyboard (use on chat/input screens)
 */
fun AppCompatActivity.applySystemBarInsets(
    rootView: View,
    @ColorRes statusBarColor: Int = R.color.colorAccent,
    applyBottom: Boolean = true,
    applyIme: Boolean = false,
    darkStatusBarIcons: Boolean = false,
) {
    window.statusBarColor = ContextCompat.getColor(this, statusBarColor)
    WindowInsetsControllerCompat(window, rootView)
        .isAppearanceLightStatusBars = darkStatusBarIcons
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
        val typeMask = if (applyIme) {
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
        } else {
            WindowInsetsCompat.Type.systemBars()
        }
        val bars = insets.getInsets(typeMask)
        v.setPadding(bars.left, bars.top, bars.right, if (applyBottom) bars.bottom else 0)
        WindowInsetsCompat.CONSUMED
    }
}

/**
 * LAI-133: shared back-press policy for post-OTP onboarding screens.
 *
 * Each onboarding activity used to roll its own back behavior — GetName
 * silently swallowed back, SelectGender / SelectLanguage / FemaleAbout each
 * inflated their own copy of dialog_discard_changes, and AiOnboarding had its
 * own ViewFlipper-aware policy. The duplication produced visibly different UX
 * across screens. This helper centralizes the dialog so every onboarding step
 * (except AiOnboarding's in-chat block, which stays bespoke) shows the same
 * confirm prompt and only finishes the activity on the confirm tap.
 *
 * Wire it up once in onCreate, after setContentView:
 *
 *     registerOnboardingBackConfirm()
 *
 * Or, if a screen wants to skip the dialog when there's nothing to lose:
 *
 *     registerOnboardingBackConfirm(shouldConfirm = { hasUnsavedInput() })
 */
fun AppCompatActivity.registerOnboardingBackConfirm(
    shouldConfirm: () -> Boolean = { true },
    onExit: () -> Unit = { finish() },
) {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!shouldConfirm()) {
                onExit()
                return
            }
            showOnboardingBackConfirm(onExit)
        }
    })
}

fun AppCompatActivity.showOnboardingBackConfirm(onExit: () -> Unit = { finish() }) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_discard_changes, null)
    val dialog = AlertDialog.Builder(this)
        .setView(dialogView)
        .setCancelable(true)
        .create()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

    dialogView.findViewById<View>(R.id.btn_keep_editing).setOnClickListener {
        dialog.dismiss()
    }
    dialogView.findViewById<View>(R.id.btn_go_back).setOnClickListener {
        dialog.dismiss()
        onExit()
    }
    dialog.show()
}
