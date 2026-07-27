package com.gmwapp.hima.activities

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.TransactionAdapter
import com.gmwapp.hima.databinding.ActivityTransactionsBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.TransactionsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TransactionsActivity : BaseActivity() {
    private lateinit var binding: ActivityTransactionsBinding
    private val transactionsViewModel: TransactionsViewModel by viewModels()
    private lateinit var transactionAdapter: TransactionAdapter

    private var isLoading = false
    private var offset = 0
    private val limit = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        initUI()
    }

    private fun initUI() {
        binding.cvBack.setOnSingleClickListener { finish() }

        // Initialize RecyclerView and Adapter
        transactionAdapter = TransactionAdapter(this, mutableListOf())
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = transactionAdapter

        // BUG #14 — pull-to-refresh
        binding.swipeRefresh.setColorSchemeResources(R.color.pink)
        binding.swipeRefresh.setOnRefreshListener { refreshTransactions() }

        // Load Initial Transactions
        if (isInternetAvailable(this)) {
            loadTransactions()
        } else {
            binding.llNoInternet.visibility = View.VISIBLE
            binding.rvTransactions.visibility = View.GONE
            setLoading(false)
        }

        // Scroll Listener for Pagination
        binding.rvTransactions.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                if (!isLoading && layoutManager.findLastCompletelyVisibleItemPosition() == transactionAdapter.itemCount - 1) {
                    isLoading = true
                    offset += limit // Load next batch
                    loadTransactions()
                }
            }
        })

        // Observe Transactions Data
        transactionsViewModel.transactionsResponseLiveData.observe(this) { response ->
            isLoading = false
            setLoading(false)
            binding.swipeRefresh.isRefreshing = false
            if (response != null && response.success && response.data != null && response.data.isNotEmpty()) {
                // offset==0 → first page or refresh: replace; else append the next page.
                if (offset == 0) transactionAdapter.setTransactions(response.data)
                else transactionAdapter.addTransactions(response.data)
                binding.llNoRecords.visibility = View.GONE
                binding.rvTransactions.visibility = View.VISIBLE
            } else if (offset == 0) {
                // Refresh/initial returned nothing — clear any stale rows and show empty state.
                transactionAdapter.setTransactions(emptyList())
                binding.llNoRecords.visibility = View.VISIBLE
                binding.rvTransactions.visibility = View.GONE
            }
        }
    }

    // BUG #14 — reset to the first page and reload (spinner is the SwipeRefresh's own).
    private fun refreshTransactions() {
        if (!isInternetAvailable(this)) {
            binding.swipeRefresh.isRefreshing = false
            binding.llNoInternet.visibility = View.VISIBLE
            binding.rvTransactions.visibility = View.GONE
            return
        }
        binding.llNoInternet.visibility = View.GONE
        offset = 0
        isLoading = true
        loadTransactions()
    }

    private fun loadTransactions() {
        setLoading(true)
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.let {
            transactionsViewModel.getTransactions(it.id, offset, limit)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        // Suppress the centre spinner during a pull-to-refresh (SwipeRefresh shows its own).
        val shouldShow = isLoading && offset == 0 && !binding.swipeRefresh.isRefreshing
        binding.progressBar.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}
