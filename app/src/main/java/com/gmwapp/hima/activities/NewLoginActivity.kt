package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.OnboardingPagerAdapter
import com.google.android.material.tabs.TabLayoutMediator
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.CountDownTimer
import android.text.Editable
import android.text.InputFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.gmwapp.hima.AppSignatureHashHelper
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityNewLoginBinding
import com.gmwapp.hima.dialogs.BottomSheetCountry
import com.gmwapp.hima.retrofit.responses.Country
import com.gmwapp.hima.utils.CallModerationConsent
import com.gmwapp.hima.utils.DPreferences
import com.gmwapp.hima.viewmodels.FcmTokenViewModel
import com.gmwapp.hima.viewmodels.LoginViewModel
import com.gmwapp.hima.viewmodels.ReferralCodeViewModel
import com.gmwapp.hima.socket.SocketManager
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.material.snackbar.Snackbar
import com.onesignal.OneSignal
import com.truecaller.android.sdk.common.TrueException
import com.truecaller.android.sdk.common.VerificationCallback
import com.truecaller.android.sdk.common.VerificationDataBundle
import com.truecaller.android.sdk.oAuth.CodeVerifierUtil
import com.truecaller.android.sdk.oAuth.TcOAuthCallback
import com.truecaller.android.sdk.oAuth.TcOAuthData
import com.truecaller.android.sdk.oAuth.TcOAuthError
import com.truecaller.android.sdk.oAuth.TcSdk
import com.truecaller.android.sdk.oAuth.TcSdkOptions
import com.zoho.salesiqembed.ZohoSalesIQ
//import com.zego.ve.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.random.Random


import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Arrays

@AndroidEntryPoint
class NewLoginActivity : BaseActivity(), OnItemSelectionListener<Country> {

    private lateinit var binding: ActivityNewLoginBinding // Ensure you have view binding enabled
    private val loginViewModel: LoginViewModel by viewModels()
    private val referralCodeViewModel : ReferralCodeViewModel by viewModels()
    private val fcmTokenViewModel: FcmTokenViewModel by viewModels()

    @javax.inject.Inject
    lateinit var activeStatusReporter: com.gmwapp.hima.utils.ActiveStatusReporter

    private var otp: Int? = null
    private var mobile: String? = null
    private var truecallerCodeVerifier: String? = "0"
    private var timer: CountDownTimer?=null
    private var sendOtpEnabledTint: ColorStateList? = null
    private var verifyOtpEnabledTint: ColorStateList? = null
    private var isVerifyingOtp = false

    // Colors, images, titles, subtitles for onboarding
    private val colors = listOf(R.color.purple_400, R.color.brown, R.color.green)
    private val images = listOf(R.drawable.im_onbording1, R.drawable.im_onbording2, R.drawable.im_onbording3)
    private val titles = listOf("Earphones on!", "Voice call & Video call", "100% safe and secure")
    private val subtitles = listOf("No real pics, Only Avatar", "Find best friends", "Zero fake profiles")


//    private val requiredPermissions = arrayOf(
//        android.Manifest.permission.READ_PHONE_STATE,
//        android.Manifest.permission.READ_CALL_LOG,
//        android.Manifest.permission.ANSWER_PHONE_CALLS
//    )
//
//    private fun checkAndRequestPermissions() {
//        val missing = requiredPermissions.filter {
//            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
//        }
//
//        if (missing.isNotEmpty()) {
//            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
//        }
//    }


    // Define the callback that handles login result
    private val tcOAuthCallback = object : TcOAuthCallback {
        override fun onSuccess(tcOAuthData: TcOAuthData) {
            // Handle successful login
            val code = tcOAuthData.authorizationCode
            val state = tcOAuthData.state
            val scopes = tcOAuthData.scopesGranted
            // showAppToast("Success! Code: $code", Toast.LENGTH_LONG)
            Log.d("truecallerCodeVerifier","$code ")
            Log.d("truecallerCodeVerifier","$truecallerCodeVerifier ")
            truecallerCodeVerifier?.let { loginViewModel.login("0",code, it) }
            initOtpUI("",0,0)

            // You should send 'code' to your backend for token exchange
        }

        override fun onVerificationRequired(tcOAuthError: TcOAuthError?) {

            showPhoneInputDialogForTruecallerFallback()
        }


        override fun onFailure(tcOAuthError: TcOAuthError) {
            val msg = tcOAuthError.errorMessage.orEmpty()
            Log.e("Truecaller", "OAuth onFailure: $msg")
            if (msg.contains("fingerprint", ignoreCase = true)) {
                logSigningCertSha256ForTruecallerConsole()
                showAppToast(R.string.truecaller_fingerprint_fallback, Toast.LENGTH_LONG)
            } else {
                showAppToast(getString(R.string.truecaller_generic_error, msg.ifEmpty { getString(R.string.please_try_again_later) }), Toast.LENGTH_LONG)
            }
            promptManualPhoneLoginAfterTruecallerFailure()
        }
    }

