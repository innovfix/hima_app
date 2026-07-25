package com.gmwapp.hima.adapters

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.ChatActivityInHouse
import com.gmwapp.hima.retrofit.responses.MatchedCreator
import com.gmwapp.hima.utils.setOnSingleClickListener
import de.hdodenhof.circleimageview.CircleImageView

class MatchedCreatorAdapter(
    private val activity: Activity,
    private val creators: List<MatchedCreator>
) : RecyclerView.Adapter<MatchedCreatorAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_matched_creator, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val creator = creators[position]

        holder.tvName.text = com.gmwapp.hima.utils.DisplayName.clean(creator.name)
        holder.tvLanguage.text = creator.language ?: ""

        if (!creator.image.isNullOrEmpty()) {
            Glide.with(activity).load(creator.image).into(holder.ivImage)
        }

        // Show audio/video buttons based on creator status
        holder.btnAudio.visibility = if (creator.audioStatus == 1) View.VISIBLE else View.GONE
        holder.btnVideo.visibility = if (creator.videoStatus == 1) View.VISIBLE else View.GONE

        // Clicking the row opens chat with this creator
        holder.itemView.setOnSingleClickListener {
            val intent = Intent(activity, ChatActivityInHouse::class.java).apply {
                putExtra("USER_ID", creator.id)
                putExtra("USER_NAME", creator.name)
                putExtra("USER_IMAGE", creator.image ?: "")
                putExtra("AUDIO_STATUS", creator.audioStatus ?: 0)
                putExtra("VIDEO_STATUS", creator.videoStatus ?: 0)
            }
            activity.startActivity(intent)
        }
    }

    override fun getItemCount() = creators.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: CircleImageView = view.findViewById(R.id.iv_creator_image)
        val tvName: TextView = view.findViewById(R.id.tv_creator_name)
        val tvLanguage: TextView = view.findViewById(R.id.tv_creator_language)
        val btnAudio: TextView = view.findViewById(R.id.btn_creator_audio)
        val btnVideo: TextView = view.findViewById(R.id.btn_creator_video)
    }
}
