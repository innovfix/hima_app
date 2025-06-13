package com.gmwapp.hima.adapters

import android.app.Activity
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
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.R
import com.gmwapp.hima.callbacks.OnItemSelectionListener
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
        Glide.with(activity)
            .load(femaleUser.image)
            .apply(RequestOptions.bitmapTransform(RoundedCorners(28)))
            .into(holder.binding.ivProfile)

        Log.d("FemaleName", "${femaleUser.name} audio ${femaleUser.audio_status   } video ${femaleUser.video_status}")

        Log.d("created_at","${femaleUser.created_at}")


       var userRegisterDate = getDayDifferenceLabel(femaleUser.created_at)
        Log.d("userRegisterDate","$userRegisterDate")
        if (userRegisterDate<3){
            holder.binding.newUser.visibility= View.VISIBLE
        }else{
            holder.binding.newUser.visibility= View.GONE

        }



        val  audioStatus = femaleUser.audio_status
        val  videoStatus = femaleUser.video_status

        if (audioStatus == 1) {
            holder.binding.cvAudio.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.purple))
            holder.binding.cvAudio.setOnSingleClickListener {
                val position = holder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val clickedUser = femaleUsers[position]
                    onAudioListener.onItemSelected(clickedUser)
                }
            }


        }
        if (videoStatus == 1) {
            holder.binding.cvVideo.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.green))
            holder.binding.cvVideo.setOnSingleClickListener{
                onVideoListener.onItemSelected(femaleUser)
            }
        }

        if (audioStatus == 0 && videoStatus == 0) {
            holder.binding.cvAudio.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.inactive_call))
            holder.binding.cvVideo.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.inactive_call))
        }

        if (audioStatus == 0) {
            holder.binding.cvAudio.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.inactive_call))
        }

        if (videoStatus == 0) {
            holder.binding.cvVideo.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.inactive_call))
        }


        holder.binding.tvName.text = femaleUser.name
        holder.binding.tvLanguage.text = femaleUser.language

        val interestsAsString = femaleUser.interests.trim('[', ']').split(", ")

        val staggeredGridLayoutManager = FlexboxLayoutManager(activity).apply {
            flexWrap = FlexWrap.WRAP
            alignItems = AlignItems.FLEX_START
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.FLEX_START
        }

        val itemDecoration = FlexboxItemDecoration(activity).apply {
            setDrawable(ContextCompat.getDrawable(activity, R.drawable.bg_divider))
            setOrientation(FlexboxItemDecoration.VERTICAL)
        }
     //   holder.binding.rvInterests.addItemDecoration(itemDecoration)
        holder.binding.rvInterests.layoutManager = staggeredGridLayoutManager


        val interests = arrayListOf<Interests>()
        interestsAsString.forEach {
            interests.add(Helper.getInterestObject(activity, it))
        }


        val interestsListAdapter = InterestsFemaleListAdapter(activity, interests, false, object : OnItemSelectionListener<Interests> {
            override fun onItemSelected(interest: Interests) {
                // Handle interest item selection
            }
        })

        holder.binding.rvInterests.adapter = interestsListAdapter
        holder.binding.tvSummary.text = femaleUser.describe_yourself
    }

    override fun getItemCount(): Int {
        return femaleUsers.size
    }

    fun getDayDifferenceLabel(createdAt: String): Long {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Extract date part only (cut off time)
        val createdDateOnly = createdAt.substring(0, 10)
        val createdDate: Date = format.parse(createdDateOnly) ?: return 3

        // Get today's date (yyyy-MM-dd)
        val todayDateOnly = format.format(Date())
        val todayDate: Date = format.parse(todayDateOnly) ?: return 3

        // Difference in milliseconds
        val diffMillis = todayDate.time - createdDate.time
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

        Log.d("created_at_label", "Date: $createdDateOnly, Diff: $diffDays ")
        return diffDays

    }

    internal class ItemHolder(val binding: AdapterFemaleUserBinding) : RecyclerView.ViewHolder(binding.root)
}
