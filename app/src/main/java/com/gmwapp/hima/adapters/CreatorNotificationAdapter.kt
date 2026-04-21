package com.gmwapp.hima.adapters

import android.app.Activity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FemaleNotificationPreferenceResponse
import com.gmwapp.hima.utils.MaleNotificationPreferenceKeys
import com.gmwapp.hima.utils.NotifyOnlinePrefsHelper
import retrofit2.Call
import retrofit2.Response

class CreatorNotificationAdapter(
    private val activity: Activity,
    private val apiManager: ApiManager,
    private val maleUserId: Int,
    private var items: MutableList<NotifyOnlinePrefsHelper.SubscribedCreator>,
    private val onDataChanged: () -> Unit
) : RecyclerView.Adapter<CreatorNotificationAdapter.VH>() {

    /** Creator ids that have already played the one-shot ON (bell) pulse for this session/list. */
    private val enteredCreatorIds = mutableSetOf<Int>()

    fun replaceItems(newItems: List<NotifyOnlinePrefsHelper.SubscribedCreator>) {
        val currentIds = newItems.map { it.id }.toSet()
        enteredCreatorIds.retainAll(currentIds)
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manage_creator_notification, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(private val root: android.view.View) : RecyclerView.ViewHolder(root) {
        private val iv = root.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.iv_creator_avatar)
        private val tvName = root.findViewById<android.widget.TextView>(R.id.tv_creator_name)
        private val tvSub = root.findViewById<android.widget.TextView>(R.id.tv_trigger_reason)
        private val vOnline = root.findViewById<View>(R.id.v_online_dot)
        private val ivActionCheck = root.findViewById<ImageView>(R.id.iv_action_check)
        private val ivActionBell = root.findViewById<ImageView>(R.id.iv_action_bell)
        private val sw = root.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_creator_notify)

        private var pendingSlideOutRunnable: Runnable? = null

        fun bind(row: NotifyOnlinePrefsHelper.SubscribedCreator) {
            pendingSlideOutRunnable?.let { root.removeCallbacks(it) }
            pendingSlideOutRunnable = null
            root.animate().cancel()
            ivActionCheck.animate().cancel()
            ivActionBell.animate().cancel()
            root.alpha = 1f
            root.translationX = 0f
            ivActionCheck.visibility = View.GONE
            ivActionCheck.scaleX = 0f
            ivActionCheck.scaleY = 0f
            ivActionBell.visibility = View.GONE
            ivActionBell.alpha = 0f
            ivActionBell.scaleX = 1f
            ivActionBell.scaleY = 1f

            tvName.text = row.displayName
            Glide.with(activity)
                .load(row.imageUrl)
                .apply(RequestOptions.circleCropTransform())
                .placeholder(R.drawable.small_profile)
                .error(R.drawable.small_profile)
                .into(iv)

            vOnline.visibility = if (row.isOnline) View.VISIBLE else View.GONE
            tvSub.text = when (row.triggerReason) {
                "bell" -> activity.getString(R.string.notify_trigger_bell)
                "repeat_caller" -> activity.getString(R.string.notify_trigger_repeat)
                "both" -> activity.getString(R.string.notify_trigger_both)
                else -> activity.getString(R.string.notify_bell_default_desc)
            }

            val subscribed = if (row.subscribedFromApi) {
                true
            } else {
                NotifyOnlinePrefsHelper.prefs(activity).getBoolean("notify_${row.id}", false)
            }
            val masterOn = BaseApplication.getInstance()?.getPrefs()
                ?.getBoolPref(MaleNotificationPreferenceKeys.CREATOR_ONLINE_MASTER, true) == true
            sw.isEnabled = masterOn
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = subscribed
            sw.setOnCheckedChangeListener { _, desiredOn ->
                sw.isEnabled = false
                val status = if (desiredOn) 1 else 0
                apiManager.setFemaleNotificationPreference(
                    maleUserId = maleUserId,
                    femaleUserId = row.id,
                    status = status,
                    callback = object : NetworkCallback<FemaleNotificationPreferenceResponse> {
                        override fun onResponse(
                            call: Call<FemaleNotificationPreferenceResponse>,
                            response: Response<FemaleNotificationPreferenceResponse>
                        ) {
                            val ok = response.isSuccessful && response.body()?.success == true
                            activity.runOnUiThread {
                                if (ok) {
                                    if (desiredOn) {
                                        sw.isEnabled = true
                                        NotifyOnlinePrefsHelper.setSubscribedWithMeta(
                                            activity,
                                            row.id,
                                            desiredOn,
                                            row.displayName,
                                            row.imageUrl
                                        )
                                        onDataChanged()
                                    } else {
                                        playOffConfirmAndRefresh(row)
                                    }
                                } else {
                                    sw.isEnabled = true
                                    Toast.makeText(
                                        activity,
                                        activity.getString(R.string.notify_error_toast),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    bind(row)
                                }
                            }
                        }

                        override fun onFailure(call: Call<FemaleNotificationPreferenceResponse>, t: Throwable) {
                            activity.runOnUiThread {
                                sw.isEnabled = true
                                Toast.makeText(
                                    activity,
                                    activity.getString(R.string.notify_error_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                                bind(row)
                            }
                        }

                        override fun onNoNetwork() {
                            activity.runOnUiThread {
                                sw.isEnabled = true
                                Toast.makeText(
                                    activity,
                                    activity.getString(R.string.notify_no_network_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                                bind(row)
                            }
                        }
                    }
                )
            }

            if (row.subscribedFromApi && !enteredCreatorIds.contains(row.id)) {
                enteredCreatorIds.add(row.id)
                ivActionBell.alpha = 0f
                ivActionBell.scaleX = 0.8f
                ivActionBell.scaleY = 0.8f
                ivActionBell.visibility = View.VISIBLE
                ivActionBell.animate()
                    .alpha(1f)
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(220L)
                    .withEndAction {
                        ivActionBell.animate()
                            .alpha(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setStartDelay(120L)
                            .setDuration(180L)
                            .withEndAction { ivActionBell.visibility = View.GONE }
                            .start()
                    }
                    .start()
            }
        }

        private fun playOffConfirmAndRefresh(row: NotifyOnlinePrefsHelper.SubscribedCreator) {
            NotifyOnlinePrefsHelper.setSubscribedWithMeta(
                activity,
                row.id,
                false,
                row.displayName,
                row.imageUrl
            )

            @Suppress("DEPRECATION")
            root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

            ivActionCheck.visibility = View.VISIBLE
            ivActionCheck.scaleX = 0f
            ivActionCheck.scaleY = 0f
            ivActionCheck.animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(160L)
                .setInterpolator(OvershootInterpolator(2.2f))
                .withEndAction {
                    ivActionCheck.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(80L)
                        .start()
                }
                .start()

            val slideOut = Runnable {
                val w = root.width
                val dx = if (w > 0) w * 0.2f else root.resources.displayMetrics.density * 40f
                root.animate()
                    .alpha(0f)
                    .translationX(dx)
                    .setDuration(220L)
                    .withEndAction {
                        root.alpha = 1f
                        root.translationX = 0f
                        ivActionCheck.visibility = View.GONE
                        ivActionCheck.scaleX = 0f
                        ivActionCheck.scaleY = 0f
                        pendingSlideOutRunnable = null
                        onDataChanged()
                    }
                    .start()
            }
            pendingSlideOutRunnable = slideOut
            root.postDelayed(slideOut, 300L)
        }
    }
}
