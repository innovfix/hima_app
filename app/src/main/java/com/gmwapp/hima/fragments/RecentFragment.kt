package com.gmwapp.hima.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.activities.RandomUserActivity
import com.gmwapp.hima.adapters.CoinAdapter
import com.gmwapp.hima.adapters.FemaleUserAdapter
import com.gmwapp.hima.adapters.RecentCallsAdapter
import com.gmwapp.hima.agora.male.MaleAudioCallingActivity
import com.gmwapp.hima.agora.male.MaleCallConnectingActivity
import com.gmwapp.hima.agora.male.MaleVideoCallingActivity
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.FragmentRecentBinding
import com.gmwapp.hima.retrofit.responses.CallsListResponseData
import com.gmwapp.hima.retrofit.responses.CoinsResponseData
import com.gmwapp.hima.retrofit.responses.FemaleUsersResponseData
import com.gmwapp.hima.viewmodels.AccountViewModel
import com.gmwapp.hima.viewmodels.RecentViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RecentFragment : BaseFragment() {
    lateinit var binding: FragmentRecentBinding
    private val recentViewModel: RecentViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentRecentBinding.inflate(layoutInflater)

        initUI()
        return binding.root
    }

    private fun initUI() {
        val userData = BaseApplication.getInstance()?.getPrefs()?.getUserData()
        userData?.let { recentViewModel.getCallsList(userData.id, userData.gender) }

        binding.swipeRefreshLayout.setOnRefreshListener {
            // Start refreshing the data
            userData?.let { recentViewModel.getCallsList(userData.id, userData.gender) }
        }




        recentViewModel.callsListLiveData.observe(viewLifecycleOwner, Observer{
            if(it!=null && it.success && it.data!=null) {
                binding.tlTitle.visibility = View.GONE
            }
            else {
                binding.tlTitle.visibility = View.VISIBLE
            }
        })

        recentViewModel.callsListLiveData.observe(viewLifecycleOwner, Observer {

            binding.swipeRefreshLayout.isRefreshing = false

            if(it!=null && it.success && it.data!=null) {
                binding.rvCalls.setLayoutManager(
                    LinearLayoutManager(
                        requireActivity(), LinearLayoutManager.VERTICAL, false
                    )
                )
              //  val recentCallsAdapter = RecentCallsAdapter(requireActivity(),it.data)



                var recentCallsAdapter = activity?.let { it1 ->
                    RecentCallsAdapter(it1,
                        it.data,
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
                        })
                }


                binding.rvCalls.setAdapter(recentCallsAdapter)
                binding.rvCalls.visibility = View.VISIBLE
                binding.tlTitle.visibility = View.GONE

            }
        })
    }
}