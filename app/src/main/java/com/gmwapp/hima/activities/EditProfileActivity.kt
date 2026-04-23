package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.PagerSnapHelper
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.AvatarsListAdapter
import com.gmwapp.hima.adapters.InterestsListAdapter
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityEditProfileBinding
import com.gmwapp.hima.retrofit.responses.Interests
import com.gmwapp.hima.viewmodels.ProfileViewModel
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxItemDecoration
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import dagger.hilt.android.AndroidEntryPoint
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@AndroidEntryPoint
class EditProfileActivity : BaseActivity() {
    private var interestsListAdapter: InterestsListAdapter? = null
    private var avatarsListAdapter: AvatarsListAdapter? = null
    lateinit var binding: ActivityEditProfileBinding
    private val profileViewModel: ProfileViewModel by viewModels()
    private val selectedInterests: ArrayList<String> = ArrayList()
    private var isValidUserName = true
    private var originalUserName: String? = null
    private var originalAvatarId: Int? = null
    private var originalInterestsList: List<String> = emptyList()
    private var cachedUserId: Int? = null
    private val nameValidatorHandler = Handler(Looper.getMainLooper())
    private var pendingNameValidator: Runnable? = null
    private val nameDebounceMs = 300L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initUI()

        onBackPressedDispatcher.addCallback(this) {
            binding.cvBack.performClick()
        }
    }

    override fun onDestroy() {
        pendingNameValidator?.let { nameValidatorHandler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun initUI() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        originalUserName = userData?.name
        originalAvatarId = userData?.avatar_id
        cachedUserId = userData?.id
        originalInterestsList = userData?.interests
            ?.removeSurrounding("[", "]")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        binding.etUserName.setText(userData?.name)
        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)

        val gender = userData?.gender

        if (gender == "male" || gender == "Male") {
            binding.tvGender.text = "Male"
        }
        else
        {
            binding.tvGender.text = "Female"
        }
        
        // Always allow users to edit their name - backend will validate and return error if second time
        binding.etUserName.isEnabled = true

        binding.tvPreferredLanguage.text = userData?.language
        //  binding.btnUpdate.setBackgroundResource(R.drawable.d_button_bg_disabled)
        binding.cvBack.setOnClickListener(View.OnClickListener {
            if (hasUnsavedChanges()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.edit_profile)
                    .setMessage(R.string.discard_changes_message)
                    .setPositiveButton(R.string.discard) { dialog, _ ->
                        dialog.dismiss()
                        finish()
                    }
                    .setNegativeButton(R.string.keep_editing) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            } else {
                finish()
            }
        })
        window.navigationBarColor = getColor(R.color.black_background)

        binding.etUserName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                val text = s.toString()
                pendingNameValidator?.let { nameValidatorHandler.removeCallbacks(it) }

                if (text.length < 4) {
                    isValidUserName = false
                    binding.cvUserName.setBackgroundResource(R.drawable.d_button_bg_error)
                    binding.pbUserNameLoader.visibility = View.GONE
                    binding.ivSuccess.visibility = View.GONE
                    binding.ivWarning.visibility = View.VISIBLE
                    binding.tvUserNameHint.text = getString(R.string.user_name_hint)
                    binding.tvUserNameHint.setTextColor(getColor(android.R.color.white))
                    updateButton()
                    return
                }

                // Show loader immediately so user sees feedback; fire API after debounce
                binding.pbUserNameLoader.visibility = View.VISIBLE
                val uid = cachedUserId ?: return
                val runnable = Runnable {
                    profileViewModel.userValidation(uid, text)
                }
                pendingNameValidator = runnable
                nameValidatorHandler.postDelayed(runnable, nameDebounceMs)
            }

            override fun afterTextChanged(s: Editable) {
            }
        })

        val staggeredGridLayoutManager = FlexboxLayoutManager(this).apply {
            flexWrap = FlexWrap.WRAP
            alignItems = AlignItems.FLEX_START
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.FLEX_START
        }
        val itemDecoration = FlexboxItemDecoration(this).apply {
            setDrawable(ContextCompat.getDrawable(this@EditProfileActivity, R.drawable.bg_divider))
            setOrientation(FlexboxItemDecoration.VERTICAL)
        }
        binding.rvInterests.addItemDecoration(itemDecoration)
        binding.rvInterests.setLayoutManager(staggeredGridLayoutManager)

        val interests = userData?.interests?.removeSurrounding("[", "]")?.split(",")?.map { it.trim() }
      //  val interests = interestsString.removeSurrounding("[", "]").split(",").map { it.trim() }
        interests?.let { selectedInterests.addAll(it) }


        interestsListAdapter = InterestsListAdapter(this, arrayListOf(
            Interests(
                getString(R.string.politics),
                R.drawable.politics,
                interests?.contains(getString(R.string.politics))
            ),
            Interests(
                getString(R.string.art),
                R.drawable.art,
                interests?.contains(getString(R.string.art))
            ),
            Interests(
                getString(R.string.sports),
                R.drawable.sports,
                interests?.contains(getString(R.string.sports))

            ),
            Interests(
                getString(R.string.movies),
                R.drawable.movie,
                interests?.contains(getString(R.string.movies))
            ),
            Interests(
                getString(R.string.music),
                R.drawable.music,
                interests?.contains(getString(R.string.music))
            ),
            Interests(
                getString(R.string.foodie),
                R.drawable.foodie,
                interests?.contains(getString(R.string.foodie))
            ),
            Interests(
                getString(R.string.travel),
                R.drawable.travel,
                interests?.contains(getString(R.string.travel))
            ),
            Interests(
                getString(R.string.photography),
                R.drawable.photography,
                interests?.contains(getString(R.string.photography))
            ),
            Interests(
                getString(R.string.love),
                R.drawable.love,
                interests?.contains(getString(R.string.love))
            ),
            Interests(
                getString(R.string.cooking),
                R.drawable.cooking,
                interests?.contains(getString(R.string.cooking))
            ),
        ), false, object : OnItemSelectionListener<Interests> {
            override fun onItemSelected(interest: Interests) {
                if (interest.isSelected == true) {
                    selectedInterests.remove(interest.name)
                } else {
                    selectedInterests.add(interest.name)
                }
                interestsListAdapter?.updateLimitReached(selectedInterests.size == 5)
           //     showAppToast(selectedInterests.size.toString(), Toast.LENGTH_LONG)
                updateButton()
            }
        })



        binding.rvAvatars.setOnScrollChangeListener(View.OnScrollChangeListener { view: View, i: Int, i1: Int, i2: Int, i3: Int ->
            val layoutManager = binding.rvAvatars.layoutManager as? CenterLayoutManager
            val selectedPos = layoutManager?.findFirstCompletelyVisibleItemPosition()
            if (selectedPos != null && selectedPos >= 0) {
                avatarsListAdapter?.setSelectedPosition(selectedPos)
            }
            updateButton()
        })

        binding.ivAvatarRight.setOnClickListener {
            val layoutManager = binding.rvAvatars.layoutManager as CenterLayoutManager
            val current = layoutManager.findFirstCompletelyVisibleItemPosition()
            val total = avatarsListAdapter?.itemCount ?: 0
            if (current < total - 1) {
                binding.rvAvatars.smoothScrollToPosition(current + 1)
            }
        }

        binding.ivAvatarLeft.setOnClickListener {
            val layoutManager = binding.rvAvatars.layoutManager as CenterLayoutManager
            val current = layoutManager.findFirstCompletelyVisibleItemPosition()
            if (current > 0) {
                binding.rvAvatars.smoothScrollToPosition(current - 1)
            }
        }
        binding.btnUpdate.setOnClickListener(View.OnClickListener {
            val layoutManager = binding.rvAvatars.layoutManager as CenterLayoutManager
            val visiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
            
            // Get the selected avatar ID, or use the current one if not changed
            val avatarId = if (visiblePosition >= 0) {
                profileViewModel.avatarsListLiveData.value?.data?.get(visiblePosition)?.id
            } else {
                userData?.avatar_id
            }
            
            userData?.let { it1 ->
                avatarId?.let { it2 ->
                    binding.pbUpdateLoader.visibility = View.VISIBLE
                    binding.btnUpdate.text = ""
                    binding.btnUpdate.isEnabled = false
                    profileViewModel.updateProfile(
                        it1.id, it2, binding.etUserName.text.toString(), selectedInterests
                    )
                }
            }

        })
        binding.rvInterests.setAdapter(interestsListAdapter)
        binding.cvUserName.setBackgroundResource(R.drawable.d_button_bg_user_name)
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(binding.rvAvatars)
        setCenterLayoutManager(binding.rvAvatars)
        userData?.gender?.let { profileViewModel.getAvatarsList(it) }
        profileViewModel.userValidationLiveData.observe(this, Observer { response ->
            Log.d("EditProfile", "userValidationLiveData received: success=${response?.success}, message=${response?.message}")
            
            if (response != null && response.success) {
                // ✅ Success - name is valid
                isValidUserName = true
                binding.cvUserName.setBackgroundResource(R.drawable.d_button_bg_user_name)
                binding.pbUserNameLoader.visibility = View.GONE
                binding.ivSuccess.visibility = View.VISIBLE
                binding.ivWarning.visibility = View.GONE
                binding.tvUserNameHint.text = getString(R.string.user_name_hint)
                binding.tvUserNameHint.setTextColor(getColor(R.color.white))
                binding.tvUserNameHint.visibility = View.VISIBLE
            } else {
                // ❌ Error - display error message from API
                isValidUserName = false
                binding.cvUserName.setBackgroundResource(R.drawable.d_button_bg_error)
                binding.pbUserNameLoader.visibility = View.GONE
                binding.ivSuccess.visibility = View.GONE
                binding.ivWarning.visibility = View.VISIBLE
                
                // Get error message from API response (sanitized)
                val errorMessage = safeServerMessage(response?.message)

                Log.d("EditProfile", "Displaying error message: $errorMessage")
                
                // Set error message in TextView
                binding.tvUserNameHint.text = errorMessage
                binding.tvUserNameHint.setTextColor(getColor(android.R.color.white))
                binding.tvUserNameHint.visibility = View.VISIBLE
                
                // Also show Toast to ensure user sees the error
                showAppToast(errorMessage, Toast.LENGTH_LONG)
                
                Log.d("EditProfile", "Error message displayed - TextView text: ${binding.tvUserNameHint.text}, visibility: ${binding.tvUserNameHint.visibility}")
            }
            updateButton()
        })
        profileViewModel.userValidationErrorLiveData.observe(this, Observer {
            isValidUserName = false
            binding.cvUserName.setBackgroundResource(R.drawable.d_button_bg_error)
            binding.pbUserNameLoader.visibility = View.GONE
            binding.ivSuccess.visibility = View.GONE
            binding.ivWarning.visibility = View.VISIBLE
            
            if (it == DConstants.NO_NETWORK) {
                binding.tvUserNameHint.text = getString(R.string.please_try_again_later)
            } else {
                // Display the sanitized error message from API or exception
                binding.tvUserNameHint.text = safeServerMessage(it)
            }
            
            binding.tvUserNameHint.setTextColor(getColor(android.R.color.white))
            updateButton()
        })
        profileViewModel.updateProfileErrorLiveData.observe(this, Observer {
            binding.pbUpdateLoader.visibility = View.GONE
            binding.btnUpdate.text = getString(R.string.update)
            binding.btnUpdate.isEnabled = true
            showAppToast(getString(R.string.please_try_again_later), Toast.LENGTH_LONG)
        })
        profileViewModel.updateProfileLiveData.observe(this, Observer {
            binding.pbUpdateLoader.visibility = View.GONE
            binding.btnUpdate.text = getString(R.string.update)
            binding.btnUpdate.isEnabled = true
            if (it.data != null) {
                // Check if name was changed
                val oldName = originalUserName
                val newName = it.data.name
                if (oldName != null && oldName != newName) {
                    // Name was changed, mark it in SharedPreferences
                    sharedPreferences.edit().putBoolean("hasChangedName", true).apply()
                }
                
                showAppToast(getString(R.string.profile_updated), Toast.LENGTH_LONG)

                BaseApplication.getInstance()?.getPrefs()?.setUserData(it.data)
                
                // ✅ Update profile picture in Firebase
                updateProfilePicInFirebase(it.data.id, it.data.image)
                
                setResult(RESULT_OK)
                finish()
            } else {
                // Show sanitized error message from backend (e.g., "You can change your name only once.")
                showAppToast(safeServerMessage(it.message), Toast.LENGTH_LONG)
            }
        })
        profileViewModel.avatarsListLiveData.observe(this, Observer {
            if (it.data != null) {
                avatarsListAdapter = AvatarsListAdapter(
                    this, it.data
                )
                binding.rvAvatars.setAdapter(avatarsListAdapter)
                val index = it.data.find { it?.id == userData?.avatar_id }
                it.data.remove(index)
                it.data.add(0, index)
                binding.rvAvatars.smoothScrollToPosition(0)
                binding.rvAvatars.post {
                    avatarsListAdapter?.setSelectedPosition(0)
                }
            }
        })
    }

    /**
     * Returns a safe, user-facing message from a server response.
     * Falls back to the generic retry string when the server returns
     * an HTML page, a stack trace, or anything obviously non-user-friendly.
     */
    private fun safeServerMessage(raw: String?): String {
        val fallback = getString(R.string.please_try_again_later)
        if (raw.isNullOrBlank()) return fallback
        val trimmed = raw.trim()
        val lower = trimmed.lowercase()
        if (trimmed.startsWith("<") ||
            lower.startsWith("<!doctype") ||
            lower.contains("<html") ||
            lower.contains("<body") ||
            lower.contains("stack trace") ||
            lower.contains("exception in") ||
            trimmed.length > 160
        ) return fallback
        return trimmed
    }

    private fun hasUnsavedChanges(): Boolean {
        val typedName = binding.etUserName.text?.toString().orEmpty()
        if (typedName != (originalUserName ?: "")) return true

        val sameInterests =
            selectedInterests.size == originalInterestsList.size &&
                originalInterestsList.containsAll(selectedInterests)
        if (!sameInterests) return true

        val layoutManager = binding.rvAvatars.layoutManager as? CenterLayoutManager
        val index = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: -1
        if (index >= 0) {
            val visibleAvatar = profileViewModel.avatarsListLiveData.value?.data?.get(index)?.id
            if (visibleAvatar != null && visibleAvatar != originalAvatarId) return true
        }
        return false
    }

    private fun updateButton() {
        val typedName = binding.etUserName.text?.toString().orEmpty()
        val usernameChanged = typedName != (originalUserName ?: "")

        val interestsChanged =
            selectedInterests.size != originalInterestsList.size ||
                !originalInterestsList.containsAll(selectedInterests)

        val layoutManager = binding.rvAvatars.layoutManager as? CenterLayoutManager
        val index = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: -1
        val avatarChanged = if (index >= 0) {
            profileViewModel.avatarsListLiveData.value?.data?.get(index)?.id != originalAvatarId
        } else {
            false
        }

        binding.btnUpdate.isEnabled =
            isValidUserName && (usernameChanged || interestsChanged || avatarChanged)
    }

    // ✅ Update profile picture in Firebase for all chat threads
    private fun updateProfilePicInFirebase(userId: Int, imageUrl: String) {
        Log.d("ProfileUpdate", "🔍 Starting profile pic update - userId: $userId, imageUrl: $imageUrl")
        
        if (imageUrl.isEmpty()) {
            Log.d("ProfileUpdate", "⚠️ Image URL is empty, skipping update")
            return
        }
        
        val db = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "himadatabase")
        
        // Get all chat threads where this user is a participant
        Log.d("ProfileUpdate", "🔍 Querying chats collection for userId: $userId")
        
        db.collection("chats")
            .whereArrayContains("participantIds", userId.toString())
            .get()
            .addOnSuccessListener { querySnapshot ->
                Log.d("ProfileUpdate", "✅ Query successful - Found ${querySnapshot.documents.size} chat threads")
                
                if (querySnapshot.documents.isEmpty()) {
                    Log.d("ProfileUpdate", "⚠️ No chat threads found with participantIds, will try direct thread lookup")
                    // Fallback: try to find threads by checking all documents
                    findAndUpdateThreadsByDirectQuery(userId, imageUrl)
                    return@addOnSuccessListener
                }
                
                querySnapshot.documents.forEach { doc ->
                    Log.d("ProfileUpdate", "📝 Updating thread: ${doc.id}")
                    doc.reference.update(mapOf("user_${userId}_image" to imageUrl as Any))
                        .addOnSuccessListener {
                            Log.d("ProfileUpdate", "✅ Updated avatar in thread: ${doc.id} with URL: $imageUrl")
                        }
                        .addOnFailureListener { e ->
                            Log.e("ProfileUpdate", "❌ Failed to update avatar in ${doc.id}: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ProfileUpdate", "❌ Error fetching chat threads: ${e.message}")
                // Fallback approach
                findAndUpdateThreadsByDirectQuery(userId, imageUrl)
            }
    }
    
    // Fallback method to find threads by thread ID format
    private fun findAndUpdateThreadsByDirectQuery(userId: Int, imageUrl: String) {
        Log.d("ProfileUpdate", "🔍 Trying fallback approach - scanning all chat threads")
        
        val db = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "himadatabase")
        val userIdStr = userId.toString()
        
        db.collection("chats")
            .get()
            .addOnSuccessListener { querySnapshot ->
                var updateCount = 0
                Log.d("ProfileUpdate", "📋 Scanning ${querySnapshot.documents.size} total threads")
                
                querySnapshot.documents.forEach { doc ->
                    val threadId = doc.id
                    // Check if this thread involves our user (format: userId_otherUserId or otherUserId_userId)
                    if (threadId.contains(userIdStr)) {
                        Log.d("ProfileUpdate", "📝 Found matching thread: $threadId")
                        doc.reference.update(mapOf("user_${userId}_image" to imageUrl as Any))
                            .addOnSuccessListener {
                                Log.d("ProfileUpdate", "✅ Updated avatar in thread: $threadId")
                                updateCount++
                            }
                            .addOnFailureListener { e ->
                                Log.e("ProfileUpdate", "❌ Failed to update thread $threadId: ${e.message}")
                            }
                    }
                }
                Log.d("ProfileUpdate", "📊 Updated $updateCount threads")
            }
            .addOnFailureListener { e ->
                Log.e("ProfileUpdate", "❌ Error in fallback query: ${e.message}")
            }
    }
}


