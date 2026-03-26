package com.gmwapp.hima.adapters

import android.app.Activity
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.UserProfileDetailActivity
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.AdapterFemaleUserBinding
import com.gmwapp.hima.retrofit.responses.FemaleUsersResponse
import com.gmwapp.hima.retrofit.responses.FemaleUsersResponseData
import com.gmwapp.hima.retrofit.responses.Interests
import com.gmwapp.hima.utils.Helper
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxItemDecoration
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent

class FemaleUserAdapter(
    val activity: Activity,
    private var femaleUsers: List<FemaleUsersResponseData>,
    val onAudioListener: OnItemSelectionListener<FemaleUsersResponseData>,
    val onVideoListener: OnItemSelectionListener<FemaleUsersResponseData>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val itemHolder = ItemHolder(
            AdapterFemaleUserBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
        return itemHolder
    }

    override fun onBindViewHolder(holderParent: RecyclerView.ViewHolder, position: Int) {
        val holder: ItemHolder = holderParent as ItemHolder
        val femaleUser: FemaleUsersResponseData = femaleUsers[position]
        
        Glide.with(activity).load(femaleUser.image).into(holder.binding.ivProfile)

        Log.d("FemaleName", "${femaleUser.name} audio ${femaleUser.audio_status} video ${femaleUser.video_status}")

        val userRegisterDate = if (!femaleUser.verified_datetime.isNullOrBlank()) {
            getDayDifferenceLabel(femaleUser.verified_datetime)
        } else {
            null
        }

        if (userRegisterDate != null) {
            if (userRegisterDate < 3) {
                holder.binding.newUser.visibility = View.VISIBLE
            } else {
                holder.binding.newUser.visibility = View.GONE
            }
        } else {
            holder.binding.newUser.visibility = View.GONE
        }

        // Show star badge only for users marked as star by API
        holder.binding.starBadge.visibility =
            if (femaleUser.is_star == 1 || femaleUser.star == 1) View.VISIBLE else View.GONE

        val audioStatus = femaleUser.audio_status
        val videoStatus = femaleUser.video_status

        // Show online indicator if either audio or video is enabled
        val isOnline = audioStatus == 1 || videoStatus == 1
        holder.binding.onlineIndicator.visibility = if (isOnline) View.VISIBLE else View.GONE

        // Configure Audio Call Button with Gradient
        val audioButton = holder.binding.cvAudio.getChildAt(0) as? android.widget.LinearLayout
        if (audioStatus == 1) {
            audioButton?.setBackgroundResource(R.drawable.button_audio_gradient)
            holder.binding.ivAudio.setColorFilter(ContextCompat.getColor(activity, R.color.white))
            holder.binding.tvIvAudio.setTextColor(ContextCompat.getColor(activity, R.color.white))
            holder.binding.cvAudio.isClickable = true
            holder.binding.cvAudio.alpha = 1.0f
            holder.binding.cvAudio.setOnSingleClickListener {
                val position = holder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val clickedUser = femaleUsers[position]
                    onAudioListener.onItemSelected(clickedUser)
                }
            }
        } else {
            audioButton?.setBackgroundResource(R.drawable.button_inactive_premium)
            holder.binding.ivAudio.setColorFilter(ContextCompat.getColor(activity, R.color.grey_medium))
            holder.binding.tvIvAudio.setTextColor(ContextCompat.getColor(activity, R.color.grey_medium))
            holder.binding.cvAudio.isClickable = false
            holder.binding.cvAudio.alpha = 0.7f
        }

        // Configure Video Call Button with Gradient
        val videoButton = holder.binding.cvVideo.getChildAt(0) as? android.widget.LinearLayout
        if (videoStatus == 1) {
            videoButton?.setBackgroundResource(R.drawable.button_video_gradient)
            holder.binding.ivVideo.setColorFilter(ContextCompat.getColor(activity, R.color.white))
            holder.binding.tvVideo.setTextColor(ContextCompat.getColor(activity, R.color.white))
            holder.binding.cvVideo.isClickable = true
            holder.binding.cvVideo.alpha = 1.0f
            holder.binding.cvVideo.setOnSingleClickListener {
                onVideoListener.onItemSelected(femaleUser)
            }
        } else {
            videoButton?.setBackgroundResource(R.drawable.button_inactive_premium)
            holder.binding.ivVideo.setColorFilter(ContextCompat.getColor(activity, R.color.grey_medium))
            holder.binding.tvVideo.setTextColor(ContextCompat.getColor(activity, R.color.grey_medium))
            holder.binding.cvVideo.isClickable = false
            holder.binding.cvVideo.alpha = 0.7f
        }

        // Set per-user audio/video rates
        holder.binding.tvIvAudio.text = "${femaleUser.coin_per_min_audio ?: 10}/min"
        holder.binding.tvVideo.text = "${femaleUser.coin_per_min_video ?: 60}/min"

        // Remove numbers from name - show only alphabets
        val nameWithoutNumbers = femaleUser.name.replace(Regex("[0-9]"), "")
        holder.binding.tvName.text = nameWithoutNumbers
        holder.binding.tvLanguage.text = femaleUser.language

        val interestsAsString = femaleUser.interests.trim('[', ']').split(", ")

        val staggeredGridLayoutManager = FlexboxLayoutManager(activity).apply {
            flexWrap = FlexWrap.WRAP
            alignItems = AlignItems.FLEX_START
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.FLEX_START
        }

        holder.binding.rvInterests.layoutManager = staggeredGridLayoutManager

        // Show only first interest
        val interests = arrayListOf<Interests>()
        if (interestsAsString.isNotEmpty() && interestsAsString[0].isNotBlank()) {
            interests.add(Helper.getInterestObject(activity, interestsAsString[0]))
        }

        val interestsListAdapter = InterestsFemaleListAdapter(activity, interests, false, object : OnItemSelectionListener<Interests> {
            override fun onItemSelected(interest: Interests) {
                // Handle interest item selection
            }
        })

        holder.binding.rvInterests.adapter = interestsListAdapter
        holder.binding.tvSummary.text = femaleUser.describe_yourself
        
        // Add click listener on the card to open profile detail
        holder.binding.cardProfile.setOnSingleClickListener {
            val intent = Intent(activity, UserProfileDetailActivity::class.java).apply {
                putExtra(DConstants.USER_ID, femaleUser.id)
                putExtra("USER_NAME", femaleUser.name)
                putExtra("USER_IMAGE", femaleUser.image)
                putExtra("USER_LANGUAGE", femaleUser.language)
                putExtra("USER_INTERESTS", femaleUser.interests)
                putExtra("USER_ABOUT", femaleUser.describe_yourself)
                putExtra("USER_AGE", 0) // Age not available in FemaleUsersResponseData
                putExtra("AUDIO_STATUS", femaleUser.audio_status)
                putExtra("VIDEO_STATUS", femaleUser.video_status)
            }
            activity.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return femaleUsers.size
    }

    fun getDayDifferenceLabel(createdAt: String): Long {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        Log.d("Created_Date","$createdAt")
        val createdDateOnly = createdAt.substring(0, 10)
        val createdDate: Date = format.parse(createdDateOnly) ?: return 3

        val todayDateOnly = format.format(Date())
        val todayDate: Date = format.parse(todayDateOnly) ?: return 3

        val diffMillis = todayDate.time - createdDate.time
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

        Log.d("created_at_label", "Date: $createdDateOnly, Diff: $diffDays ")
        return if (diffDays >= 0) diffDays else 10
    }

    internal class ItemHolder(val binding: AdapterFemaleUserBinding) : RecyclerView.ViewHolder(binding.root)
}
