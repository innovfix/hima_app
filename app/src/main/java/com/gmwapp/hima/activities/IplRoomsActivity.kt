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
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.ActivityIplRoomsBinding
import com.gmwapp.hima.dialogs.BottomSheetCreateIplRoom
import com.gmwapp.hima.models.IplTeam
import com.gmwapp.hima.viewmodels.IplRoomViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IplRoomsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIplRoomsBinding
    private val viewModel: IplRoomViewModel by viewModels()

    private val userId: Int
        get() = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIplRoomsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        // Hide room list and FAB — female only sees create dialog
        binding.rvRooms.visibility = View.GONE
        binding.llEmptyState.visibility = View.GONE
        binding.fabCreateRoom.visibility = View.GONE

        observeViewModel()

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
            // Save the female creator's team atomically with room creation
            viewModel.createRoom(userId, roomName, teamA.abbreviation, teamB.abbreviation, selectedTeam.abbreviation)
        }
        bottomSheet.show(supportFragmentManager, "CreateIplRoom")
    }

    private fun observeViewModel() {
        viewModel.errorLiveData.observe(this) { error ->
            if (!error.isNullOrEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.createRoomLiveData.observe(this) { response ->
            if (response?.success == true && response.data != null) {
                val roomData = response.data
                // Go directly to call screen
                val intent = Intent(this, IplRoomCallActivity::class.java).apply {
                    putExtra("room_id", roomData.id)
                    putExtra("room_name", roomData.name)
                    putExtra("team_a", roomData.teamA)
                    putExtra("team_b", roomData.teamB)
                    putExtra("invite_code", roomData.inviteCode ?: "")
                }
                startActivity(intent)
                finish() // Close this activity so female can't come back to room list
            }
        }
    }
}
