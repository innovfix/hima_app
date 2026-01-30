package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.DialogFreeCoinsWelcomeBinding
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.ClaimFreeCoinsResponse
import com.gmwapp.hima.utils.setOnSingleClickListener
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FreeCoinsWelcomeDialog : DialogFragment() {

    private var _binding: DialogFreeCoinsWelcomeBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var apiManager: ApiManager

    private var coinsValue: Int = 0
    private var badgeText: String = "WELCOME GIFT"
    private var title: String = "First Minute for Free"
    private var subtitle: String = "No Payment Required"
    private var description: String = "20 coins will be added"
    private var buttonText: String = "Claim Free Minute"
    private var makePayment: Int = 0
    private var coinId: Int = 0
    private var price: Int = 0
    private var coinsClaimedListener: OnCoinsClaimedListener? = null
    private var buyCoinsListener: OnBuyCoinsListener? = null
    private var dismissListener: OnDismissListener? = null

    // Interface to notify activity when coins are claimed
    interface OnCoinsClaimedListener {
        fun onCoinsClaimed(coinsAdded: Int)
    }

    interface OnBuyCoinsListener {
        fun onBuyCoins(totalAmount: String, coinId: Int)
    }

    // Interface to notify when dialog is dismissed
    interface OnDismissListener {
        fun onDialogDismissed()
    }

    companion object {
        fun newInstance(
            coinsValue: Int,
            makePayment: Int? = null,
            coinId: Int? = null,
            price: Int? = null,
            badgeText: String? = null,
            title: String? = null,
            subtitle: String? = null,
            description: String? = null,
            buttonText: String? = null
        ): FreeCoinsWelcomeDialog {
            val fragment = FreeCoinsWelcomeDialog()
            val args = Bundle().apply {
                putInt("coinsValue", coinsValue)
                putInt("makePayment", makePayment ?: 0)
                putInt("coinId", coinId ?: 0)
                putInt("price", price ?: 0)
                putString("badgeText", badgeText)
                putString("title", title)
                putString("subtitle", subtitle)
                putString("description", description)
                putString("buttonText", buttonText)
            }
            fragment.arguments = args
            return fragment
        }
    }

    fun setOnCoinsClaimedListener(listener: OnCoinsClaimedListener) {
        this.coinsClaimedListener = listener
    }

    fun setOnBuyCoinsListener(listener: OnBuyCoinsListener) {
        this.buyCoinsListener = listener
    }

    fun setOnDismissListener(listener: OnDismissListener) {
        this.dismissListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            coinsValue = it.getInt("coinsValue", 0)
            makePayment = it.getInt("makePayment", 0)
            coinId = it.getInt("coinId", 0)
            price = it.getInt("price", 0)
            badgeText = it.getString("badgeText") ?: "WELCOME GIFT"
            title = it.getString("title") ?: "First Minute for Free"
            subtitle = it.getString("subtitle") ?: "No Payment Required"
            description = it.getString("description") ?: "$coinsValue coins will be added"
            buttonText = it.getString("buttonText") ?: "Claim Free Minute"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFreeCoinsWelcomeBinding.inflate(inflater, container, false)

        // Set dynamic text from API response
        binding.tvWelcomeBadge.text = badgeText
        binding.tvFirstMinuteFree.text = title
        binding.tvNoPayment.text = subtitle
        binding.tvCoinsWillBeAdded.text = description
        binding.btnClaimFreeMinute.text = buttonText

        // Handle claim button click
        binding.btnClaimFreeMinute.setOnSingleClickListener {
            if (makePayment == 1) {
                startPaymentFlow()
            } else {
                claimFreeCoins()
            }
        }

        return binding.root
    }

    private fun claimFreeCoins() {
        if (makePayment == 1) {
            // Safety guard: do not call claim API when payment flow is required
            startPaymentFlow()
            return
        }
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id

        if (userId == null) {
            Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable button to prevent multiple clicks
        binding.btnClaimFreeMinute.isEnabled = false
        binding.btnClaimFreeMinute.text = "Claiming..."

        apiManager.claimFreeCoins(userId, object : NetworkCallback<ClaimFreeCoinsResponse> {
            override fun onResponse(
                call: retrofit2.Call<ClaimFreeCoinsResponse>,
                response: retrofit2.Response<ClaimFreeCoinsResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    val claimResponse = response.body()!!
                    if (claimResponse.success) {
                        // Show success animation
                        showSuccessAnimation(claimResponse)
                    } else {
                        Toast.makeText(context, claimResponse.message, Toast.LENGTH_SHORT).show()
                        binding.btnClaimFreeMinute.isEnabled = true
                        binding.btnClaimFreeMinute.text = buttonText
                    }
                } else {
                    Toast.makeText(context, "Failed to claim coins", Toast.LENGTH_SHORT).show()
                    binding.btnClaimFreeMinute.isEnabled = true
                    binding.btnClaimFreeMinute.text = buttonText
                }
            }

            override fun onFailure(
                call: retrofit2.Call<ClaimFreeCoinsResponse>,
                t: Throwable
            ) {
                Log.e("FreeCoinsWelcomeDialog", "API call error: ${t.message}")
                Toast.makeText(context, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                binding.btnClaimFreeMinute.isEnabled = true
                binding.btnClaimFreeMinute.text = buttonText
            }

            override fun onNoNetwork() {
                Toast.makeText(context, "No network connection", Toast.LENGTH_SHORT).show()
                binding.btnClaimFreeMinute.isEnabled = true
                binding.btnClaimFreeMinute.text = buttonText
            }
        })
    }

    private fun startPaymentFlow() {
        if (coinId <= 0 || price <= 0) {
            Toast.makeText(context, "Payment info not available", Toast.LENGTH_SHORT).show()
            return
        }

        val twoPercentage = price.toDouble() * 0.02
        val roundedAmount = Math.round(twoPercentage)
        val totalAmount = (price + roundedAmount).toString()

        buyCoinsListener?.onBuyCoins(totalAmount, coinId)
        dismissAllowingStateLoss()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        // Allow dismissing by clicking outside or pressing back
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // Set dialog width to 90% of screen width
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        // Center the dialog
        dialog?.window?.setGravity(android.view.Gravity.CENTER)
        // Add dim background with premium feel
        dialog?.window?.setDimAmount(0.65f)
    }

    private fun showSuccessAnimation(claimResponse: ClaimFreeCoinsResponse) {
        claimResponse.data?.let { data ->
            // Hide button and other elements
            binding.btnClaimFreeMinute.visibility = View.GONE
            binding.llSubtitle.visibility = View.GONE
            binding.cvWelcomeBadge.visibility = View.GONE
            
            // Update text to show success
            binding.tvFirstMinuteFree.text = "Coins Added!"
            binding.tvCoinsWillBeAdded.text = "${data.coins_added} coins added successfully"
            
            // Update dismiss hint to be more prominent
            binding.tvDismissHint.text = "Tap outside or press back to close"
            binding.tvDismissHint.visibility = View.VISIBLE
            
            // Animate coin icon flying up
            val coinFlyAnimation = AnimationUtils.loadAnimation(context, R.anim.coin_fly_up)
            binding.ivCoinIcon.startAnimation(coinFlyAnimation)
            
            // Animate gift box with bounce
            val bounceAnimation = AnimationUtils.loadAnimation(context, R.anim.success_scale_bounce)
            binding.ivGiftBox.startAnimation(bounceAnimation)
            
            // Show success message
            Toast.makeText(
                context,
                "Success! ${data.coins_added} coins added to your wallet",
                Toast.LENGTH_SHORT
            ).show()
            
            // Notify listener to refresh coins in MainActivity
            val coinsAdded = data.coins_added
            coinsClaimedListener?.onCoinsClaimed(coinsAdded)
            
            // Don't auto-dismiss - user will dismiss by clicking outside or pressing back
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        dismissListener?.onDialogDismissed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
