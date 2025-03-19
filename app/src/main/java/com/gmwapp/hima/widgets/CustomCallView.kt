package com.gmwapp.hima.widgets

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.bumptech.glide.Glide
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.BaseActivity
import com.gmwapp.hima.activities.RandomUserActivity
import com.gmwapp.hima.activities.WalletActivity
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.dagger.GetRemainingTimeEvent
import com.gmwapp.hima.dagger.UpdateRemainingTimeEvent
import com.gmwapp.hima.utils.GiftManager
import com.gmwapp.hima.utils.GiftViewModelProvider
import com.gmwapp.hima.utils.Helper
import com.gmwapp.hima.viewmodels.GiftViewModel
import com.gmwapp.hima.viewmodels.LoginViewModel
//import com.zegocloud.uikit.components.audiovideo.ZegoBaseAudioVideoForegroundView
//import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
//import com.zegocloud.uikit.prebuilt.call.invite.internal.CallInviteActivity
//import com.zegocloud.uikit.service.defines.ZegoUIKitUser
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class CustomCallView{

}
//class CustomCallView : ZegoBaseAudioVideoForegroundView, LifecycleObserver {
//
//
//
//    private var customCallrootView: View? = null
//    private var tvRemainingTime: TextView? = null
//    private var balanceTime: String? = null
//    private var activity: RandomUserActivity? = null
//    private var WALLET_ACTIVITY_REQUEST_CODE = 1;
//    var remainingTimeLeft = 0
//    private var giftDialog: AlertDialog? = null
//    private val handler = Handler(Looper.getMainLooper())
//    private var checkRunnable: Runnable? = null
//    var userId = 0
//    var receiverId= 0
//    constructor(context: Context, userID: String?) : super(context, userID){
//        registerLifecycleObserver()
//    }
//
//
//
//    constructor(
//        context: Context, attrs: AttributeSet?, userID: String?
//    ) : super(context, attrs, userID){
//        registerLifecycleObserver()
//    }
//
//
//
//    override fun onAttachedToWindow() {
//        super.onAttachedToWindow()
//        EventBus.getDefault().post(GetRemainingTimeEvent());
//    }
//
//    fun getDisplayContentHeight(): Int {
//        return Resources.getSystem().getDisplayMetrics().heightPixels;
//    }
//
//    override fun onForegroundViewCreated(uiKitUser: ZegoUIKitUser) {
//        // Make the window full-screen
//        // Make the window full-screen without hiding the navigation bar
//        val activity = context as? Activity
//
//
//
//        receiverId = uiKitUser.userID.toInt()
//        post{
//            setupGiftUI()
//        }
//
//
//
//        activity?.window?.apply {
//            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE  // Ensures layout doesn't change
//            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) // Ensures status bar respects layout
//            clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)  // Ensure status bar is visible
//        }
////        activity?.window?.apply {
////            decorView.systemUiVisibility = (
////                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
////                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
////                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
////                    )
////            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
////        }
////
//        val root = activity?.findViewById<View>(android.R.id.content)
//
//        if (root != null) {
//            root?.setBackgroundColor(Color.parseColor("#ff0d4a"))
//            // Set fitsSystemWindows to true to ensure layout respects insets
//            root?.fitsSystemWindows = true
//            ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
//                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//                insets
//            }
//        }
//
//        // Initialize your custom view
//        val prefs = BaseApplication.getInstance()?.getPrefs()
//        val userData = prefs?.getUserData()
//
//        if (userData != null) {
//            userId = userData.id
//        }
//
//
//        val inflate = inflate(
//            context,
//            if (userData?.gender == DConstants.MALE)
//                R.layout.widget_custom_call
//            else
//                R.layout.widget_custom_call_female,
//            this
//        )
//        var height = getDisplayContentHeight();
//        inflate.addOnLayoutChangeListener(OnLayoutChangeListener { v, left, top, right, bottom, leftWas, topWas, rightWas, bottomWas ->
//            if(v.height < height/2){
//                root?.setBackgroundColor(context.getColor(android.R.color.transparent))
//                customCallrootView?.visibility = View.INVISIBLE
//            } else{
//                root?.setBackgroundColor(Color.parseColor("#ff0d4a"))
//                customCallrootView?.visibility = View.VISIBLE
//            }
//        })
//
//        customCallrootView = inflate
//
//        tvRemainingTime = inflate.findViewById<View>(R.id.tv_remaining_time) as TextView?
//        if (userData?.gender == DConstants.MALE) {
//            var clCoins = inflate.findViewById<View>(R.id.cl_coins) as ConstraintLayout?
//            clCoins?.setOnClickListener({
//                try {
//                    val aux: Fragment = FragmentForResult(RandomUserActivity())
//                    val fm: FragmentManager? =
//                        (context as CallInviteActivity).supportFragmentManager
//                    fm?.beginTransaction()?.add(aux, "FRAGMENT_TAG")?.commit()
//                    fm?.executePendingTransactions()
//                    val intent = Intent(activity, WalletActivity::class.java)
//                    intent.putExtra(DConstants.NEED_TO_FINISH, true)
//                    ZegoUIKitPrebuiltCallService.minimizeCall()
//                    aux.startActivityForResult(intent, WALLET_ACTIVITY_REQUEST_CODE)
//                } catch (e: Exception) {
//                }
//            })
//
//            }
//            EventBus.getDefault().register(this)
//        }
//
//    private fun registerLifecycleObserver() {
//        val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner
//        lifecycleOwner?.lifecycle?.addObserver(this)
//    }
//
//    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
//    fun onResume() {
//        Log.d("CustomCallView", "onResume called")
//
//        restoreUIState()
//    }
//
//
//
//
//    private fun setupGiftUI() {
//        val giftList = GiftManager.getGiftIconsWithCoins().values.toList()
//
//        // Find UI components safely
//        val ivGiftList = listOfNotNull(
//            customCallrootView?.findViewById<ImageView?>(R.id.iv_gift1),
//            customCallrootView?.findViewById<ImageView?>(R.id.iv_gift2),
//            customCallrootView?.findViewById<ImageView?>(R.id.iv_gift3),
//            customCallrootView?.findViewById<ImageView?>(R.id.iv_gift4)
//        )
//
//        val tvCoinsList = listOfNotNull(
//            customCallrootView?.findViewById<TextView?>(R.id.tv_coinsAmount1),
//            customCallrootView?.findViewById<TextView?>(R.id.tv_coinsAmount2),
//            customCallrootView?.findViewById<TextView?>(R.id.tv_coinsAmount3),
//            customCallrootView?.findViewById<TextView?>(R.id.tv_coinsAmount4)
//        )
//
//
//        val ivCoinsImage = listOfNotNull(
//            customCallrootView?.findViewById(R.id.iv_coinImg1),
//            customCallrootView?.findViewById(R.id.iv_coinImg2),
//            customCallrootView?.findViewById(R.id.iv_coinImg3),
//            customCallrootView?.findViewById(R.id.iv_coinImg4)
//        )
//
//        // Prevent crash if views are missing
//        if (ivGiftList.isEmpty() || tvCoinsList.isEmpty()) {
//            Log.e("CustomCallView", "Gift views not found in layout!")
//            return
//        }
//
//        // Load gifts dynamically
//        for (i in ivGiftList.indices) {
//            if (i < giftList.size) {
//                val gift = giftList[i] // Get full GiftData object
//
//                // Load the gift image using Glide
//                Glide.with(context)
//                    .load(gift.gift_icon)
//                    .into(ivGiftList[i])
//
//                // Set the coin amount
//                tvCoinsList[i].text = gift.coins.toString()
//                ivCoinsImage[i].visibility = View.VISIBLE
//
//                ivGiftList[i].setOnClickListener {
//                    checkAndSendGift(gift.coins,gift.id)
//                    Log.e("UserIdValue", "user$userId")
//                    Log.e("UserIdValue", "receiver$receiverId")
//                    Log.e("UserIdValue", "coin${gift.id}")
//
//                }
//            } else {
//                // Hide extra gift slots if there are fewer gifts
//                ivGiftList[i].visibility = View.GONE
//                tvCoinsList[i].visibility = View.GONE
//                ivCoinsImage[i].visibility = View.GONE
//
//            }
//        }
//    }
//
//    private fun checkAndSendGift(giftCoin: Int, giftId:Int) {
//        val coinLeft = remainingTimeLeft / 6  // Convert remaining time (seconds) to coins
//
//        Log.d("coinleft","$coinLeft coinleft")
//        Log.d("coinleft","$giftCoin giftcoin")
//        Log.d("coinleft","$remainingTimeLeft remainingtimeleft")
//
//        if (coinLeft > giftCoin) {
//            showGiftConfirmationDialog(giftCoin,giftId)
//        } else {
//            Toast.makeText(context, "Not enough coins to send this gift!", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//
//    private fun showGiftConfirmationDialog(giftCoin: Int, giftId:Int) {
//        val dialog = AlertDialog.Builder(context)
//            .setTitle("Send Gift")
//            .setMessage("Want to send a gift worth $giftCoin coins ?")
//            .setPositiveButton("Yes") { _, _ ->
//              //  Toast.makeText(context, "Gift Sent Successfully", Toast.LENGTH_SHORT).show()
//              //  giftViewModel.sendGift(userId, receiverId, giftId)
//                GiftViewModelProvider.giftViewModel.sendGift(userId, receiverId, giftId)
//
//
//            }
//            .setNegativeButton("Cancel") { _, _ ->
//                stopChecking()
//            }
//            .setOnDismissListener {
//                stopChecking()  // Stop checking when dialog is dismissed
//            }
//            .create()
//
//        giftDialog = dialog
//        dialog.show()
//
//        // Start checking coin balance every second
//        startChecking(giftCoin)
//    }
//
//    private fun startChecking(giftCoin: Int) {
//        checkRunnable = object : Runnable {
//            override fun run() {
//                val coinLeft = remainingTimeLeft / 6  // Convert remaining time to coins
//
//                if (coinLeft < giftCoin) {
//                    giftDialog?.dismiss()  // Close the dialog
//                    Toast.makeText(context, "Not enough coins to send this gift!", Toast.LENGTH_SHORT).show()
//                } else {
//                    handler.postDelayed(this, 1000)  // Check again after 1 second
//                }
//            }
//        }
//        handler.postDelayed(checkRunnable!!, 1000)  // Start immediately
//    }
//
//    private fun stopChecking() {
//        checkRunnable?.let { handler.removeCallbacks(it) }
//    }
//
//
//
//
//
//    private fun restoreUIState() {
//        val activity = context as? Activity
//        val root = activity?.findViewById<View>(android.R.id.content)
//
//        activity?.window?.apply {
//            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE  // Ensures layout doesn't change
//            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) // Ensures status bar respects layout
//            clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)  // Ensure status bar is visible
//        }
//
//        root?.apply {
//            setBackgroundColor(Color.parseColor("#ff0d4a"))
//            fitsSystemWindows = true
//            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
//                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//                insets
//            }
//        }
//
//        customCallrootView?.visibility = View.VISIBLE
//    }
//
//
//
//        @Subscribe(threadMode = ThreadMode.MAIN)
//        fun onGetRemainingTimeEvent(event: UpdateRemainingTimeEvent?) {
//            this.balanceTime = event?.remainingTime
//        }
//
//        fun setBalanceTime(balanceTime: String?) {
//            this.balanceTime = balanceTime;
//        }
//
//        fun setContext(activity: BaseActivity) {
//            this.activity = activity as RandomUserActivity;
//        }
//
//
//        fun updateTime(seconds: Int) {
//            var balanceTimeInsecs: Int = 0
//            try {
//                if (balanceTime != null) {
//                    val split = balanceTime!!.split(":")
//                    balanceTimeInsecs += split[0].toInt() * 60 + split[1].toInt()
//                }
//            } catch (e: Exception) {
//            }
//            var remainingTime: Int = balanceTimeInsecs - seconds.toInt()
//            remainingTimeLeft = remainingTime
//            Log.d("remainingTime","$remainingTimeLeft")
//            tvRemainingTime?.visibility = View.VISIBLE
//            val hours = remainingTime / 3600
//            val minutes = (remainingTime % 3600) / 60;
//            val secs = remainingTime % 60;
//            tvRemainingTime?.text = String.format("%02d:%02d:%02d", hours, minutes, secs)
//            if (BaseApplication.getInstance()?.getRoomId() != null && remainingTime <= 0) {
//                ZegoUIKitPrebuiltCallService.endCall()
//                if (!Helper.checkNetworkConnection()) {
//                    BaseApplication.getInstance()?.setEndCallUpdatePending(true)
//                }
//                EventBus.getDefault().post(UpdateRemainingTimeEvent(balanceTime));
//
//            }
//        }
//
//        override fun onCameraStateChanged(isCameraOn: Boolean) {
//            // will be called when camera changed
//        }
//
//        override fun onMicrophoneStateChanged(isMicrophoneOn: Boolean) {
//            // will be called when microphone changed
//        }
//    }

    class FragmentForResult(private val mActivity: RandomUserActivity?) : Fragment() {
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (resultCode == Activity.RESULT_OK && requestCode == 1) {
                mActivity?.onButtonClick()
            }
            activity?.supportFragmentManager?.beginTransaction()?.remove(this)?.commit()
        }


    }
