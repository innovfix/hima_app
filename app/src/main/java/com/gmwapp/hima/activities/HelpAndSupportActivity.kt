package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.CategoriesAdapter
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.ActivityHelpAndSupportBinding
import com.gmwapp.hima.retrofit.responses.CategoryData
import com.gmwapp.hima.retrofit.responses.TicketDataResponse
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HelpAndSupportActivity : BaseActivity() {
    lateinit var binding: ActivityHelpAndSupportBinding
    private val accountViewModel: AccountViewModel by viewModels()
    private lateinit var categoriesAdapter: CategoriesAdapter
    private val categoriesList = mutableListOf<CategoryData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpAndSupportBinding.inflate(layoutInflater)
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
        setupRecyclerView()
        loadTicketsAndCategories()
    }

    override fun onResume() {
        super.onResume()
        // Always call ticket list API on resume
        loadTickets()
    }

    private fun initUI() {
        binding.ivBack.setOnSingleClickListener {
            finish()
        }

        // Handle ticket card click
        binding.cvYourTickets.setOnSingleClickListener {
            // Navigate to tickets list activity
            val intent = Intent(this, TicketsListActivity::class.java)
            startActivity(intent)
        }

        // Handle raise ticket card click (for male users)
        binding.cvRaiseTicket.setOnSingleClickListener {
            val intent = Intent(this, SubmitTicketActivity::class.java)
            startActivity(intent)
        }

        // Check user gender and configure UI accordingly
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val gender = userData?.gender

        if (gender?.equals(DConstants.MALE, ignoreCase = true) == true) {
            // Hide category section for male users
            binding.tvSelectCategoryTitle.visibility = android.view.View.GONE
            binding.rvCategories.visibility = android.view.View.GONE
            binding.tvEmpty.visibility = android.view.View.GONE
            
            // Show create ticket section for male users
            binding.tvCreateTicketTitle.visibility = android.view.View.VISIBLE
            binding.cvRaiseTicket.visibility = android.view.View.VISIBLE
        } else {
            // Show category section for female users
            binding.tvSelectCategoryTitle.visibility = android.view.View.VISIBLE
            binding.rvCategories.visibility = android.view.View.VISIBLE
            
            // Hide create ticket section for female users
            binding.tvCreateTicketTitle.visibility = android.view.View.GONE
            binding.cvRaiseTicket.visibility = android.view.View.GONE
        }
    }

    private fun setupRecyclerView() {
        categoriesAdapter = CategoriesAdapter(categoriesList) { category ->
            // Check if it's "Other Queries" - navigate to ticket submission
            if (category.category.equals("Other Queries", ignoreCase = true)) {
                val intent = Intent(this, SubmitTicketActivity::class.java)
                startActivity(intent)
            } else {
                // Regular category - navigate to subqueries
                val intent = Intent(this, SubqueriesActivity::class.java)
                intent.putExtra("category_id", category.id)
                intent.putExtra("category_name", category.category)
                startActivity(intent)
            }
        }
        binding.rvCategories.layoutManager = LinearLayoutManager(this)
        binding.rvCategories.adapter = categoriesAdapter
    }

    private fun loadTickets() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id ?: 0
        
        if (userId > 0) {
            // Load tickets
            accountViewModel.getTicketsList(userId)
        }
    }

    private fun loadTicketsAndCategories() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.rvCategories.visibility = android.view.View.GONE
        binding.tvEmpty.visibility = android.view.View.GONE

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id ?: 0
        val gender = userData?.gender
        
        if (userId > 0) {
            // Load tickets first (for both male and female)
            accountViewModel.getTicketsList(userId)
            
            // Only load categories for female users
            if (gender?.equals(DConstants.MALE, ignoreCase = true) != true) {
                accountViewModel.getCategoriesList(userId)
            } else {
                // For male users, hide progress bar after tickets load
                binding.progressBar.visibility = android.view.View.GONE
            }
        } else {
            binding.progressBar.visibility = android.view.View.GONE
            showEmptyState()
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
        }

        // Observe tickets list
        accountViewModel.ticketsListLiveData.observe(this, Observer { response ->
            // Always show "Your tickets" section
            binding.tvYourTicketsTitle.visibility = android.view.View.VISIBLE
            binding.cvYourTickets.visibility = android.view.View.VISIBLE
            
            // Hide progress bar after tickets are loaded (for male users who don't load categories)
            val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
            val gender = userData?.gender
            if (gender?.equals(DConstants.MALE, ignoreCase = true) == true) {
                binding.progressBar.visibility = android.view.View.GONE
            }
            
            if (response != null && response.success) {
                // Get the data array - this contains multiple tickets, each with a status field
                val tickets = response.data ?: emptyList()
                
                Log.d("HelpAndSupportActivity", "Total tickets received: ${tickets.size}")
                
                // Count active tickets (status = 0 means active, status = 1 means resolved)
                val activeTicketsCount = tickets.count { ticket: TicketDataResponse ->
                    ticket.status == 0
                }
                
                val resolvedTicketsCount = tickets.count { ticket: TicketDataResponse ->
                    ticket.status == 1
                }
                
                Log.d("HelpAndSupportActivity", "Active tickets: $activeTicketsCount, Resolved tickets: $resolvedTicketsCount")
                
                if (activeTicketsCount > 0) {
                    // Show active tickets count
                    val ticketText = if (activeTicketsCount == 1) {
                        "$activeTicketsCount active ticket"
                    } else {
                        "$activeTicketsCount active tickets"
                    }
                    binding.tvActiveTickets.text = ticketText
                    // Set color to green for active tickets
                    binding.tvActiveTickets.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                } else if (resolvedTicketsCount > 0) {
                    // Show resolved tickets if no active tickets but resolved tickets exist
                    val ticketText = if (resolvedTicketsCount == 1) {
                        "$resolvedTicketsCount ticket resolved"
                    } else {
                        "$resolvedTicketsCount tickets resolved"
                    }
                    binding.tvActiveTickets.text = ticketText
                    // Set color to gray for resolved tickets
                    binding.tvActiveTickets.setTextColor(android.graphics.Color.parseColor("#757575"))
                } else {
                    // Show "No ticket raised" when no tickets available
                    binding.tvActiveTickets.text = "No ticket raised"
                    // Set color to gray for no tickets
                    binding.tvActiveTickets.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                }
            } else {
                // Show "No ticket raised" on error or empty response
                binding.tvActiveTickets.text = "No ticket raised"
                binding.tvActiveTickets.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
            }
        })

        accountViewModel.ticketsListErrorLiveData.observe(this, Observer { error ->
            // Always show section even on error, but show "No ticket raised"
            binding.tvYourTicketsTitle.visibility = android.view.View.VISIBLE
            binding.cvYourTickets.visibility = android.view.View.VISIBLE
            binding.tvActiveTickets.text = "No ticket raised"
            binding.tvActiveTickets.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
            Log.e("HelpAndSupportActivity", "Error loading tickets: $error")
        })

        // Observe categories
        accountViewModel.categoriesLiveData.observe(this, Observer { response ->
            binding.progressBar.visibility = android.view.View.GONE
            if (response != null && response.success) {
                response.data?.let { categories ->
                    if (categories.isNotEmpty()) {
                        categoriesList.clear()
                        categoriesList.addAll(categories)
                        
                        // Check if "Other Queries" exists, if not add it manually
                        val hasOtherQueries = categoriesList.any { 
                            it.category.equals("Other Queries", ignoreCase = true) 
                        }
                        if (!hasOtherQueries) {
                            val otherQueriesCategory = CategoryData(
                                id = -1, // Special ID for manually added category
                                category = "Other Queries",
                                createdAt = "",
                                updatedAt = ""
                            )
                            categoriesList.add(otherQueriesCategory)
                        }
                        
                        categoriesAdapter.notifyDataSetChanged()
                        binding.rvCategories.visibility = android.view.View.VISIBLE
                        binding.tvEmpty.visibility = android.view.View.GONE
                    } else {
                        // Even if no categories, show "Other Queries"
                        categoriesList.clear()
                        val otherQueriesCategory = CategoryData(
                            id = -1,
                            category = "Other Queries",
                            createdAt = "",
                            updatedAt = ""
                        )
                        categoriesList.add(otherQueriesCategory)
                        categoriesAdapter.notifyDataSetChanged()
                        binding.rvCategories.visibility = android.view.View.VISIBLE
                        binding.tvEmpty.visibility = android.view.View.GONE
                    }
                } ?: run {
                    // If response is null, still show "Other Queries"
                    categoriesList.clear()
                    val otherQueriesCategory = CategoryData(
                        id = -1,
                        category = "Other Queries",
                        createdAt = "",
                        updatedAt = ""
                    )
                    categoriesList.add(otherQueriesCategory)
                    categoriesAdapter.notifyDataSetChanged()
                    binding.rvCategories.visibility = android.view.View.VISIBLE
                    binding.tvEmpty.visibility = android.view.View.GONE
                }
            } else {
                // On error, still show "Other Queries" as fallback
                categoriesList.clear()
                val otherQueriesCategory = CategoryData(
                    id = -1,
                    category = "Other Queries",
                    createdAt = "",
                    updatedAt = ""
                )
                categoriesList.add(otherQueriesCategory)
                categoriesAdapter.notifyDataSetChanged()
                binding.rvCategories.visibility = android.view.View.VISIBLE
                binding.tvEmpty.visibility = android.view.View.GONE
            }
        })

        accountViewModel.categoriesErrorLiveData.observe(this, Observer { error ->
            binding.progressBar.visibility = android.view.View.GONE
            showEmptyState()
            Toast.makeText(this, error ?: "An error occurred", Toast.LENGTH_SHORT).show()
            Log.e("HelpAndSupportActivity", "Error loading categories: $error")
        })
    }

    private fun showEmptyState() {
        binding.rvCategories.visibility = android.view.View.GONE
        binding.tvEmpty.visibility = android.view.View.VISIBLE
    }
}

