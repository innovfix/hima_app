package com.gmwapp.hima.activities

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.FemaleTransactionAdapter
import com.gmwapp.hima.databinding.ActivityTransactionsBinding
import com.gmwapp.hima.utils.setOnSingleClickListener
import com.gmwapp.hima.viewmodels.FemaleTransactionsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FemaleTransactionsActivity : BaseActivity() {
    private lateinit var binding: ActivityTransactionsBinding
    private val femaleTransactionsViewModel: FemaleTransactionsViewModel by viewModels()
    private lateinit var transactionAdapter: FemaleTransactionAdapter

    private var isLoading = false
    private var offset = 0
    private val limit = 10

    // Optional server-side filter. null = normal Earnings; "gift" = Gift Earning.
    // Same screen, same adapter, same API — only this filter and the header differ.
    private var transactionType: String? = null

    companion object {
        private const val TAG = "femaleTrasactionLog"

        /** Intent extra: pass TYPE_GIFT to open this screen in Gift Earning mode. */
        const val EXTRA_TYPE = "extra_transaction_type"
        const val TYPE_GIFT = "gift"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: FemaleTransactionsActivity started")
        binding = ActivityTransactionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = resources.getColor(R.color.pink)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true

        transactionType = intent.getStringExtra(EXTRA_TYPE)

        // FI_02 (creator-side only): activity_transactions.xml is shared with the male
        // TransactionsActivity, so the female header is relabelled here at runtime to
        // "Earnings" while the male screen keeps the XML default "Transactions".
        // Gift Earning reuses the same screen with a "Gift Earning" header.
        binding.tvTransactions.text = if (transactionType == TYPE_GIFT) {
            getString(R.string.gift_earning)
        } else {
            getString(R.string.earnings)
        }
        // BUG #13 — the subtitle is shared with the male screen exactly like the title
        // above, but was never relabelled here, so the XML default had to carry creator
        // wording and the user screen inherited "View your earnings history". The default
        // is now the user wording; the creator copy is restored here, unchanged.
        binding.tvSubtitle.text = "View your earnings history"

        initUI()
    }

    private fun initUI() {
        binding.cvBack.setOnSingleClickListener { finish() }

        // Initialize RecyclerView and Adapter
        transactionAdapter = FemaleTransactionAdapter(this, mutableListOf())
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = transactionAdapter

        // BUG #14 — pull-to-refresh
        binding.swipeRefresh.setColorSchemeResources(R.color.pink)
        binding.swipeRefresh.setOnRefreshListener { refreshTransactions() }

        // Load Initial Transactions
        if (isInternetAvailable(this)) {
            Log.d(TAG, "initUI: Internet available, loading transactions")
            loadTransactions()
        } else {
            Log.e(TAG, "initUI: No internet connection available")
            binding.llNoInternet.visibility = View.VISIBLE
            binding.rvTransactions.visibility = View.GONE
            setLoading(false)
        }

        // Scroll Listener for Pagination
        binding.rvTransactions.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                val itemCount = transactionAdapter.itemCount
                
                Log.d(TAG, "onScrolled: lastVisiblePosition=$lastVisiblePosition, itemCount=$itemCount, isLoading=$isLoading")
                
                if (!isLoading && lastVisiblePosition == itemCount - 1) {
                    Log.d(TAG, "onScrolled: Reached end, loading more transactions")
                    isLoading = true
                    offset += limit // Load next batch
                    Log.d(TAG, "onScrolled: New offset = $offset")
                    loadTransactions()
                }
            }
        })

        // Observe Transactions Data
        femaleTransactionsViewModel.transactionsResponseLiveData.observe(this) { response ->
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

        // Observe Error Data
        femaleTransactionsViewModel.transactionsErrorLiveData.observe(this) { error ->
            isLoading = false
            setLoading(false)
            binding.swipeRefresh.isRefreshing = false
            Log.e(TAG, "onError: Error received = $error")
            if (error != null && transactionAdapter.itemCount == 0) {
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
        BaseApplication.getInstance()?.getPrefs()?.getUserData()?.let { userData ->
            Log.d(TAG, "loadTransactions: Calling API for user_id = ${userData.id}, offset = $offset, limit = $limit, type = $transactionType")
            femaleTransactionsViewModel.getFemaleTransactions(userData.id, offset, limit, transactionType)
        } ?: run {
            Log.e(TAG, "loadTransactions: ERROR - User data is null, cannot load transactions")
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

