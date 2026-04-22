package com.gmwapp.hima.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.R
import com.gmwapp.hima.retrofit.responses.MatchedCreator
import de.hdodenhof.circleimageview.CircleImageView

/**
 * Renders each matched creator as a row with avatar + name + a trailing
 * status indicator that starts as a spinner ("Delivering…") and flips to a
 * green check ("✓ Message delivered") when the Activity calls
 * [markDelivered]. Used on Screen 3 of AiOnboardingActivity after the AI
 * chat completes, so the handoff to the creator list feels personal.
 */
class ProgressMatchAdapter(
    private val activity: Activity,
    private val creators: List<MatchedCreator>
) : RecyclerView.Adapter<ProgressMatchAdapter.ViewHolder>() {

    private val deliveredIndices = mutableSetOf<Int>()

    fun markDelivered(index: Int) {
        if (index < 0 || index >= creators.size) return
        if (deliveredIndices.add(index)) {
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_match_progress, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val creator = creators[position]
        holder.tvName.text = creator.name.replace(Regex("[0-9]"), "").ifBlank { creator.name }

        Glide.with(activity)
            .load(creator.image)
            .apply(RequestOptions.circleCropTransform())
            .placeholder(R.drawable.small_profile)
            .error(R.drawable.small_profile)
            .into(holder.ivAvatar)

        val isDelivered = deliveredIndices.contains(position)
        holder.spinner.visibility = if (isDelivered) View.GONE else View.VISIBLE
        holder.ivCheck.visibility = if (isDelivered) View.VISIBLE else View.GONE
        holder.tvStatus.text = if (isDelivered) "✓ Message delivered" else "Delivering…"
        holder.tvStatus.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                activity,
                if (isDelivered) R.color.green else R.color.grey_medium
            )
        )

        if (isDelivered) {
            holder.itemView.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.match_progress_pulse))
        }
    }

    override fun getItemCount(): Int = creators.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: CircleImageView = view.findViewById(R.id.iv_progress_avatar)
        val tvName: TextView = view.findViewById(R.id.tv_progress_name)
        val tvStatus: TextView = view.findViewById(R.id.tv_progress_status)
        val spinner: ProgressBar = view.findViewById(R.id.progress_spinner)
        val ivCheck: ImageView = view.findViewById(R.id.iv_progress_check)
    }
}
