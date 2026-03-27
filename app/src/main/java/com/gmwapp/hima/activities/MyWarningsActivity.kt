package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.adapters.MyWarningsAdapter
import com.gmwapp.hima.databinding.ActivityMyWarningsBinding
import com.gmwapp.hima.databinding.BottomSheetWarningHistoryBinding
import com.gmwapp.hima.retrofit.responses.MyWarningItem
import com.gmwapp.hima.viewmodels.MyWarningsViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MyWarningsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyWarningsBinding
    private val viewModel: MyWarningsViewModel by viewModels()
    private var oldWarningsCache: List<MyWarningItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyWarningsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cvBack.setOnClickListener {
            val messageCameWhenIsAlive = BaseApplication.getInstance()?.messageCameWhenIsAlive ?: 0
            if (messageCameWhenIsAlive == 0) {
                val intent = Intent(this@MyWarningsActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                finish()
            } else {
                finish()
            }
        }
        binding.ivMore.setOnClickListener {
            showHistoryBottomSheet(oldWarningsCache)
        }
        binding.tvHistoryHint.setOnClickListener {
            showHistoryBottomSheet(oldWarningsCache)
        }

        // History is shown via bottom sheet
        binding.rvWarnings.layoutManager = LinearLayoutManager(this)

        observe()
        load()
        onBackPressedBtn()
    }

    private fun observe() {
        viewModel.myWarningsLiveData.observe(this) { response ->
            binding.progressBar.visibility = View.GONE
            Log.d("MyWarningsUI", "my_warnings response: $response")

            if (response?.success != true) {
                showEmpty(showSummary = false)
                showAppToast(response?.message ?: "Failed to load warnings", Toast.LENGTH_SHORT)
                return@observe
            }

            oldWarningsCache = response.oldWarningData ?: emptyList()
            val hasHistory = oldWarningsCache.isNotEmpty()
            binding.cvMoreBtn.visibility = View.GONE
            binding.tvHistoryHint.visibility = if (hasHistory) View.VISIBLE else View.GONE

            // Current warning data (only)
            val current = response.currentLevelWarningData
            val titleFromApi = current?.title?.trim().orEmpty()
            val normalizedTitle = titleFromApi
                .replace(Regex("^Level\\s*\\d+\\s*:\\s*", RegexOption.IGNORE_CASE), "")
                .trim()

            // Yellow badge: "Current level : Soft Warning"
            val badgeText = when {
                normalizedTitle.isNotEmpty() -> "Current level : $normalizedTitle"
                current?.level != null -> "Current level : Level ${current.level}"
                else -> ""
            }
            if (badgeText.isNotEmpty()) {
                binding.tvCurrentLevel.visibility = View.VISIBLE
                binding.tvCurrentLevel.text = badgeText
            } else {
                binding.tvCurrentLevel.visibility = View.GONE
            }

            // Remove the duplicate dark title line under the badge.
            binding.tvCurrentTitle.visibility = View.GONE
            binding.tvCurrentTitle.text = ""

            // Hide count; the new screen focuses on current warning only.
            binding.tvTotalWarnings.visibility = View.GONE

            // Details only from current warning object
            val details = current?.details?.trim()
            if (!details.isNullOrEmpty()) {
                binding.tvCurrentDetails.visibility = View.VISIBLE
                binding.tvCurrentDetails.text = details
            } else {
                binding.tvCurrentDetails.visibility = View.GONE
            }

            val warningSystemImage = response.warningSystemImage?.trim()
            if (!warningSystemImage.isNullOrEmpty()) {
                binding.cvWarningSystemImage.visibility = View.VISIBLE
                Glide.with(this)
                    .load(warningSystemImage)
                    .into(binding.ivWarningSystemImage)
            } else {
                binding.cvWarningSystemImage.visibility = View.GONE
            }

            // If current warning is missing, show empty state.
            val hasCurrent = current?.level != null ||
                !current?.title.isNullOrBlank() ||
                !current?.details.isNullOrBlank() ||
                !response.warningSystemImage.isNullOrBlank()
            if (!hasCurrent) {
                showEmpty(showSummary = false)
                binding.tvEmptyTitle.text =
                    response.message?.takeIf { it.isNotBlank() } ?: "No warnings"
                binding.tvEmptySubtitle.text = ""
            } else {
                showCurrentOnly()
            }
        }

        viewModel.myWarningsErrorLiveData.observe(this) { err ->
            binding.progressBar.visibility = View.GONE
            Log.e("MyWarningsUI", "my_warnings error: $err")
            showEmpty(showSummary = false)
            showAppToast(err ?: "Something went wrong", Toast.LENGTH_SHORT)
        }
    }

    private fun load() {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
        if (userId <= 0) {
            showEmpty(showSummary = false)
            showAppToast("User not found. Please login again.", Toast.LENGTH_SHORT)
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        viewModel.getMyWarnings(userId)
    }

    private fun showEmpty(showSummary: Boolean) {
        binding.llEmptyState.visibility = View.VISIBLE
        binding.cvSummary.visibility = if (showSummary) View.VISIBLE else View.GONE
        binding.cvWarningSystemImage.visibility = View.GONE
        binding.cvInfoCard.visibility = View.GONE
        binding.rvWarnings.visibility = View.GONE
    }

    private fun showList() {
        binding.llEmptyState.visibility = View.GONE
        binding.cvSummary.visibility = View.VISIBLE
        binding.rvWarnings.visibility = View.VISIBLE
    }

    private fun showCurrentOnly() {
        binding.llEmptyState.visibility = View.GONE
        binding.cvSummary.visibility = View.VISIBLE
        // Keep image card visibility controlled from observe() based on API field.
        binding.cvInfoCard.visibility = View.VISIBLE
        binding.rvWarnings.visibility = View.GONE
    }

    private fun showHistoryBottomSheet(items: List<MyWarningItem>) {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetWarningHistoryBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.ivClose.setOnClickListener { dialog.dismiss() }

        if (items.isEmpty()) {
            sheetBinding.rvHistory.visibility = View.GONE
            sheetBinding.tvHistoryEmpty.visibility = View.VISIBLE
            sheetBinding.tvHistorySubtitle.visibility = View.GONE
        } else {
            sheetBinding.rvHistory.visibility = View.VISIBLE
            sheetBinding.tvHistoryEmpty.visibility = View.GONE
            sheetBinding.tvHistorySubtitle.visibility = View.VISIBLE

            sheetBinding.rvHistory.layoutManager = LinearLayoutManager(this)
            // Show details for previous warnings in the bottom sheet.
            sheetBinding.rvHistory.adapter = MyWarningsAdapter(items, showDetails = true)
        }

        dialog.show()
    }

    private fun onBackPressedBtn() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val messageCameWhenIsAlive = BaseApplication.getInstance()?.messageCameWhenIsAlive ?: 0

                if (messageCameWhenIsAlive == 0) {
                    val intent = Intent(this@MyWarningsActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    finish()
                }
            }
        })
    }
}

