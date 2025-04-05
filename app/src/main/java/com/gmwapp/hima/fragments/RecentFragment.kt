package com.gmwapp.hima.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.RandomUserActivity
import com.gmwapp.hima.adapters.RecentCallsAdapter
import com.gmwapp.hima.agora.male.MaleCallConnectingActivity
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.FragmentRecentBinding
import com.gmwapp.hima.retrofit.responses.CallsListResponseData
import com.gmwapp.hima.viewmodels.RecentViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RecentFragment : BaseFragment() {
    private lateinit var binding: FragmentRecentBinding
    private val recentViewModel: RecentViewModel by viewModels()
    private lateinit var recentCallsAdapter: RecentCallsAdapter
    private var isLoading = false
    private var offset = 0
    private val limit = 10

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentRecentBinding.inflate(layoutInflater)
        initUI()
        return binding.root
    }

    private fun initUI() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.let { recentViewModel.getCallsList(userData.id, userData.gender, limit, offset) }

        binding.swipeRefreshLayout.setOnRefreshListener {
            offset = 0
           recentCallsAdapter.clearData()  // Clear old data on refresh
            Log.d("offsetCheck","offser= $offset limit= $limit")
            userData?.let { recentViewModel.getCallsList(userData.id, userData.gender,limit, offset) }
        }

        // Initialize adapter with empty data list
        recentCallsAdapter = RecentCallsAdapter(requireActivity(), ArrayList(),
            object : OnItemSelectionListener<CallsListResponseData> {
                override fun onItemSelected(data: CallsListResponseData) {
                    val intent = Intent(context, MaleCallConnectingActivity::class.java)
                    intent.putExtra(DConstants.CALL_TYPE, "audio")
                    intent.putExtra(DConstants.RECEIVER_ID, data.id)
                    intent.putExtra(DConstants.RECEIVER_NAME, data.name)
                    intent.putExtra(DConstants.CALL_ID, 0)
                    intent.putExtra(DConstants.IMAGE, data.image)
                    intent.putExtra(DConstants.IS_RECEIVER_DETAILS_AVAILABLE, true)
                    intent.putExtra(DConstants.TEXT, getString(R.string.wait_user_hint, data.name))
                    startActivity(intent)
                }
            },
            object : OnItemSelectionListener<CallsListResponseData> {
                override fun onItemSelected(data: CallsListResponseData) {
                    val intent = Intent(context, MaleCallConnectingActivity::class.java)
                    intent.putExtra(DConstants.CALL_TYPE, "video")
                    intent.putExtra(DConstants.RECEIVER_ID, data.id)
                    intent.putExtra(DConstants.RECEIVER_NAME, data.name)
                    intent.putExtra(DConstants.CALL_ID, 0)
                    intent.putExtra(DConstants.IMAGE, data.image)
                    intent.putExtra(DConstants.IS_RECEIVER_DETAILS_AVAILABLE, true)
                    intent.putExtra(DConstants.TEXT, getString(R.string.wait_user_hint, data.name))
                    startActivity(intent)
                }
            }
        )

        binding.rvCalls.layoutManager = LinearLayoutManager(requireActivity(), LinearLayoutManager.VERTICAL, false)
        binding.rvCalls.adapter = recentCallsAdapter

        // Add pagination on scroll
        binding.rvCalls.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                if (!isLoading && layoutManager.findLastCompletelyVisibleItemPosition() == recentCallsAdapter.itemCount - 1) {
                    isLoading = true
                    offset += limit  // Increase offset to load next batch
                    userData?.let { recentViewModel.getCallsList(userData.id, userData.gender, limit,offset) }
                }
            }
        })

        recentViewModel.callsListLiveData.observe(viewLifecycleOwner, Observer {
            binding.swipeRefreshLayout.isRefreshing = false
            isLoading = false

            if (it != null && it.success && it.data != null) {
                recentCallsAdapter.addData(it.data)
                binding.rvCalls.visibility = View.VISIBLE
                binding.tlTitle.visibility = View.GONE
            }
        })
    }
}
