package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.TicketImageAdapter
import com.gmwapp.hima.databinding.ActivitySubmitTicketBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@AndroidEntryPoint
class SubmitTicketActivity : BaseActivity() {
    lateinit var binding: ActivitySubmitTicketBinding
    private val accountViewModel: AccountViewModel by viewModels()
    private val selectedImages = mutableListOf<Uri>()
    private lateinit var imageAdapter: TicketImageAdapter

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (selectedImages.size < 3) {
                val insertIndex = selectedImages.size
                selectedImages.add(it)
                imageAdapter.notifyItemInserted(insertIndex)
                updateImageRecyclerView()
            } else {
                showAppToast("You can attach up to 3 images", Toast.LENGTH_SHORT)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubmitTicketBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Status bar configuration
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initUI()
        setupObservers()
    }

    private fun initUI() {
        binding.cvBack.setOnSingleClickListener {
            finish()
        }

        binding.btnSubmit.setOnSingleClickListener {
            submitTicket()
        }

        binding.btnAttachImages.setOnSingleClickListener {
            if (selectedImages.size < 3) {
                imagePickerLauncher.launch("image/*")
            } else {
                showAppToast("You can attach up to 3 images", Toast.LENGTH_SHORT)
            }
        }

        // Hard-limit the description field to 250 characters. Previously only
        // the tvCharCount label reflected the number; the user could still type
        // beyond the limit and the backend's new length cap would reject it.
        binding.etDescription.filters = arrayOf(InputFilter.LengthFilter(250))

        // Character counter
        binding.etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val count = s?.length ?: 0
                binding.tvCharCount.text = "$count/250"
            }
        })

        // Setup RecyclerView for images — remove notify is targeted to the
        // removed row so Android can animate the shrink smoothly.
        imageAdapter = TicketImageAdapter(selectedImages) { position ->
            if (position !in selectedImages.indices) return@TicketImageAdapter
            selectedImages.removeAt(position)
            imageAdapter.notifyItemRemoved(position)
            if (position < selectedImages.size) {
                imageAdapter.notifyItemRangeChanged(position, selectedImages.size - position)
            }
            updateImageRecyclerView()
        }
        binding.rvSelectedImages.layoutManager = GridLayoutManager(this, 3)
        binding.rvSelectedImages.adapter = imageAdapter
    }

    private fun updateImageRecyclerView() {
        // Don't fire notifyDataSetChanged here — callers already notified the
        // adapter about the specific index that changed (add or remove).
        if (selectedImages.isNotEmpty()) {
            binding.rvSelectedImages.visibility = android.view.View.VISIBLE
        } else {
            binding.rvSelectedImages.visibility = android.view.View.GONE
        }
        
        // Update button text with clear, readable text
        val remaining = 3 - selectedImages.size
        when {
            selectedImages.isEmpty() -> {
                binding.btnAttachImages.text = "Attach up to 3 images"
                binding.btnAttachImages.isEnabled = true
            }
            remaining > 0 -> {
                val imageText = if (remaining == 1) "image" else "images"
                binding.btnAttachImages.text = "Attach $remaining more $imageText"
                binding.btnAttachImages.isEnabled = true
            }
            else -> {
                binding.btnAttachImages.text = "Maximum images attached"
                binding.btnAttachImages.isEnabled = false
            }
        }
    }

    private fun submitTicket() {
        val message = binding.etDescription.text.toString().trim()

        if (TextUtils.isEmpty(message)) {
            showAppToast("Please describe your issue", Toast.LENGTH_SHORT)
            return
        }

        if (message.length < 15) {
            showAppToast("Please provide more details (at least 15 characters)", Toast.LENGTH_SHORT)
            return
        }

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id ?: 0

        if (userId <= 0) {
            showAppToast("User not logged in", Toast.LENGTH_SHORT)
            return
        }

        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnSubmit.isEnabled = false
        
        // Convert all selected images to MultipartBody.Part list with correct field names (screenshot1, screenshot2, etc.)
        val screenshotParts = mutableListOf<MultipartBody.Part>()
        selectedImages.forEachIndexed { index, uri ->
            try {
                val file = uriToFile(uri, index)
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                // Use screenshot1, screenshot2, etc. as field names to match API
                val part = MultipartBody.Part.createFormData("screenshot${index + 1}", file.name, requestFile)
                screenshotParts.add(part)
            } catch (e: Exception) {
                Log.e("SubmitTicketActivity", "Error converting image ${index + 1}: ${e.message}")
            }
        }
        
        accountViewModel.submitTicket(userId, message, screenshotParts)
    }

    private fun setupObservers() {
        accountViewModel.submitTicketLiveData.observe(this, Observer { response ->
            binding.progressBar.visibility = android.view.View.GONE
            binding.btnSubmit.isEnabled = true
            if (response != null && response.success) {
                showAppToast(response.message ?: "Ticket submitted successfully", Toast.LENGTH_SHORT)
                binding.etDescription.text?.clear()
                selectedImages.clear()
                updateImageRecyclerView()
                finish()
            } else {
                showAppToast(response?.message ?: "Failed to submit ticket", Toast.LENGTH_SHORT)
            }
        })

        accountViewModel.submitTicketErrorLiveData.observe(this, Observer { error ->
            binding.progressBar.visibility = android.view.View.GONE
            binding.btnSubmit.isEnabled = true
            showAppToast(error ?: "An error occurred", Toast.LENGTH_SHORT)
            Log.e("SubmitTicketActivity", "Error submitting ticket: $error")
        })
    }

    private fun uriToFile(uri: Uri, index: Int): File {
        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        // Include a timestamp so concurrent or fast-retry submits don't reuse
        // the same on-disk path and clobber each other's bytes.
        val file = File(cacheDir, "ticket_image_${System.currentTimeMillis()}_$index.jpg")
        val outputStream = FileOutputStream(file)

        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }

        return file
    }
}

