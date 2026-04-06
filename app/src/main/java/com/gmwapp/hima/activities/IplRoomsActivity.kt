package com.gmwapp.hima.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.IplRoomAdapter
import com.gmwapp.hima.callbacks.OnItemSelectionListener
import com.gmwapp.hima.databinding.ActivityIplRoomsBinding
import com.gmwapp.hima.dialogs.BottomSheetCreateIplRoom
import com.gmwapp.hima.models.IplRoom
import com.gmwapp.hima.models.IplTeam
import com.gmwapp.hima.models.RoomMember
import com.gmwapp.hima.viewmodels.IplRoomViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IplRoomsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIplRoomsBinding
    private val viewModel: IplRoomViewModel by viewModels()
    private lateinit var adapter: IplRoomAdapter

    private val userId: Int
        get() = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIplRoomsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        observeViewModel()
        setupClickListeners()
        handleWindowInsets()

        viewModel.getRooms(userId)
        viewModel.getMatchSuggestions()
    }

    private fun setupRecyclerView() {
        adapter = IplRoomAdapter(this, mutableListOf(), object : OnItemSelectionListener<IplRoom> {
            override fun onItemSelected(selectedItemDetails: IplRoom) {
                launchRoomCall(selectedItemDetails)
            }
        })
        binding.rvRooms.layoutManager = LinearLayoutManager(this)
        binding.rvRooms.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.rooms.observe(this) { rooms ->
            if (rooms.isNullOrEmpty()) {
                binding.rvRooms.visibility = View.GONE
                binding.llEmptyState.visibility = View.VISIBLE
            } else {
                binding.rvRooms.visibility = View.VISIBLE
                binding.llEmptyState.visibility = View.GONE
                adapter.updateRooms(rooms)
            }
        }

        viewModel.errorLiveData.observe(this) { error ->
            if (!error.isNullOrEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.createRoomLiveData.observe(this) { response ->
            if (response?.success == true && response.data != null) {
                val roomData = response.data
                val teamA = IplTeam.values().find { it.abbreviation == roomData.teamA } ?: IplTeam.MI
                val teamB = IplTeam.values().find { it.abbreviation == roomData.teamB } ?: IplTeam.CSK
                val room = IplRoom(
                    id = roomData.id,
                    name = roomData.name,
                    teamA = teamA,
                    teamB = teamB,
                    creatorName = roomData.creatorName,
                    members = listOf(RoomMember(id = userId, name = "You", isCreator = true)),
                    maxMembers = roomData.maxMembers,
                    isLive = true
                )
                launchRoomCall(room)
            }
        }
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener { finish() }

        binding.fabCreateRoom.setOnClickListener {
            val suggestions = viewModel.matchSuggestionsLiveData.value ?: emptyList()
            val bottomSheet = BottomSheetCreateIplRoom(suggestions) { roomName, teamA, teamB ->
                viewModel.createRoom(userId, roomName, teamA.abbreviation, teamB.abbreviation)
            }
            bottomSheet.show(supportFragmentManager, "CreateIplRoom")
        }
    }

    private fun handleWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabCreateRoom) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBarInsets.bottom + 24.dpToPx()
            }
            insets
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun launchRoomCall(room: IplRoom) {
        val intent = Intent(this, IplRoomCallActivity::class.java).apply {
            putExtra("room_id", room.id)
            putExtra("room_name", room.name)
            putExtra("team_a", room.teamA.name)
            putExtra("team_b", room.teamB.name)
            putExtra("member_count", room.memberCount)
            putExtra("creator_name", room.creatorName)
        }
        startActivity(intent)
    }
}