    val verificationCallback = object : VerificationCallback {
        override fun onRequestSuccess(callbackType: Int, bundle: VerificationDataBundle?) {
            when (callbackType) {

//                VerificationCallback.TYPE_MISSED_CALL_INITIATED -> {
//                    val ttl = bundle?.getString(VerificationDataBundle.KEY_TTL)
//                    val nonce = bundle?.getString(VerificationDataBundle.KEY_REQUEST_NONCE)
//                    Log.d("verificationCallback", "Missed call initiated: TTL=$ttl, Nonce=$nonce")
//                }
//
//                VerificationCallback.TYPE_MISSED_CALL_RECEIVED -> {
//                    Log.d("verificationCallback", "Missed call received, now verifying")
//
//                    val profile = TrueProfile.Builder("Rishabh", "Kumar").build()
//                    TcSdk.getInstance().verifyMissedCall(profile, this) // 'this' = VerificationCallback
//                }

                VerificationCallback.TYPE_OTP_INITIATED -> {
                    val ttl = bundle?.getString(VerificationDataBundle.KEY_TTL)
                    val nonce = bundle?.getString(VerificationDataBundle.KEY_REQUEST_NONCE)
                    Log.d("verificationCallback", "OTP initiated: TTL=$ttl, Nonce=$nonce")
                }

                VerificationCallback.TYPE_OTP_RECEIVED -> {
                    val otp = bundle?.getString(VerificationDataBundle.KEY_OTP)
                    Log.d("verificationCallback", "OTP auto-received: $otp")
                    // Auto-fill to your EditText
                    binding.pvOtp.setText(otp)
                }

                VerificationCallback.TYPE_VERIFICATION_COMPLETE -> {
                    val token = bundle?.getString(VerificationDataBundle.KEY_ACCESS_TOKEN)
                    Log.d("verificationCallback", "Verification complete, token: $token")

                    // TODO: Send this access token to your server for validation
                    showAppToast("Verified!", Toast.LENGTH_SHORT)
                }

                VerificationCallback.TYPE_PROFILE_VERIFIED_BEFORE -> {
                    val token = bundle?.profile?.accessToken
                    Log.d("verificationCallback", "Already verified before, token: $token")

                    // TODO: Use token if needed
                    showAppToast("Already Verified", Toast.LENGTH_SHORT)
                }
            }
        }

        override fun onRequestFailure(callbackType: Int, e: TrueException) {
            Log.e("Truecaller", "Verification failed: ${e.exceptionMessage}")
            showAppToast("Verification failed: ${e.exceptionMessage}", Toast.LENGTH_SHORT)
        }
    }



    private val smsBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent?.action) {
                val extras = intent.extras
                val status = extras?.get(SmsRetriever.EXTRA_STATUS) as Status
                when (status.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        val message = extras.get(SmsRetriever.EXTRA_SMS_MESSAGE) as String
                        Log.d("OTP", "Message received: $message")

                        // Extract 6-digit OTP
                        val otpRegex = Regex("\\d{6}")
                        val getOtp = otpRegex.find(message)?.value
                        getOtp?.let {
                            binding.pvOtp.setText(it)
                            Log.d("OtpExtract","$it")
                            if (it.toIntOrNull() == otp) { // your default OTP
                                isVerifyingOtp = true
                                binding.pbVerifyOtpLoader.visibility = View.VISIBLE
                                binding.btnVerifyOtp.text = ""
                                updateVerifyOtpButtonState()
                                login(mobile ?: "")
                            }else{
                                Log.d("OtpExtract","null")

                            }
                        }
                    }
                    CommonStatusCodes.TIMEOUT -> {
                        Log.e("OTP", "SMS Retriever timed out (5 minutes)")
                    }
                }
            }
        }
    }

    private fun startSmsRetriever() {
        val client = SmsRetriever.getClient(this)
        val task = client.startSmsRetriever()
        task.addOnSuccessListener { Log.d("OTP", "SMS Retriever started") }
        task.addOnFailureListener { Log.e("OTP", "Error starting SMS retriever", it) }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(smsBroadcastReceiver, filter)
        }
        startSmsRetriever()
    }


    override fun onStop() {
        super.onStop()
        unregisterReceiver(smsBroadcastReceiver)
    }

