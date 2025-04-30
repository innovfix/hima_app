package com.gmwapp.hima.agora

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.GiftAdapter
import com.gmwapp.hima.agora.male.MaleAudioCallingActivity
import com.gmwapp.hima.databinding.BottomSheetGiftsLayoutBinding
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.GetRemainingTimeResponse
import com.gmwapp.hima.retrofit.responses.GiftData
import com.gmwapp.hima.viewmodels.GiftImageViewModel
import com.gmwapp.hima.viewmodels.GiftViewModel
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Response

@AndroidEntryPoint
class GiftBottomSheetFragment(var callType: String, var femaleId:Int) : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetGiftsLayoutBinding

    private lateinit var giftAdapter: GiftAdapter
    private val profileViewModel: ProfileViewModel by viewModels()

    val giftImageViewModel: GiftImageViewModel by viewModels()
    val giftViewModel: GiftViewModel by viewModels()
    var count = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetGiftsLayoutBinding.inflate(inflater, container, false)

        val recyclerView: RecyclerView = binding.rvGifts
        recyclerView.layoutManager = GridLayoutManager(context, 4) // 4 items per row


        giftAdapter = GiftAdapter(requireContext()) { giftData ->
            getRemainingTime(callType) { availableCoins ->

                if (availableCoins >= giftData.coins) {
                    //Toast.makeText(requireContext(), "✅ You can gift!", Toast.LENGTH_SHORT).show()
                    count=1
                    val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
                    val maleUserId = userData?.id
                    Log.d("selectedGiftData","${giftData.id}")
                    maleUserId?.let { it1 -> giftViewModel.sendGift(it1,femaleId,giftData.id) }
                    giftObservers()

                } else {
                    Toast.makeText(requireContext(), "You don't have enough coins to send this gift!", Toast.LENGTH_SHORT).show()
                }

            }
        }

        recyclerView.adapter = giftAdapter

        giftImageViewModel.giftResponseLiveData.observe(viewLifecycleOwner, Observer { response ->
            response?.data?.let { giftList ->
                giftAdapter.updateGiftList(giftList)
            }
        })

        giftImageViewModel.giftErrorLiveData.observe(viewLifecycleOwner, Observer { errorMessage ->
            errorMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
        })

        giftImageViewModel.fetchGiftImages() // Fetch images

        return binding.root
    }

    override fun onStart() {
        super.onStart()

    }


    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    private fun getRemainingTime(type: String, onCoinsCalculated: (Int) -> Unit) {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val maleUserId = userData?.id
        maleUserId?.let {
            profileViewModel.getRemainingTime(it, type, object :
                NetworkCallback<GetRemainingTimeResponse> {
                override fun onNoNetwork() {
                    // Handle no network error
                }

                override fun onFailure(call: Call<GetRemainingTimeResponse>, t: Throwable) {
                    // Handle API failure
                }

                override fun onResponse(
                    call: Call<GetRemainingTimeResponse>,
                    response: Response<GetRemainingTimeResponse>
                ) {
                    response.body()?.data?.let { data ->
                        val newTime = data.remaining_time
                        Log.d("newtime", "$newTime")

                        val leftCoins = calculateAvailableCoins(newTime, type)

                        // 🔥 Send the calculated coins to the callback
                        onCoinsCalculated(leftCoins)
                    }
                }

            })
        }
    }

    fun calculateAvailableCoins(remainingTime: String, callType: String): Int {
        val parts = remainingTime.split(":")
        val minutes = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val seconds = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val totalMinutes = minutes + if (seconds >= 30) 1 else 0 // if seconds ≥30, round up to next minute

        return when (callType.lowercase()) {
            "audio" -> totalMinutes * 10   // 10 coins per minute
            "video" -> totalMinutes * 60   // 60 coins per minute
            else -> 0
        }
    }

//    private fun showGiftDialog(selectedGiftData: GiftData) {
//        val dialog = Dialog(requireContext()) // 👉 use requireContext() inside Fragment
//        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
//        dialog.setContentView(R.layout.gift_send_dilog_layout)
//
//        dialog.window?.setLayout(
//            (resources.displayMetrics.widthPixels * 0.9).toInt(),  // 90% width
//            WindowManager.LayoutParams.WRAP_CONTENT
//        )
//        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
//
//        val btnNo = dialog.findViewById<Button>(R.id.btnNo)
//        val btnYes = dialog.findViewById<Button>(R.id.btnYes)
//
//        btnNo.setOnClickListener { dialog.dismiss() }
//
//        btnYes.setOnClickListener {
//            getRemainingTime(callType) { availableCoins ->
//                if (availableCoins >= selectedGiftData.coins) {
//
//                    val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
//                    val maleUserId = userData?.id
//                    Log.d("selectedGiftData","${selectedGiftData.id}")
//                    maleUserId?.let { it1 -> giftViewModel.sendGift(it1,femaleId,selectedGiftData.id) }
//                    giftObservers()
//                } else {
//                    Toast.makeText(requireContext(), "You don't have enough coins now!", Toast.LENGTH_SHORT).show()
//                }
//                dialog.dismiss()
//            }
//        }
//
//        dialog.show()
//    }

    private fun giftObservers() {

        giftViewModel.giftResponseLiveData.observe(this) { response ->
            Log.d("userAvatarLiveData", "Image URL: $response")

            if (response != null && response.success) {
                if (count==1){
                Toast.makeText(requireContext(), "Gift Sent Successfully!", Toast.LENGTH_SHORT).show()
                    count++
                    response.data?.let { (activity as? MaleAudioCallingActivity)?.sendGiftSentNotification(it.gift_icon) }
                    (activity as? MaleAudioCallingActivity)?.newRemainingTime()
                    response.data?.let { (activity as? MaleAudioCallingActivity)?.animateGift(it.gift_icon) }
                    dismiss()


                }}
        }

        giftViewModel.giftErrorLiveData.observe(this) { errorMessage ->
            Log.e("UserAvatarError", errorMessage)
        }
    }




}
