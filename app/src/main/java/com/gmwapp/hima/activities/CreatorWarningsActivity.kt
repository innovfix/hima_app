package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.adapters.CreatorWarningsAdapter
import com.gmwapp.hima.databinding.ActivityCreatorWarningsBinding
import com.gmwapp.hima.viewmodels.CreatorWarningsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreatorWarningsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatorWarningsBinding
    private val warningsViewModel: CreatorWarningsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatorWarningsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupObservers()
        loadWarnings()
        onBackPressedBtn()
    }

    private fun onBackPressedBtn() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val messageCameWhenIsAlive = BaseApplication.getInstance()?.messageCameWhenIsAlive ?: 0

                if (messageCameWhenIsAlive == 0) {
                    val intent = Intent(this@CreatorWarningsActivity, MainActivity::class.java).apply {
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



    private fun setupToolbar() {
        binding.cvBack.setOnClickListener {
            finish()
        }
    }

    private fun setupObservers() {
        // Observe warnings data
        warningsViewModel.warningsLiveData.observe(this, Observer { response ->
            binding.progressBar.visibility = View.GONE

            if (response?.success == true) {
                val warnings = response.data ?: emptyList()
                
                if (warnings.isEmpty()) {
                    // Show empty state
                    showEmptyState()
                } else {
                    // Show warnings list
                    showWarningsList(warnings.size, response.isBlocked == 1)
                    
                    // Setup RecyclerView
                    binding.rvWarnings.apply {
                        layoutManager = LinearLayoutManager(this@CreatorWarningsActivity)
                        adapter = CreatorWarningsAdapter(warnings)
                        visibility = View.VISIBLE
                    }
                }

                // Show warning count banner if has warnings
                if (response.hasWarnings == true && response.warningCount ?: 0 > 0) {
                    binding.cvWarningsCount.visibility = View.VISIBLE
                    binding.tvWarningsCount.text = if (!response.warningStatusMessage.isNullOrEmpty()) {
                        response.warningStatusMessage
                    } else {
                        "You have ${response.warningCount} warning${if (response.warningCount!! > 1) "s" else ""}"
                    }
                }

                // Show blocked banner if account is blocked
                if (response.isBlocked == 1) {
                    binding.cvBlockedBanner.visibility = View.VISIBLE
                    // Set blocked message from API
                    val blockedMsg = response.blockedMessage
                    if (!blockedMsg.isNullOrEmpty()) {
                        binding.tvBlockedMessage.text = blockedMsg
                    } else {
                        binding.tvBlockedMessage.text = "Your account is currently blocked due to policy violations"
                    }
                }
            } else {
                showError(response?.message ?: "Failed to load warnings")
            }
        })

        // Observe errors
        warningsViewModel.warningsErrorLiveData.observe(this, Observer { error ->
            binding.progressBar.visibility = View.GONE
            showError(error)
        })
    }

    private fun loadWarnings() {
        val userId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0

        if (userId == 0) {
            showError("Unable to fetch warnings. Please try again.")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        warningsViewModel.getCreatorWarnings(userId)
    }

    private fun showEmptyState() {
        binding.llEmptyState.visibility = View.VISIBLE
        binding.rvWarnings.visibility = View.GONE
        binding.cvWarningsCount.visibility = View.GONE
        binding.cvBlockedBanner.visibility = View.GONE
    }

    private fun showWarningsList(count: Int, isBlocked: Boolean) {
        binding.llEmptyState.visibility = View.GONE
        binding.rvWarnings.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        showEmptyState()
    }
}
