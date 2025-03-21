package com.gmwapp.hima

import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.databinding.ActivityDemoBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.gmwapp.hima.viewmodels.UserAvatarViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DemoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDemoBinding
    private val userAvatarViewModel: UserAvatarViewModel by viewModels()

    var isClicked : Boolean = false
    var ismute : Boolean = false
    var isSpeakerOn : Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        Log.d("userdataid","${userData?.id}")
        userData?.id?.let { userAvatarViewModel.getUserAvatar(it) }

        //userAvatarViewModel.getUserAvatar(1187)
        addObservers()


//        Glide.with(this)
//            .load(userData?.image)
//            .apply(RequestOptions.circleCropTransform())
//            .into(binding.ivMaleUser)

//        Glide.with(this)
//            .load(userData?.image)
//            .apply(RequestOptions.circleCropTransform())
//            .into(binding.ivFemaleUser)

        binding.btnMenu.setOnSingleClickListener {
            if (!isClicked){
                binding.layoutButtons.visibility = View.VISIBLE
                binding.ivMaleUser.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginEnd =  14.dpToPx()
                }
                isClicked = true



            }else{
                binding.layoutButtons.visibility = View.INVISIBLE
                binding.ivMaleUser.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginEnd =  0
                }
                isClicked= false
            }
        }


        binding.main.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) { // Detect touch down event
                val screenWidth = binding.main.width
                val clickX = event.x  // Get X position relative to `main`

                if (clickX < screenWidth * 0.75) { // Clicked outside the rightmost 20%
                    isClicked = false
                    binding.layoutButtons.visibility = View.INVISIBLE
                    binding.ivMaleUser.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        marginEnd = 0
                    }
                }
            }
            false // Return false to allow other touch events
        }



        binding.btnMute.setOnSingleClickListener {
            if(!ismute){
                binding.btnMute.setImageResource(R.drawable.mute_img)
                ismute=true
            }else{
                binding.btnMute.setImageResource(R.drawable.unmute_img)
                ismute = false
            }
        }


        binding.btnSpeaker.setOnSingleClickListener {
            if(!isSpeakerOn){
                binding.btnSpeaker.setImageResource(R.drawable.speakeroff_img)
                isSpeakerOn=true
            }else{
                binding.btnSpeaker.setImageResource(R.drawable.speakeron_img)
                isSpeakerOn = false
            }
        }
    }
    fun Int.dpToPx() = (this * Resources.getSystem().displayMetrics.density).toInt()

    private fun addObservers() {
        userAvatarViewModel.userAvatarLiveData.observe(this) { response ->
            Log.d("userAvatarLiveData", "Image URL: $response")

            if (response != null && response.success) {
                val imageUrl = response.data?.image
                Log.d("UserAvatar", "Image URL: $imageUrl")

                // Load the avatar image into an ImageView using Glide or Picasso
               // Glide.with(this).load(imageUrl).into(binding.ivMaleUser)
                Glide.with(this)
                    .load(imageUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .into(binding.ivMaleUser)

                binding.tvMaleName.setText(response.data?.name)
            }
        }

        userAvatarViewModel.userAvatarErrorLiveData.observe(this) { errorMessage ->
            Log.e("UserAvatarError", errorMessage)
        }
    }

}