//    @Suppress("DEPRECATION")
//    private fun getAppSignatures(): List<String> {
//        val appCodes: MutableList<String> = ArrayList()
//        try {
//            val packageName = packageName
//            val packageManager = packageManager
//            val packageInfo = packageManager.getPackageInfo(
//                packageName,
//                PackageManager.GET_SIGNING_CERTIFICATES // safe on 28+, ignored on lower
//            )
//
//            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
//                packageInfo.signingInfo?.apkContentsSigners
//            } else {
//                packageInfo.signatures
//            }
//
//            if (signatures != null) {
//                for (signature in signatures) {
//                    val hash = hash(packageName, signature.toCharsString())
//                    if (hash != null) {
//                        appCodes.add(hash)
//                        Log.d("AppHash", "Hash: $hash")
//                    }
//                }
//            }
//        } catch (e: Exception) {
//            Log.e("AppHash", "Error getting signatures", e)
//        }
//        return appCodes
//    }
//
//    private fun hash(packageName: String, signature: String): String? {
//        val appInfo = "$packageName $signature"
//        return try {
//            val messageDigest = MessageDigest.getInstance("SHA-256")
//            messageDigest.update(appInfo.toByteArray(Charsets.UTF_8))
//            val hashSignature = messageDigest.digest()
//            val truncated = Arrays.copyOfRange(hashSignature, 0, 9) // first 9 bytes
//            Base64.encodeToString(truncated, Base64.NO_PADDING or Base64.NO_WRAP).substring(0, 11)
//        } catch (e: NoSuchAlgorithmException) {
//            Log.e("AppHash", "NoSuchAlgorithm", e)
//            null
//        }
//    }








    // QA bug #4 — tracks soft-keyboard visibility so the header compresses / restores
    // exactly once per transition (guards the global-layout listener against loops).
    private var keyboardOpen = false

    // Pristine (keyboard-closed) constraints of the root ConstraintLayout, captured
    // once so the orb re-anchoring can be reverted exactly on keyboard close.
    private var baseConstraints: ConstraintSet? = null

    // While the keyboard is up the logo cluster shrinks; the floating orbs + sparkles
    // are re-anchored INTO that short band (instead of the full page) so they wrap the
    // small logo and never clip. Values = vertical bias within the logo band; the
    // horizontal bias set in XML is preserved, so each stays on its own side.
    private val orbCompactBias: Map<Int, Float> = linkedMapOf(
        R.id.orb_chat    to 0.10f,   // top-left
        R.id.orb_star    to 0.40f,   // mid-left
        R.id.orb_phone   to 0.72f,   // bottom-left
        R.id.orb_video   to 0.10f,   // top-right
        R.id.orb_connect to 0.40f,   // mid-right
        R.id.orb_heart   to 0.72f,   // bottom-right
        R.id.spark1      to 0.04f,
        R.id.spark2      to 0.90f,
        R.id.spark3      to 0.06f,
        R.id.spark4      to 0.88f,
        R.id.spark5      to 0.94f
    )

    /**
     * When the soft keyboard opens, compress the decorative logo cluster (hide the
     * tagline + Chat/Video/Voice/Connect row, shrink the logo) so the bottom-pinned
     * form fits inside the shrunken viewport, then scroll the currently-focused field
     * (mobile number or OTP boxes) fully into view. When it closes, restore the
     * original cluster so the keyboard-closed screen looks exactly as before.
     */
    private fun setupKeyboardCompaction() {
        val density = resources.displayMetrics.density
        fun px(v: Int) = (v * density).toInt()
        val logoFull = px(94);  val logoSmall = px(46)
        val padTopFull = px(40); val padTopSmall = px(10)
        val padBotFull = px(16); val padBotSmall = px(6)
        // The corner radius (27dp) and inner padding (24dp) are tuned for the 94dp
        // logo. If they stay fixed while the card shrinks to 46dp, the 27dp corners
        // round it into a full circle and the 24dp padding swallows the whole logo —
        // leaving just a pink circle (owner-reported). Scale both with the card so it
        // stays a rounded square with the Hima mark visible at both sizes.
        val cornerFull = px(27).toFloat(); val cornerSmall = px(13).toFloat()
        val innerPadFull = px(24); val innerPadSmall = px(11)

        val cl = binding.rootLayout

        fun applyCompact(compact: Boolean) {
            // The big labelled Chat/Video/Voice/Connect row + tagline have no room
            // above the keypad, so they drop away. The small floating orbs STAY and
            // re-anchor to frame the shrunk logo (see orbCompactBias).
            binding.tvTagline.visibility  = if (compact) View.GONE else View.VISIBLE
            binding.llFeatureRow.visibility = if (compact) View.GONE else View.VISIBLE
            binding.logoContainer.layoutParams = binding.logoContainer.layoutParams.apply {
                width  = if (compact) logoSmall else logoFull
                height = if (compact) logoSmall else logoFull
            }
            binding.logoContainer.radius = if (compact) cornerSmall else cornerFull
            val innerPad = if (compact) innerPadSmall else innerPadFull
            binding.logoInner.setPadding(innerPad, innerPad, innerPad, innerPad)
            binding.llLogoSection.setPadding(
                binding.llLogoSection.paddingLeft,
                if (compact) padTopSmall else padTopFull,
                binding.llLogoSection.paddingRight,
                if (compact) padBotSmall else padBotFull
            )

            // Capture the pristine orb constraints once (they're still XML-original at
            // the first compaction because we haven't touched them yet).
            if (baseConstraints == null) baseConstraints = ConstraintSet().apply { clone(cl) }

            // Clone the LIVE state so app-managed visibility (e.g. the OTP back button,
            // login vs OTP section) is preserved — we only rewrite the orb anchors.
            val set = ConstraintSet().apply { clone(cl) }
            if (compact) {
                orbCompactBias.forEach { (id, bias) ->
                    set.connect(id, ConstraintSet.TOP, R.id.ll_logo_section, ConstraintSet.TOP)
                    set.connect(id, ConstraintSet.BOTTOM, R.id.ll_logo_section, ConstraintSet.BOTTOM)
                    set.setVerticalBias(id, bias)
                }
            } else {
                baseConstraints?.let { base ->
                    orbCompactBias.keys.forEach { id ->
                        set.connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                        set.connect(id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                        set.setVerticalBias(id, base.getConstraint(id).layout.verticalBias)
                    }
                }
            }
            set.applyTo(cl)
        }

        val root = binding.root
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            root.getWindowVisibleDisplayFrame(rect)
            val screenH = root.rootView.height
            val keypad = screenH - rect.bottom
            val open = keypad > screenH * 0.15   // >15% of screen ⇒ keyboard, not just nav/status bars
            if (open != keyboardOpen) {
                keyboardOpen = open
                applyCompact(open)
                // No forced scroll: once the tagline + feature row collapse and the orbs
                // re-anchor, the compact hero (~110dp) + form fit above the keypad, so the
                // logo stays pinned and visible. (The old smoothScrollTo(field.bottom)
                // pushed the shrunk logo off the top edge — the reported clipping.)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Marketing funnel — Phone Number Screen (the login screen is the phone entry).
        com.gmwapp.hima.utils.HimaAnalytics.logPhoneNumberScreen(this)
        // Light login background: match system bars to the page and use dark icons.
        window.statusBarColor = getColor(R.color.grey_extra_light)
        window.navigationBarColor = getColor(R.color.white)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        // Add animated background (same as splash screen)
        startBackgroundAnimations()

        val helper = AppSignatureHashHelper(this)
        val hashList = helper.appSignatures

        for (hash in hashList) {
            Log.d("AppHash", "Your app hash: $hash")
        }


        ZohoSalesIQ.showLauncher(false)

        // setupOnboarding() // Disabled for clean UI
        initUI()
        // QA bug #4 — lift the focused mobile/OTP field above the soft keyboard.
        // The screen is one NestedScrollView with fillViewport + a bottom-pinned
        // form under a tall logo cluster, so with the keyboard up the content stays
        // taller than the shrunken viewport and the field lands under the keyboard
        // with no scroll-to-focus. On keyboard-open we compress the logo cluster and
        // scroll the focused field into view; keyboard-closed layout is untouched.
        setupKeyboardCompaction()
        observeReferralCodeResponse()

        // Notification permission is intentionally NOT requested on the login screen.
        // Asking before the user has entered anything (or seen any value) hurts grant
        // rates; the OS prompt now first appears on Home (MainActivity.onCreate,
        // throttled once/24h) after login. OneSignal opt-in is unaffected — it still
        // happens on OTP success below and is re-asserted in MainActivity.

        val prefs = getSharedPreferences("my_app_prefs", Context.MODE_PRIVATE)

        prefs.edit().remove("notification_user_id").apply()

        binding.loginSection.visibility  = View.VISIBLE
        binding.otpSection.visibility  = View.GONE
        binding.cvOtpBack.visibility = View.GONE

        binding.cvOtpBack.setOnClickListener {
            binding.otpSection.visibility = View.GONE
            binding.loginSection.visibility = View.VISIBLE
            binding.cvOtpBack.visibility = View.GONE
            timer?.cancel()
        }

        val tcSdkOptions = TcSdkOptions.Builder(this, tcOAuthCallback)
            .sdkOptions(TcSdkOptions.OPTION_VERIFY_ALL_USERS)
            .build()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                TcSdk.init(tcSdkOptions)
            }

            withContext(Dispatchers.Main) {
                if (TcSdk.getInstance().isOAuthFlowUsable) {
                    // ⚠️ Set these before login
                    val stateRequested = BigInteger(130, SecureRandom()).toString(32)
                    val codeVerifier = CodeVerifierUtil.generateRandomCodeVerifier()
                    val codeChallenge = CodeVerifierUtil.getCodeChallenge(codeVerifier)

                    truecallerCodeVerifier = codeVerifier

                    Log.d("authorizationCode","$codeVerifier")

                    TcSdk.getInstance().setOAuthState(stateRequested)
                    codeChallenge?.let { TcSdk.getInstance().setCodeChallenge(it) }
                    TcSdk.getInstance().setOAuthScopes(arrayOf("openid", "phone",))

                    TcSdk.getInstance().getAuthorizationCode(this@NewLoginActivity)
                } else {
                    showAppToast("Truecaller not usable", Toast.LENGTH_SHORT)
                }
            }
        }





    }



    private fun showPhoneInputDialogForTruecallerFallback() {
        promptManualPhoneLoginAfterTruecallerFailure()
    }

    /**
     * Truecaller OAuth failed (e.g. invalid fingerprint). Same signing SHA-256 must be added in
     * Truecaller developer console for this package + Client ID — cannot be bypassed in code.
     */
    private fun logSigningCertSha256ForTruecallerConsole() {
        try {
            val pm = packageManager
            val pkg = packageName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val signers = info.signingInfo?.apkContentsSigners ?: return
                for (sig in signers) {
                    val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                    val hex = digest.joinToString(":") { b -> "%02X".format(b) }
                    Log.e(
                        "TruecallerDev",
                        "Add this SHA-256 in Truecaller console (package=$pkg, debug=${BuildConfig.DEBUG}): $hex"
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val signatures = info.signatures ?: return
                for (sig in signatures) {
                    val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                    val hex = digest.joinToString(":") { b -> "%02X".format(b) }
                    Log.e(
                        "TruecallerDev",
                        "Add this SHA-256 in Truecaller console (package=$pkg): $hex"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("TruecallerDev", "Could not read signing certificate: ${e.message}")
        }
    }

    private fun promptManualPhoneLoginAfterTruecallerFailure() {
        binding.loginSection.visibility = View.VISIBLE
        binding.otpSection.visibility = View.GONE
        binding.cvOtpBack.visibility = View.GONE
        binding.etMobileNumber.post {
            binding.etMobileNumber.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etMobileNumber, InputMethodManager.SHOW_IMPLICIT)
        }
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == TcSdk.SHARE_PROFILE_REQUEST_CODE) {
            // v1106 (2026-05-29) — TcSdk.init() lives in a network-callback path
            // (line ~391, inside the captcha-init listener). On some devices that
            // callback hasn't run yet when the user returns from Truecaller's
            // consent screen, so TcSdk.getInstance() throws "Please call init()
            // on TcSdk first". Defensive guard.
            try {
                TcSdk.getInstance().onActivityResultObtained(this, requestCode, resultCode, data)
            } catch (e: RuntimeException) {
                Log.w("NewLogin", "TcSdk.onActivityResultObtained failed (SDK not initialized): ${e.message}")
            }
        }
    }



    private fun setupOnboarding() {
        // Set up onboarding ViewPager and TabLayout
        val pages = listOf(R.layout.onboarding_page1, R.layout.onboarding_page1, R.layout.onboarding_page1)
        val adapter = OnboardingPagerAdapter(pages, images, titles, subtitles)
        binding.viewPagerOnboarding.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPagerOnboarding) { tab, position ->
            val customView = LayoutInflater.from(this).inflate(R.layout.custom_tab_indicator, null)
            val indicator = customView.findViewById<ImageView>(R.id.indicator)
            indicator.setImageResource(if (position == 0) R.drawable.indicator_selected else R.drawable.indicator_unselected)
            tab.customView = customView
        }.attach()

        binding.viewPagerOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.rootLayout.setBackgroundColor(resources.getColor(colors[position], null))
                for (i in 0 until binding.tabLayout.tabCount) {
                    val icon = binding.tabLayout.getTabAt(i)?.customView?.findViewById<ImageView>(R.id.indicator)
                    icon?.setImageResource(if (i == position) R.drawable.indicator_selected else R.drawable.indicator_unselected)
                }
            }
        })

        // Automatically move the ViewPager every 2 seconds
        lifecycleScope.launch {
            while (true) {
                delay(2000) // Wait for 2 seconds
                val currentItem = binding.viewPagerOnboarding.currentItem
                val nextItem = (currentItem + 1) % adapter.itemCount
                binding.viewPagerOnboarding.setCurrentItem(nextItem, true) // Smooth transition
            }
        }
    }




    private fun initUI() {

        DPreferences(this).setReferralCode("") // Save empty referral code initially
        val savedReferCode = DPreferences(this).getReferralCode()
        Log.d("savedReferCode","$savedReferCode")
        checkReferal()
        sendOtpEnabledTint = binding.btnSendOtp.backgroundTintList

        binding.btnSendOtp.setOnClickListener {
            closeKeyboard()

            val mobile = binding.etMobileNumber.text.toString()
            // B-v1110 #9 — tvCountryCode can read empty/"null" at startup; toInt()
            // threw NumberFormatException. Default to 91 (India) when unparseable.
            val countryCode = binding.tvCountryCode.text.toString().trim().toIntOrNull() ?: 91
            val mobileRegex = Regex("^[6-9]\\d{9}$")

            when {
                TextUtils.isEmpty(mobile) -> {
                    showSnackbar("Please enter your mobile number")
                }
                !mobile.matches(mobileRegex) -> {
                    binding.tvOtpText.text = getString(R.string.invalid_phone_number_text)
                    binding.tvOtpText.setTextColor(getColor(R.color.text_light_grey))
                    showSnackbar("Enter a valid 10-digit mobile number")
                }
                else -> {
                    binding.btnSendOtp.isEnabled = false
                    val r = Random(System.currentTimeMillis())
                    otp = r.nextInt(100000, 999999)
                    // Marketing funnel — OTP Send.
                    com.gmwapp.hima.utils.HimaAnalytics.logOtpSend(this, mobile)
                    sendOTP(mobile, countryCode)
                }
            }
        }

        // Country selection removed - India flag is now fixed
//        binding.etMobileNumber.setOnTouchListener { v, _ ->
//            binding.cvLogin.setBackgroundResource(R.drawable.card_view_border_active)
//            false
//        }
        // Filter to allow only digits (no special characters) and max 10 digits
        binding.etMobileNumber.filters = arrayOf(
            InputFilter.LengthFilter(10), // Max 10 digits
            InputFilter { source, start, end, dest, dstart, dend ->
                // Only allow digits
                if (source.toString().matches(Regex("\\d*"))) {
                    null // Accept the input
                } else {
                    "" // Reject special characters
                }
            }
        )
        binding.etMobileNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                window.statusBarColor = resources.getColor(R.color.grey_extra_light)
                updateSendOtpButtonState()
            }

            override fun afterTextChanged(s: Editable) {
            }
        })
        loginViewModel.sendOTPResponseLiveData.observe(this, Observer {
            binding.pbSendOtpLoader.visibility = View.GONE
            binding.btnSendOtp.setText(getString(R.string.send_otp))
            updateSendOtpButtonState()
            
            // ✅ Add null check before accessing properties
            if (it == null) {
                binding.tvOtpText.text = getString(R.string.please_try_again_later)
                binding.tvOtpText.setTextColor(getColor(R.color.error))
                return@Observer
            }
            
            if (it.success) {
                showAppToast("OTP sent successfully", Toast.LENGTH_SHORT)
                binding.loginSection.visibility  = View.GONE
                binding.otpSection.visibility  = View.VISIBLE
                binding.cvOtpBack.visibility = View.VISIBLE

                // B-v1110 #9 (sibling) — same tvCountryCode guard as the send path;
                // otp is always a valid Int so otp.toString().toInt() is safe.
                initOtpUI(mobile.toString(), otp.toString().toInt(), binding.tvCountryCode.text.toString().trim().toIntOrNull() ?: 91)
//                val intent = Intent(this, VerifyOTPActivity::class.java)
//                intent.putExtra(DConstants.MOBILE_NUMBER, mobile)
//                intent.putExtra(DConstants.COUNTRY_CODE, binding.tvCountryCode.text.toString().toInt())
//                intent.putExtra(DConstants.OTP, otp)
//                startActivity(intent)
            } else {
                binding.tvOtpText.text = it.message
                binding.tvOtpText.setTextColor(getColor(R.color.error))
                // binding.cvLogin.setBackgroundResource(R.drawable.card_view_border_error)
            }
        })
        loginViewModel.sendOTPErrorLiveData.observe(this, Observer {
            binding.pbSendOtpLoader.visibility = View.GONE
            binding.btnSendOtp.setText(getString(R.string.send_otp))
            updateSendOtpButtonState()
            binding.tvOtpText.text = getString(R.string.please_try_again_later)
            binding.tvOtpText.setTextColor(getColor(R.color.error))
            // binding.cvLogin.setBackgroundResource(R.drawable.card_view_border_error)
        })

        setMessageWithClickableLink()
        updateSendOtpButtonState()
    }

    private fun updateSendOtpButtonState() {
        val hasTenDigitInput = (binding.etMobileNumber.text?.length ?: 0) == 10
        binding.btnSendOtp.isEnabled = hasTenDigitInput
        binding.btnSendOtp.backgroundTintList = if (hasTenDigitInput) {
            sendOtpEnabledTint
        } else {
            ColorStateList.valueOf(getColor(R.color.grey_medium))
        }
    }

    private fun setMessageWithClickableLink() {
        val content = HtmlCompat.fromHtml(
            getString(R.string.terms_and_conditions_text),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()

        val spannableString = SpannableString(content)

        addClickablePart(
            spannableString,
            "terms & conditions"
        ) {
            // B_057: open the native card Terms screen (with header), same as
            // Profile → Terms, instead of the old headerless WebviewActivity.
            startActivity(Intent(this, TermsPolicyActivity::class.java))
        }


        addClickablePart(
            spannableString,
            "community guidelines & moderation policy"
        ) {
            startActivity(Intent(this, CommunityGuidelineActivity::class.java))
        }

        binding.tvTermsAndConditions.text = spannableString
        binding.tvTermsAndConditions.movementMethod = LinkMovementMethod.getInstance()
        binding.tvTermsAndConditions.highlightColor = Color.TRANSPARENT

        binding.tvOtpTermsAndConditions.text = spannableString
        binding.tvOtpTermsAndConditions.movementMethod = LinkMovementMethod.getInstance()
        binding.tvOtpTermsAndConditions.highlightColor = Color.TRANSPARENT
    }

    private fun addClickablePart(spannable: SpannableString, phrase: String, onClick: () -> Unit) {
        val start = spannable.indexOf(phrase)
        if (start >= 0) {
            val end = start + phrase.length
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) = onClick()

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = getColor(R.color.colorPrimaryDark)
                    ds.isUnderlineText = false
                }
            }
            spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }


