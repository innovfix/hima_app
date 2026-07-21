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
        ticketsAdapter = TicketsAdapter(ticketsList) { ticket ->
            // Open the full ticket detail page (user issue + reply, both attachments)
            val intent = android.content.Intent(requireContext(), com.gmwapp.hima.activities.TicketDetailActivity::class.java)
            intent.putExtra(com.gmwapp.hima.activities.TicketDetailActivity.EXTRA_TICKET, ticket)
            startActivity(intent)
        }
        binding.rvTickets.layoutManager = LinearLayoutManager(context)
        binding.rvTickets.adapter = ticketsAdapter
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

