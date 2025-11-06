package com.gmwapp.hima.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.adapters.TicketsAdapter
import com.gmwapp.hima.databinding.FragmentTicketsTabBinding
import com.gmwapp.hima.retrofit.responses.TicketDataResponse

class TicketsTabFragment : Fragment() {
    private var _binding: FragmentTicketsTabBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var ticketsAdapter: TicketsAdapter
    private val ticketsList = mutableListOf<TicketDataResponse>()
    private var tabType: Int = 0 // 0 for active, 1 for resolved
    private var pendingTickets: List<TicketDataResponse>? = null

    companion object {
        private const val ARG_TAB_TYPE = "tab_type"
        
        fun newInstance(tabType: Int): TicketsTabFragment {
            val fragment = TicketsTabFragment()
            val args = Bundle()
            args.putInt(ARG_TAB_TYPE, tabType)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabType = arguments?.getInt(ARG_TAB_TYPE) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTicketsTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        
        // Update with pending tickets if any
        pendingTickets?.let {
            updateTickets(it)
            pendingTickets = null
        }
    }

    private fun setupRecyclerView() {
        ticketsAdapter = TicketsAdapter(ticketsList) { screenshotUrls ->
            // Open attachment viewer
            showAttachmentViewer(screenshotUrls)
        }
        binding.rvTickets.layoutManager = LinearLayoutManager(context)
        binding.rvTickets.adapter = ticketsAdapter
    }
    
    private fun showAttachmentViewer(screenshotUrls: List<String>) {
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(com.gmwapp.hima.R.layout.dialog_attachments_viewer)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val viewPager = dialog.findViewById<ViewPager2>(com.gmwapp.hima.R.id.view_pager_attachments)
        val btnClose = dialog.findViewById<android.widget.ImageView>(com.gmwapp.hima.R.id.btn_close)
        val tvImageCount = dialog.findViewById<android.widget.TextView>(com.gmwapp.hima.R.id.tv_image_count)
        
        if (viewPager != null && btnClose != null && tvImageCount != null) {
            val adapter = com.gmwapp.hima.adapters.AttachmentViewPagerAdapter(screenshotUrls)
            viewPager.adapter = adapter
            
            tvImageCount.text = "1 / ${screenshotUrls.size}"
            
            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    tvImageCount.text = "${position + 1} / ${screenshotUrls.size}"
                }
            })
            
            btnClose.setOnClickListener {
                dialog.dismiss()
            }
            
            // Also make the dialog dismissible by clicking outside
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            
            dialog.show()
        }
    }

    fun updateTickets(tickets: List<TicketDataResponse>) {
        // Check if view is created
        if (_binding == null) {
            pendingTickets = tickets
            return
        }
        
        ticketsList.clear()
        
        // Filter tickets based on tab type (0 = active, 1 = resolved)
        val filteredTickets = tickets.filter { it.status == tabType }
        
        if (filteredTickets.isEmpty()) {
            showEmptyState()
        } else {
            ticketsList.addAll(filteredTickets)
            ticketsAdapter.notifyDataSetChanged()
            showTickets()
        }
    }

    private fun showEmptyState() {
        binding.rvTickets.visibility = View.GONE
        binding.llEmptyState.visibility = View.VISIBLE
        
        if (tabType == 0) {
            binding.tvEmptyTitle.text = "No Active Tickets"
            binding.tvEmptyMessage.text = "You don't have any active tickets"
        } else {
            binding.tvEmptyTitle.text = "No Resolved Tickets"
            binding.tvEmptyMessage.text = "You don't have any resolved tickets yet"
        }
    }

    private fun showTickets() {
        binding.rvTickets.visibility = View.VISIBLE
        binding.llEmptyState.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

