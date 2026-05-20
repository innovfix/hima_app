package com.gmwapp.hima.utils

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * Tester report: a creator on a device with a non-functional camera (hardware
 * fault, not a permission denial) joined an incoming video call. Agora's
 * Camera2 capture path threw on `agoraEngine.startPreview()` and the process
 * crashed with the generic Samsung "Something went wrong with HI ma" dialog.
 * WhatsApp on the same handset doesn't crash — it falls back to the user's
 * profile picture in place of the local preview.
 *
 * This helper is the single source of truth for "does this device actually
 * have a camera we can open right now?". Callers gate every camera-touching
 * Agora call ([setupLocalVideo], [startPreview], [enableLocalVideo(true)])
 * behind this check; when it returns false they should:
 *   1. skip the local SurfaceView setup
 *   2. call `agoraEngine.enableLocalVideo(false)` and
 *      `agoraEngine.muteLocalVideoStream(true)` so Agora doesn't attempt
 *      camera capture
 *   3. hide the local-preview card (the remote already shows their avatar
 *      via the B058 skeleton when no video frames arrive)
 *   4. surface a small toast so the user knows audio still works
 *
 * Two-layer check because the layers fail on different devices:
 *   - `hasSystemFeature(FEATURE_CAMERA_ANY)` is the manifest-declared capability.
 *     Reliable when the device was manufactured WITHOUT a camera (rare).
 *   - `CameraManager.cameraIdList` reflects the actual runtime state. Catches
 *     the more common case where hardware exists but is faulty / disabled by
 *     the OS for a reason we can't see — exactly the tester's scenario.
 */
object CameraAvailability {

    private const val TAG = "CameraAvailability"

    fun isCameraAvailable(context: Context): Boolean {
        // Layer 1 — manifest-declared capability.
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            Log.w(TAG, "Device does not declare FEATURE_CAMERA_ANY — treating as no camera")
            return false
        }
        // Layer 2 — runtime check via Camera2. The CameraManager throws
        // CameraAccessException in a handful of OEM-specific edge cases
        // (notably some Samsung firmwares when the camera HAL is wedged);
        // a throw on the lookup itself means the camera subsystem is in a
        // state where opening it is going to fail. Treat that as unavailable.
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val ids = cm?.cameraIdList
            val ok = !ids.isNullOrEmpty()
            if (!ok) Log.w(TAG, "CameraManager.cameraIdList is empty — treating as no camera")
            ok
        } catch (e: CameraAccessException) {
            Log.w(TAG, "CameraManager.cameraIdList threw — treating as no camera", e)
            false
        } catch (e: Exception) {
            // Some OEM stacks throw a non-CameraAccessException (NPE,
            // IllegalStateException) here. Defensive catch so a buggy HAL
            // can't propagate up to a process crash via this helper itself.
            Log.w(TAG, "CameraManager probe threw unexpected exception", e)
            false
        }
    }
}
