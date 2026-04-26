package com.gmwapp.hima.activities

import com.gmwapp.hima.utils.showAppToast

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.SubqueriesAdapter
import com.gmwapp.hima.databinding.ActivitySubqueriesBinding
import com.gmwapp.hima.retrofit.responses.SubqueryData
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SubqueriesActivity : BaseActivity() {
    lateinit var binding: ActivitySubqueriesBinding
    private val accountViewModel: AccountViewModel by viewModels()
    private lateinit var subqueriesAdapter: SubqueriesAdapter
    private val subqueriesList = mutableListOf<SubqueryData>()
    private var categoryId: Int = -1
    private var categoryName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubqueriesBinding.inflate(layoutInflater)
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
        
        categoryId = intent.getIntExtra("category_id", -1)
        categoryName = intent.getStringExtra("category_name") ?: ""
        
        if (categoryId == -1) {
            showAppToast("Invalid category", Toast.LENGTH_SHORT)
            finish()
            return
        }
        
        initUI()
        setupRecyclerView()
        setupObservers()
        loadSubqueries()
    }

    private fun setupObservers() {
        // Observer registration moved out of loadSubqueries() so re-fetching
        // (e.g. from a future pull-to-refresh) doesn't stack extra observers.
        accountViewModel.subqueriesLiveData.observe(this, Observer { response ->
            binding.progressBar.visibility = android.view.View.GONE
            if (response != null && response.success) {
                response.data?.let { subqueries ->
                    if (subqueries.isNotEmpty()) {
                        subqueriesList.clear()
                        subqueriesList.addAll(subqueries)
                        @Suppress("NotifyDataSetChanged") subqueriesAdapter.notifyDataSetChanged()
                        binding.rvSubqueries.visibility = android.view.View.VISIBLE
                        binding.tvSectionTitle.visibility = android.view.View.VISIBLE
                        binding.llEmptyState.visibility = android.view.View.GONE
                    } else {
                        showEmptyState()
                    }
                } ?: run {
                    showEmptyState()
                }
            } else {
                showEmptyState()
            }
        })

        accountViewModel.subqueriesErrorLiveData.observe(this, Observer { error ->
            binding.progressBar.visibility = android.view.View.GONE
            showEmptyState()
            Log.e("SubqueriesActivity", "Error loading subqueries: $error")
        })
    }

    private fun initUI() {
        binding.cvBack.setOnSingleClickListener {
            finish()
        }
        binding.tvTitle.text = categoryName.ifEmpty { "Subqueries" }
    }

    private fun setupRecyclerView() {
        subqueriesAdapter = SubqueriesAdapter(subqueriesList) { subquery ->
            val intent = Intent(this, SubqueryDetailActivity::class.java)
            // Parcelable overload chosen automatically — no manual cast required.
            intent.putExtra("subquery", subquery)
            startActivity(intent)
        }
        binding.rvSubqueries.layoutManager = LinearLayoutManager(this)
        binding.rvSubqueries.adapter = subqueriesAdapter
    }

    private fun loadSubqueries() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.rvSubqueries.visibility = android.view.View.GONE
        binding.llEmptyState.visibility = android.view.View.GONE
        binding.tvSectionTitle.visibility = android.view.View.GONE

        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        val userId = userData?.id ?: 0
        val language = userData?.language.orEmpty()

        if (userId > 0) {
            accountViewModel.getSubqueriesList(userId, categoryId, language)
        } else {
            binding.progressBar.visibility = android.view.View.GONE
            showEmptyState()
            showAppToast("User not logged in", Toast.LENGTH_SHORT)
        }
    }

    private fun showEmptyState() {
        binding.rvSubqueries.visibility = android.view.View.GONE
        binding.tvSectionTitle.visibility = android.view.View.GONE
        binding.llEmptyState.visibility = android.view.View.VISIBLE
    }
}

