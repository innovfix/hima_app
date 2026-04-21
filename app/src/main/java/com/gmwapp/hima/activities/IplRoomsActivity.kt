package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.IplRoomAdapter
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.databinding.ActivityIplRoomsBinding
import com.gmwapp.hima.dialogs.BottomSheetCreateIplRoom
import com.gmwapp.hima.dialogs.BottomSheetJoinIplRoom
import com.gmwapp.hima.models.IplRoom
import com.gmwapp.hima.viewmodels.IplRoomViewModel
import androidx.core.view.WindowInsetsControllerCompat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IplRoomsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIplRoomsBinding
    private val viewModel: IplRoomViewModel by viewModels()

    private val userId: Int
        get() = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0

    private val isFemaleUser: Boolean
        get() = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.gender
            .equals("female", ignoreCase = true)

    private var roomAdapter: IplRoomAdapter? = null
    private var currentOffset = 0
    private var isLoadingMore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIplRoomsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Dark fantasy header extends under status bar via fitsSystemWindows
        // on the LinearLayout banner. LIGHT icons for readability.
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = false

        binding.ivBack.setOnClickListener { finish() }

        if (isFemaleUser) {
            setupFemaleFlow()
        } else {
            setupMaleFlow()
        }
    }

    // ===== FEMALE FLOW (existing behavior) =====

    private fun setupFemaleFlow() {
        // Hide room list and FAB — female only sees create dialog
        binding.rvRooms.visibility = View.GONE
        binding.llEmptyState.visibility = View.GONE
        binding.fabCreateRoom.visibility = View.GONE

        observeFemaleViewModel()

        // Fetch match suggestions then show create dialog
        viewModel.getMatchSuggestions()
        viewModel.matchSuggestionsLiveData.observe(this) { matches ->
            if (matches != null) {
                showCreateRoomDialog()
            }
        }
    }

    private fun showCreateRoomDialog() {
        val suggestions = viewModel.matchSuggestionsLiveData.value ?: emptyList()
        if (suggestions.isEmpty()) {
            Toast.makeText(this, "No matches available today", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val bottomSheet = BottomSheetCreateIplRoom(suggestions) { roomName, teamA, teamB, selectedTeam ->
            viewModel.createRoom(userId, roomName, teamA.abbreviation, teamB.abbreviation, selectedTeam.abbreviation)
        }
        bottomSheet.show(supportFragmentManager, "CreateIplRoom")
    }

    private fun observeFemaleViewModel() {
        viewModel.errorLiveData.observe(this) { error ->
            if (!error.isNullOrEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.createRoomLiveData.observe(this) { response ->
            if (response?.success == true && response.data != null) {
                val roomData = response.data
                val intent = Intent(this, IplRoomCallActivity::class.java).apply {
                    putExtra("room_id", roomData.id)
                    putExtra("room_name", roomData.name)
                    putExtra("team_a", roomData.teamA)
                    putExtra("team_b", roomData.teamB)
                    putExtra("invite_code", roomData.inviteCode ?: "")
                }
                startActivity(intent)
                finish()
            }
        }
    }

    // ===== MALE FLOW (new behavior) =====

    private fun setupMaleFlow() {
        // Show room list, hide FAB (males can't create rooms)
        binding.rvRooms.visibility = View.VISIBLE
        binding.fabCreateRoom.visibility = View.GONE

        // Set empty state text for males
        binding.tvEmptyTitle.text = "No Live Rooms"
        binding.tvEmptySubtitle.text = "No rooms available right now"

        // Set up "Join by Code" and "Join Random" via the FAB area
        // Repurpose the FAB as "Join by Code / Random" button
        binding.fabCreateRoom.visibility = View.VISIBLE
        binding.fabCreateRoom.text = "Join by Code / Random"
        binding.fabCreateRoom.icon = null
        binding.fabCreateRoom.setOnClickListener {
            val bottomSheet = BottomSheetJoinIplRoom()
            bottomSheet.show(supportFragmentManager, "JoinIplRoom")
        }

        // Set up room adapter
        roomAdapter = IplRoomAdapter(this, mutableListOf(), object : OnItemSelectionListener<IplRoom> {
            override fun onItemSelected(room: IplRoom) {
                // Open room as listener
                val intent = Intent(this@IplRoomsActivity, IplRoomCallActivity::class.java).apply {
                    putExtra("room_id", room.id)
                    putExtra("room_name", room.name)
                    putExtra("team_a", room.teamA.abbreviation)
                    putExtra("team_b", room.teamB.abbreviation)
                    putExtra("is_listener", true)
                }
                startActivity(intent)
            }
        })
        binding.rvRooms.adapter = roomAdapter

        // Scroll listener for pagination
        binding.rvRooms.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                if (!isLoadingMore && lastVisibleItem >= totalItemCount - 3) {
                    if (viewModel.hasMoreRooms.value == true) {
                        loadMoreRooms()
                    }
                }
            }
        })

        observeMaleViewModel()
        loadRooms()
    }

    private fun loadRooms() {
        currentOffset = 0
        val language = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.language
        viewModel.getRooms(userId, language, 0)
    }

    private fun loadMoreRooms() {
        isLoadingMore = true
        currentOffset += 10
        val language = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.language
        viewModel.getRooms(userId, language, currentOffset)
    }

    private fun observeMaleViewModel() {
        viewModel.errorLiveData.observe(this) { error ->
            if (!error.isNullOrEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            if (loading == false) isLoadingMore = false
        }

        viewModel.rooms.observe(this) { rooms ->
            if (rooms.isNullOrEmpty()) {
                binding.rvRooms.visibility = View.GONE
                binding.llEmptyState.visibility = View.VISIBLE
            } else {
                binding.rvRooms.visibility = View.VISIBLE
                binding.llEmptyState.visibility = View.GONE
                roomAdapter?.updateRooms(rooms)
                binding.tvLiveRoomsCount.text = "${rooms.size} Live Rooms"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh rooms when returning from a call
        if (!isFemaleUser) {
            loadRooms()
        }
    }
}