//    private fun setMessageWithClickableLink() {
//        val content = getString(R.string.terms_and_conditions_text, getString(R.string.app_name))
//        val clickableSpan = object : ClickableSpan() {
//            override fun onClick(textView: View) {
//                val intent = Intent(this@NewLoginActivity, WebviewActivity::class.java)
//                startActivity(intent)
//            }
//
//            override fun updateDrawState(textPaint: TextPaint) {
//                super.updateDrawState(textPaint)
//                textPaint.color = getColor(R.color.colorPrimaryDark)
//                textPaint.isUnderlineText = false
//            }
//        }
//        val startIndex = content.indexOf("terms & conditions")
//        val endIndex = startIndex + "terms & conditions".length
//        val spannableString = SpannableString(content)
//        spannableString.setSpan(
//            clickableSpan,
//            startIndex,
//            endIndex,
//            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
//        )
//        binding.tvTermsAndConditions.text = spannableString
//        binding.tvTermsAndConditions.movementMethod = LinkMovementMethod.getInstance()
//    }

    private fun sendOTP(mobile: String, countryCode:Int) {
        this.mobile = mobile
        otp?.let {
            binding.pbSendOtpLoader.visibility = View.VISIBLE
            binding.btnSendOtp.setText("")
            loginViewModel.sendOTP(mobile, countryCode, it)
        }
    }

    override fun onItemSelected(country: Country) {
        binding.ivFlag.setImageResource(country.image)
        binding.tvCountryCode.text = country.code
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }


    private fun initOtpUI(mobile: String, otp: Int, countryCode: Int) {
        window.statusBarColor = resources.getColor(R.color.grey_extra_light)
        if (verifyOtpEnabledTint == null) {
            verifyOtpEnabledTint = binding.btnVerifyOtp.backgroundTintList
        }
        binding.tvOtpMobileNumber.text = " $mobile"
        binding.tvOtpMobileNumber.paintFlags =
            binding.tvOtpMobileNumber.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.ivEdit.setOnClickListener(View.OnClickListener {
            binding.loginSection.visibility  = View.VISIBLE
            binding.otpSection.visibility  = View.GONE
            binding.pvOtp.setText("")
            stopTimer()
        })
        loginViewModel.sendOTPResponseLiveData.observe(this, Observer {
            binding.pbLoader.visibility = View.GONE
            binding.btnResendOtp.setText(getString(R.string.resend_otp))
            startTimer()
        })

        loginViewModel.loginErrorLiveData.observe(this, Observer {
            isVerifyingOtp = false
            binding.pbVerifyOtpLoader.visibility = View.GONE
            binding.btnVerifyOtp.setText(getString(R.string.verify_otp))
            updateVerifyOtpButtonState()
            showSnackbar("Invalid OTP. Please try again.")
        })
        loginViewModel.loginResponseLiveData.observe(this, Observer {
            isVerifyingOtp = false
            binding.pbVerifyOtpLoader.visibility = View.GONE
            binding.btnVerifyOtp.setText(getString(R.string.verify_otp))
            updateVerifyOtpButtonState()
            
            // ✅ Add null check before accessing properties
            if (it == null) {
                showSnackbar("Login failed. Please try again.")
                return@Observer
            }
            
            if (it.success) {
                // Marketing funnel — OTP Verified (unique). Fires on the first
                // successful OTP verification for both new and returning users.
                com.gmwapp.hima.utils.HimaAnalytics.logOtpVerified(this, it.data?.id)
                if (it.registered) {
                    // Marketing funnel — App Login (an already-registered user logs in again).
                    com.gmwapp.hima.utils.HimaAnalytics.logAppLogin(this, it.data?.id)
                    it.data?.let { it1 ->
                        BaseApplication.getInstance()?.getPrefs()?.setUserData(it1)
                        BaseApplication.getInstance()?.getPrefs()?.setAuthenticationToken(it.token)
                        // Re-bind the in-memory chat-history cache to the freshly
                        // signed-in user so any stale entries from a prior account
                        // on this device are dropped before the chat list opens.
                        runCatching {
                            BaseApplication.getInstance()?.chatHistoryMemoryCache?.setOwner(it1.id)
                        }
                        // Bump users.datetime immediately on login. force=true so
                        // any throttle window left over from a prior session in
                        // the same process doesn't suppress this ping.
                        runCatching { activeStatusReporter.reportActive(force = true) }

                        // Record acceptance of the call-safety disclosure carried by
                        // the "community guidelines & moderation policy" link on this
                        // screen. Tied to this path specifically: the consent row must
                        // only exist where the user was actually shown the text.
                        runCatching { CallModerationConsent.submitIfRequired() }

                        // Bind this device to the user's external id *immediately* on OTP success.
                        // Without this the subscription wouldn't be tagged until the user reached
                        // Home, which was the original trigger for the churn we just deleted.
                        runCatching {
                            OneSignal.login(it1.id.toString())
                            // Unconditional — the local `optedIn` flag can lie (cached true
                            // before the server round-trip lands), so guarding on it leaves
                            // users stuck with enabled=false on OneSignal's servers.
                            OneSignal.User.pushSubscription.optIn()
                            Log.d("OneSignalFix", "OTP success — login+optIn for externalId=${it1.id}")
                            com.gmwapp.hima.utils.OneSignalDiag.dump(this, "after_fresh_login")
                        }.onFailure {
                            Log.e("OneSignalFix", "OTP-success OneSignal bind failed: ${it.message}")
                        }

                        // Female users routed to AlmostDone/VoiceIdentification never hit
                        // MainActivity.updateFcmToken, so the backend keeps a stale "0" from
                        // the previous logout — direct FCM pushes silently drop until reinstall.
                        // Register here so every OTP success re-binds (uid, device token).
                        registerFcmTokenForNewLogin(it1.id)

                        // Socket.IO will connect only when ChatActivityInHouse opens
                        Log.d("SocketIOCheck", "✅ Login successful - Socket.IO will connect when chat opens")
                    }
                    var intent: Intent? = null
                    if (it.data?.gender == DConstants.MALE) {
                        intent = Intent(this, MainActivity::class.java)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                    } else {
                        if (it.data?.status == 2) {
                            intent = Intent(this, MainActivity::class.java)
                            intent.putExtra(
                                DConstants.AVATAR_ID,
                                getIntent().getIntExtra(DConstants.AVATAR_ID, 0)
                            )
                            intent.putExtra(DConstants.LANGUAGE, it.data?.language)
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        } else if (it.data?.status == 1) {
                            intent = Intent(this, AlmostDoneActivity::class.java)
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        } else {
                            intent = Intent(this, VoiceIdentificationActivity::class.java)
                            intent.putExtra(DConstants.LANGUAGE, it.data?.language)
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                    }
                    startActivity(intent)
                    finish()
                } else {
                    val intent = Intent(this, SelectGenderActivity::class.java)
                    intent.putExtra(DConstants.MOBILE_NUMBER, it.usernumber)
                    startActivity(intent)
                }
            } else {
                showSnackbar(it.message ?: "Invalid OTP. Please try again.")
            }
        })
        binding.btnResendOtp.setOnClickListener({
            binding.btnResendOtp.setText("")
            binding.pbLoader.visibility = View.VISIBLE
            loginViewModel.sendOTP(mobile, countryCode, otp)
        })
        binding.pvOtp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                updateVerifyOtpButtonState()
            }
        }
        )
        updateVerifyOtpButtonState()
        binding.btnVerifyOtp.setOnClickListener {
            closeKeyboard()
            val enteredOtp = binding.pvOtp.text.toString()
            val serverOtp = otp?.toString()
            val defaultOtp = "011011"

            // defaultOtp is kept active in production intentionally (app-review / QA access).
            if (enteredOtp == serverOtp || enteredOtp == defaultOtp) {
                isVerifyingOtp = true
                binding.pbVerifyOtpLoader.visibility = View.VISIBLE
                binding.btnVerifyOtp.text = ""
                updateVerifyOtpButtonState()
                login(mobile)
            } else {
                showSnackbar("Invalid OTP. Please try again.")
            }
        }
    }

    private fun startTimer(){
        timer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.btnResendOtp.isEnabled = false
                val time = millisUntilFinished / 1000
                binding.btnResendOtp.setText(getString(R.string.retry_in, if(time<10) "0$time" else time.toString()))
            }

            override fun onFinish() {
                binding.btnResendOtp.visibility = View.VISIBLE
                binding.btnResendOtp.setText("Resend")
                binding.btnResendOtp.isEnabled = true
                binding.btnResendOtp.isEnabled = true
            }
        }.start()
    }

    private fun stopTimer() {
        timer?.cancel()  // This will stop the countdown and call onFinish()
        binding.btnResendOtp.setText("Resend") // Optionally reset the button text
    }


    private fun login(mobile: String) {
        Log.d("VerifyOTP", "Calling login function now")
        Log.d("VerifyOTP", "$mobile")

        if (mobile.isNotEmpty()){
            Log.d("VerifyOTP", "Not Empty")

            loginViewModel.login(mobile,"0","0")

        }
    }

    private fun updateVerifyOtpButtonState() {
        val hasCompleteOtp = (binding.pvOtp.text?.length ?: 0) == 6
        val isEnabled = hasCompleteOtp && !isVerifyingOtp
        binding.btnVerifyOtp.isEnabled = isEnabled
        binding.btnVerifyOtp.backgroundTintList = if (isEnabled) {
            verifyOtpEnabledTint
        } else {
            ColorStateList.valueOf(getColor(R.color.grey_medium))
        }
    }


    private fun closeKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocusedView = currentFocus
        if (currentFocusedView != null) {
            inputMethodManager.hideSoftInputFromWindow(currentFocusedView.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
        }
    }

    fun checkReferal(){
        // Referral code: allow only letters/digits and max 10 chars
        binding.etReferCode.filters = arrayOf(
            InputFilter.LengthFilter(10),
            InputFilter { source, start, end, dest, dstart, dend ->
                if (source.isNullOrEmpty()) return@InputFilter null
                val isAlphaNumericOnly = source.toString().matches(Regex("[a-zA-Z0-9]*"))
                if (isAlphaNumericOnly) null else ""
            }
        )
        fun updateReferralApplyButtonState() {
            val codeLength = binding.etReferCode.text?.toString()?.trim()?.length ?: 0
            val isEnabled = codeLength >= 8
            binding.applyReferral.isEnabled = isEnabled
            binding.applyReferral.backgroundTintList = if (isEnabled) {
                ColorStateList.valueOf(getColor(R.color.colorAccent))
            } else {
                ColorStateList.valueOf(getColor(R.color.grey_medium))
            }
        }

        binding.referCodeCheckbox.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                //  binding.btnSendOtp.setBackgroundResource(R.drawable.d_button_bg_white)
                binding.cvReferCode.visibility = View.VISIBLE
                updateReferralApplyButtonState()
            } else {

                binding.etReferCode.setText("")
                binding.cvReferCode.visibility = View.GONE
                binding.applyReferral.isEnabled = false
            }
        }

        binding.etReferCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                binding.applyReferral.setText("Apply")
                DPreferences(this@NewLoginActivity).setReferralCode("") // Save empty referral code initially
                val savedReferCode = DPreferences(this@NewLoginActivity).getReferralCode()

                Log.d("savedReferCode","$savedReferCode")
                updateReferralApplyButtonState()


            }

            override fun afterTextChanged(s: Editable) {
            }
        })



        binding.applyReferral.setOnClickListener {
            val mobile = binding.etMobileNumber.text.toString()

            val mobileRegex = Regex("^[6-9]\\d{9}$")
            var refercode = binding.etReferCode.text.toString()

            // Validation logic
            if (TextUtils.isEmpty(mobile) || !mobile.matches(mobileRegex)) {
                showSnackbar("Enter a valid 10-digit mobile number")
            }else if (binding.etReferCode.text.isEmpty()){
                showSnackbar("Referral code can't be empty")
            }else{
                binding.applyReferral.setText("")
                binding.applyReferral.isEnabled = false
                binding.pbApplyReferralLoader.visibility = View.VISIBLE
                referralCodeViewModel.checkReferCode(mobile,refercode)
            }
        }
        binding.applyReferral.isEnabled = false
        binding.applyReferral.backgroundTintList = ColorStateList.valueOf(getColor(R.color.grey_medium))
    }

    fun observeReferralCodeResponse() {

        referralCodeViewModel.referCodeResponseLiveData.observe(this) { response ->
            Log.d("referCodeResponseLiveData", "$response")

            response?.let {
                binding.pbApplyReferralLoader.visibility = View.GONE
                if (it.success) {
                    binding.applyReferral.setText("Applied")
                    binding.applyReferral.isEnabled = false
                    showAppToast("Refer code applied successfully", Toast.LENGTH_SHORT)
                    val referCode = binding.etReferCode.text.toString()
                    DPreferences(this).setReferralCode(referCode)
                    val savedReferCode = DPreferences(this).getReferralCode()
                    Log.d("savedReferCode","$savedReferCode")
                } else {
                    binding.applyReferral.setText("Apply")
                    binding.applyReferral.isEnabled = true
                    showSnackbar(it.message ?: "Failed to apply referral code")
                }
            }


        }

    }

    private fun startBackgroundAnimations() {
        // Load logo using Glide
        try {
            Glide.with(this)
                .load(R.drawable.logo)
                .placeholder(R.drawable.logo)
                .into(binding.imageViewLogo)
        } catch (e: Exception) {
            Log.e("LoginAnimation", "Error loading logo: ${e.message}")
        }

        // Animate background circles (same as splash screen)
        // Circle 1 - Slow rotation and scale
        val circle1Rotate = ObjectAnimator.ofFloat(binding.circle1, "rotation", 0f, 360f).apply {
            duration = 20000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        val circle1ScaleX = ObjectAnimator.ofFloat(binding.circle1, "scaleX", 1f, 1.2f, 1f).apply {
            duration = 4000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        
        val circle1ScaleY = ObjectAnimator.ofFloat(binding.circle1, "scaleY", 1f, 1.2f, 1f).apply {
            duration = 4000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }

        // Circle 2 - Slow rotation opposite direction
        val circle2Rotate = ObjectAnimator.ofFloat(binding.circle2, "rotation", 360f, 0f).apply {
            duration = 15000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        val circle2ScaleX = ObjectAnimator.ofFloat(binding.circle2, "scaleX", 1f, 1.3f, 1f).apply {
            duration = 5000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        
        val circle2ScaleY = ObjectAnimator.ofFloat(binding.circle2, "scaleY", 1f, 1.3f, 1f).apply {
            duration = 5000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }

        circle1Rotate.start()
        circle1ScaleX.start()
        circle1ScaleY.start()
        circle2Rotate.start()
        circle2ScaleX.start()
        circle2ScaleY.start()

        startDecorAnimations()
    }

    private fun registerFcmTokenForNewLogin(userId: Int) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("FCMToken", "OTP-success token fetch failed", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result ?: return@addOnCompleteListener
                // Drop any pending logout-invalidation for this user — otherwise the
                // stale worker could race-reset the token we're about to register.
                androidx.work.WorkManager.getInstance(applicationContext)
                    .cancelUniqueWork(
                        "${com.gmwapp.hima.workers.FcmTokenInvalidationWorker.WORK_NAME_PREFIX}$userId"
                    )
                fcmTokenViewModel.sendToken(userId, token)
            }
    }


//    override fun onRequestPermissionsResult(
//        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//
//        if (requestCode == 1001) {
//            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
//                showAppToast("Permissions granted", Toast.LENGTH_SHORT)
//            } else {
//                showAppToast("Permissions denied. Verification may not work.", Toast.LENGTH_LONG)
//            }
//        }
//    }

    // ===== Login decorative animations (float / twinkle / entrance / shine / breathing) =====
    private val decorAnimators = mutableListOf<android.animation.Animator>()

    private fun startDecorAnimations() {
        val density = resources.displayMetrics.density
        fun dp(v: Float) = v * density

        val tiles = listOf(
            binding.orbChat, binding.orbStar, binding.orbConnect,
            binding.orbVideo, binding.orbPhone, binding.orbHeart
        )
        val sparks = listOf(
            binding.spark1, binding.spark2, binding.spark3, binding.spark4, binding.spark5
        )
        val shines = listOf(
            binding.shineChat, binding.shineVideo, binding.shineVoice, binding.shineConnect
        )

        // Window after which all entrance animations have finished.
        val entranceTotal = 80L * (tiles.size + sparks.size) + 500L

        // ENTRANCE — fade + scale-in around the logo, staggered.
        (tiles + sparks).forEachIndexed { i, v ->
            v.alpha = 0f
            v.scaleX = 0.6f
            v.scaleY = 0.6f
            v.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(80L * i)
                .setDuration(380)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.6f))
                .start()
        }

        // FLOAT — tiles gently bob up/down, varied timing, staggered.
        val floatDur = longArrayOf(2300, 2600, 2750, 2450, 2100, 2250)
        tiles.forEachIndexed { i, v ->
            val a = ObjectAnimator.ofFloat(v, "translationY", 0f, -dp(7f)).apply {
                duration = floatDur[i % floatDur.size]
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                startDelay = entranceTotal + 120L * i
            }
            decorAnimators.add(a); a.start()
        }

        // TWINKLE — sparkles pulse opacity + slight scale (after entrance).
        val twinkleDur = longArrayOf(1400, 1700, 1300, 1550, 1850)
        sparks.forEachIndexed { i, v ->
            val pa = android.animation.PropertyValuesHolder.ofFloat("alpha", 1f, 0.35f)
            val px = android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 0.82f)
            val py = android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 0.82f)
            val a = ObjectAnimator.ofPropertyValuesHolder(v, pa, px, py).apply {
                duration = twinkleDur[i % twinkleDur.size]
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                startDelay = entranceTotal + 90L * i
            }
            decorAnimators.add(a); a.start()
        }

        // SHINE — gloss sweep across each feature tile; sweep then hold = gap between passes.
        val shineStart = -dp(60f)
        val shineEnd = dp(60f)
        shines.forEachIndexed { i, v ->
            val kf0 = android.animation.Keyframe.ofFloat(0f, shineStart)
            val kf1 = android.animation.Keyframe.ofFloat(0.45f, shineEnd)
            val kf2 = android.animation.Keyframe.ofFloat(1f, shineEnd)
            val pvh = android.animation.PropertyValuesHolder.ofKeyframe("translationX", kf0, kf1, kf2)
            val a = ObjectAnimator.ofPropertyValuesHolder(v, pvh).apply {
                duration = 3400
                repeatCount = ObjectAnimator.INFINITE
                startDelay = 220L * i
            }
            decorAnimators.add(a); a.start()
        }

        // LOGO — slow breathing.
        val lpx = android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.03f)
        val lpy = android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.03f)
        val logoAnim = ObjectAnimator.ofPropertyValuesHolder(binding.logoContainer, lpx, lpy).apply {
            duration = 4000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        decorAnimators.add(logoAnim); logoAnim.start()
    }

    override fun onDestroy() {
        decorAnimators.forEach { it.cancel() }
        decorAnimators.clear()
        super.onDestroy()
    }

}


