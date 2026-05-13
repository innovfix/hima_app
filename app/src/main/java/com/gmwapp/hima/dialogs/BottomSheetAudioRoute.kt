package com.gmwapp.hima.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.gmwapp.hima.R
import com.gmwapp.hima.utils.CallAudioRouter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Three-way audio-route picker shown when a Bluetooth headset is connected
 * during a call (when none is connected the calling activities use the binary
 * speaker toggle instead). Invokes the caller-supplied callback with the
 * chosen [CallAudioRouter.AudioRoute] and dismisses itself; the activity is
 * responsible for applying the route via the router and updating its button
 * icon.
 */
class BottomSheetAudioRoute : BottomSheetDialogFragment() {

    private var bluetoothAvailable: Boolean = false
    private var onSelected: ((CallAudioRouter.AudioRoute) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.bottom_sheet_audio_route, container, false)
        // Make the Material bottom-sheet container transparent so our rounded
        // top corners on bg_audio_route_sheet aren't clipped by the default
        // white surface behind it.
        view.viewTreeObserver.addOnGlobalLayoutListener {
            (view.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        val earpieceRow = view.findViewById<View>(R.id.tv_route_earpiece)
        val speakerRow = view.findViewById<View>(R.id.tv_route_speaker)
        val bluetoothRow = view.findViewById<View>(R.id.tv_route_bluetooth)

        bluetoothRow.visibility = if (bluetoothAvailable) View.VISIBLE else View.GONE

        earpieceRow.setOnClickListener { pick(CallAudioRouter.AudioRoute.EARPIECE) }
        speakerRow.setOnClickListener { pick(CallAudioRouter.AudioRoute.SPEAKER) }
        bluetoothRow.setOnClickListener { pick(CallAudioRouter.AudioRoute.BLUETOOTH) }

        return view
    }

    private fun pick(route: CallAudioRouter.AudioRoute) {
        onSelected?.invoke(route)
        dismissAllowingStateLoss()
    }

    companion object {
        fun show(
            fm: FragmentManager,
            router: CallAudioRouter,
            onSelected: (CallAudioRouter.AudioRoute) -> Unit
        ) {
            // Re-check BT availability at show-time so the sheet reflects the
            // live state even if the user connected/disconnected a headset
            // between construction and display.
            val sheet = BottomSheetAudioRoute().apply {
                bluetoothAvailable = router.isBluetoothConnected()
                this.onSelected = onSelected
            }
            sheet.show(fm, TAG)
        }

        private const val TAG = "BottomSheetAudioRoute"
    }
}
