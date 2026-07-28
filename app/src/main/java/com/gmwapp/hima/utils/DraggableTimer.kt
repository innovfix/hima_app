package com.gmwapp.hima.utils

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * BUG #2 — the call-duration pill sat in a fixed spot and overlapped the self-view
 * preview. Rather than move it to a place that happens to be clear on one device, the
 * user places it themselves; this attaches that behaviour to the pill.
 *
 * Position is stored as a pair of ConstraintLayout biases, NOT pixels. A bias is a
 * fraction of the free space along an axis, so a position chosen on one phone lands in
 * the same visual spot on a different screen size or density, and survives rotation.
 * Pixel offsets would not. One pair is shared by all four call screens (male/female,
 * direct video and switched-to-video), so the user places the pill once.
 *
 * Owner decisions, 28-Jul: one shared position; snap to edges/corners on release;
 * long-press to start a drag.
 *
 * The long press matters for more than accident-proofing. These screens already use
 * "tap anywhere to toggle the chrome" plus a 10s idle auto-hide, so an immediate drag
 * would fight that gesture. Holding first makes the two unambiguous: a quick tap on the
 * pill is forwarded to [onTap] and behaves exactly like a tap anywhere else, and only a
 * deliberate hold moves it.
 */
object DraggableTimer {

    private const val PREFS = "Hima"
    private const val KEY_X = "call_timer_bias_x"
    private const val KEY_Y = "call_timer_bias_y"

    /** Left edge, just under the top bar — clear of the top-right self-view preview. */
    const val DEFAULT_X = 0f
    const val DEFAULT_Y = 0f

    /** Within this fraction of an edge or the centre line, snap flush to it. */
    private const val SNAP = 0.15f

    private const val LONG_PRESS_MS = 350L

    /** Keep the pill out from under the top bar and the end-call row. */
    private const val TOP_INSET_DP = 64
    private const val BOTTOM_INSET_DP = 96

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun savedX(c: Context) = prefs(c).getFloat(KEY_X, DEFAULT_X)
    private fun savedY(c: Context) = prefs(c).getFloat(KEY_Y, DEFAULT_Y)

    private fun save(c: Context, x: Float, y: Float) =
        prefs(c).edit().putFloat(KEY_X, x).putFloat(KEY_Y, y).apply()

    /**
     * @param view    the pill. Its parent must be a ConstraintLayout and it must be
     *                constrained to all four parent edges, or bias has nothing to act on.
     * @param onTap   invoked for a press that never became a drag, so the host can keep
     *                its tap-to-toggle-chrome behaviour over the pill's own bounds.
     * @param onDrag  true while a drag is in progress. Hosts use it to hold off the idle
     *                auto-hide, otherwise the pill fades out from under the finger.
     */
    @JvmStatic
    fun attach(view: View, onTap: () -> Unit = {}, onDrag: (Boolean) -> Unit = {}) {
        val parent = view.parent as? ConstraintLayout ?: return

        // Bias resolves at layout time and needs no measured size, so the stored position
        // can be applied immediately — no waiting for a layout pass.
        applyBias(view, savedX(view.context), savedY(view.context))

        val slop = ViewConfiguration.get(view.context).scaledTouchSlop
        val density = view.resources.displayMetrics.density
        val topInset = (TOP_INSET_DP * density)
        val bottomInset = (BOTTOM_INSET_DP * density)

        var downRawX = 0f
        var downRawY = 0f
        var startTx = 0f
        var startTy = 0f
        var armed = false
        var movedPastSlop = false

        val arm = Runnable {
            armed = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            view.animate().scaleX(1.06f).scaleY(1.06f).setDuration(90).start()
            onDrag(true)
        }

        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX; downRawY = e.rawY
                    startTx = v.translationX; startTy = v.translationY
                    armed = false; movedPastSlop = false
                    v.postDelayed(arm, LONG_PRESS_MS)
                    // Must consume, or no MOVE/UP arrives at all. A press that never
                    // becomes a drag is forwarded to onTap() on release instead.
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (!armed) {
                        // Moving before the hold completes means this is a swipe, not a
                        // long press — cancel arming and let it fall through as a tap.
                        if (!movedPastSlop && (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop)) {
                            movedPastSlop = true
                            v.removeCallbacks(arm)
                        }
                        return@setOnTouchListener true
                    }
                    val lp = v.layoutParams as ConstraintLayout.LayoutParams
                    // Margins shift where a bias resolves to, so the travel range is the
                    // parent minus the view AND its margins - not just parent minus view.
                    val availW = (parent.width - v.width - lp.leftMargin - lp.rightMargin).toFloat()
                    val availH = (parent.height - v.height - lp.topMargin - lp.bottomMargin).toFloat()
                    // Clamp in pixels while dragging so the pill cannot be pulled off
                    // screen or parked under the controls.
                    val minTop = lp.topMargin + topInset
                    val maxTop = (lp.topMargin + availH - bottomInset).coerceAtLeast(minTop)
                    val left = (v.left + startTx + dx)
                        .coerceIn(lp.leftMargin.toFloat(), (lp.leftMargin + availW).coerceAtLeast(lp.leftMargin.toFloat()))
                    val top = (v.top + startTy + dy).coerceIn(minTop, maxTop)
                    v.translationX = left - v.left
                    v.translationY = top - v.top
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(arm)
                    if (armed) {
                        armed = false
                        v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                        commit(v, parent, topInset, bottomInset)
                        onDrag(false)
                    } else if (!movedPastSlop && e.actionMasked == MotionEvent.ACTION_UP) {
                        onTap()
                    }
                    true
                }

                else -> false
            }
        }
    }

    /** Convert the dragged pixel offset into a snapped, clamped, persisted bias pair. */
    private fun commit(v: View, parent: ConstraintLayout, topInset: Float, bottomInset: Float) {
        val lp = v.layoutParams as? ConstraintLayout.LayoutParams ?: return
        // Same margin-aware travel range as the drag clamp above; using the naive
        // parent-minus-view here would make the pill jump on release by the margin.
        val availW = (parent.width - v.width - lp.leftMargin - lp.rightMargin).toFloat()
        val availH = (parent.height - v.height - lp.topMargin - lp.bottomMargin).toFloat()
        if (availW <= 0f || availH <= 0f) return

        var bx = ((v.left + v.translationX - lp.leftMargin) / availW).coerceIn(0f, 1f)
        var by = ((v.top + v.translationY - lp.topMargin) / availH).coerceIn(0f, 1f)

        // The usable vertical band excludes the top bar and the control row, so the
        // snap targets are the edges of that band rather than of the whole screen.
        val minY = (topInset / availH).coerceIn(0f, 1f)
        val maxY = ((availH - bottomInset) / availH).coerceIn(minY, 1f)
        by = by.coerceIn(minY, maxY)

        bx = when {
            bx < SNAP -> 0f
            bx > 1f - SNAP -> 1f
            kotlin.math.abs(bx - 0.5f) < SNAP -> 0.5f
            else -> bx
        }
        by = when {
            by < minY + SNAP -> minY
            by > maxY - SNAP -> maxY
            else -> by
        }

        v.translationX = 0f
        v.translationY = 0f
        applyBias(v, bx, by)
        save(v.context, bx, by)
    }

    private fun applyBias(v: View, x: Float, y: Float) {
        val lp = v.layoutParams as? ConstraintLayout.LayoutParams ?: return
        lp.horizontalBias = x
        lp.verticalBias = y
        v.layoutParams = lp
    }
}
