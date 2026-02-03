package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.TicketsViewPagerAdapter
import com.gmwapp.hima.databinding.ActivityTicketsListBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TicketsListActivity : BaseActivity() {
    lateinit var binding: ActivityTicketsListBinding
    private val accountViewModel: AccountViewModel by viewModels()
    private lateinit var viewPagerAdapter: TicketsViewPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTicketsListBinding.inflate(layoutInflater)
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
        setupViewPager()
        loadTickets()
        observeTickets()
        onBackPressedBtn()
    }

    private fun initUI() {
        binding.cvBack.setOnSingleClickListener {

            val messageCameWhenIsAlive = BaseApplication.getInstance()?.messageCameWhenIsAlive ?: 0

            if (messageCameWhenIsAlive == 0) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                finish()
            } else {
                finish()
            }
        }
    }

    private fun setupViewPager() {
        viewPagerAdapter = TicketsViewPagerAdapter(this)
        binding.viewPager.adapter = viewPagerAdapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Active"
                1 -> "Resolved"
                else -> ""
            }
        }.attach()
        
        // Check if we need to open a specific tab from intent
        val tabPosition = intent.getIntExtra("TAB_POSITION", 0)
        if (tabPosition in 0..1) {
            binding.viewPager.post {
                binding.viewPager.setCurrentItem(tabPosition, false)
            }
        }
    }

    private fun loadTickets() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id ?: 0

        if (userId > 0) {
            binding.progressBar.visibility = android.view.View.VISIBLE
            accountViewModel.getTicketsList(userId)
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeTickets() {
        accountViewModel.ticketsListLiveData.observe(this, Observer { response ->
            binding.progressBar.visibility = android.view.View.GONE
            
            if (response != null && response.success) {
                val tickets = response.data ?: emptyList()
                
                Log.d("TicketsListActivity", "Total tickets received: ${tickets.size}")
                
                // Update both fragments with tickets data
                viewPagerAdapter.getFragment(0)?.updateTickets(tickets) // Active
                viewPagerAdapter.getFragment(1)?.updateTickets(tickets) // Resolved
                
                // Log counts
                val activeCount = tickets.count { it.status == 0 }
                val resolvedCount = tickets.count { it.status == 1 }
                Log.d("TicketsListActivity", "Active: $activeCount, Resolved: $resolvedCount")
            } else {
                Toast.makeText(this, response?.message ?: "Failed to load tickets", Toast.LENGTH_SHORT).show()
            }
        })

        accountViewModel.ticketsListErrorLiveData.observe(this, Observer { error ->
            binding.progressBar.visibility = android.view.View.GONE
            Toast.makeText(this, error ?: "An error occurred", Toast.LENGTH_SHORT).show()
            Log.e("TicketsListActivity", "Error loading tickets: $error")
        })
    }

    override fun onResume() {
        super.onResume()
        // Reload tickets when activity resumes
        loadTickets()
    }

    private fun onBackPressedBtn() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val messageCameWhenIsAlive = BaseApplication.getInstance()?.messageCameWhenIsAlive ?: 0

                if (messageCameWhenIsAlive == 0) {
                    val intent = Intent(this@TicketsListActivity, MainActivity::class.java).apply {
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

