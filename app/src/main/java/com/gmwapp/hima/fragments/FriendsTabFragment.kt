package com.gmwapp.hima.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.activities.ChatActivity
import com.gmwapp.hima.activities.UserProfileDetailActivity
import com.gmwapp.hima.adapters.FriendsAdapter
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.databinding.FragmentFriendsTabBinding
import com.gmwapp.hima.retrofit.responses.FriendData

class FriendsTabFragment : Fragment() {

    private lateinit var binding: FragmentFriendsTabBinding
    private lateinit var adapter: FriendsAdapter
    private var friendsList = ArrayList<FriendData>()
    private var tabType: Int = TYPE_FRIENDS

    companion object {
        const val TYPE_CHAT = 0
        const val TYPE_FRIENDS = 1
        const val TYPE_MY_REQUESTS = 2
        const val TYPE_THEIR_REQUESTS = 3
        private const val ARG_TYPE = "type"

        fun newInstance(type: Int): FriendsTabFragment {
            val fragment = FriendsTabFragment()
            val args = Bundle()
            args.putInt(ARG_TYPE, type)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabType = arguments?.getInt(ARG_TYPE, TYPE_FRIENDS) ?: TYPE_FRIENDS
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFriendsTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        loadMockData()
    }

    private fun setupRecyclerView() {
        adapter = FriendsAdapter(
            requireActivity(),
            friendsList,
            tabType,
            onChatClick = { friend ->
                // Open chat
                val intent = Intent(requireContext(), ChatActivity::class.java)
                intent.putExtra("USER_ID", friend.friend_id)
                intent.putExtra("USER_NAME", friend.name)
                intent.putExtra("USER_IMAGE", friend.image)
                startActivity(intent)
            },
            onAcceptClick = { friend ->
                // Accept friend request
                Toast.makeText(context, "Accepted ${friend.name}'s request", Toast.LENGTH_SHORT).show()
                // TODO: Call API to accept friend request
                removeFriend(friend)
            },
            onRemoveClick = { friend ->
                // Remove friend or reject request
                val action = when (tabType) {
                    TYPE_THEIR_REQUESTS -> "Rejected"
                    TYPE_MY_REQUESTS -> "Cancelled"
                    else -> "Removed"
                }
                Toast.makeText(context, "$action ${friend.name}", Toast.LENGTH_SHORT).show()
                // TODO: Call API to remove/reject
                removeFriend(friend)
            },
            onProfileClick = { friend ->
                // Open profile detail
                val intent = Intent(requireContext(), UserProfileDetailActivity::class.java)
                intent.putExtra(DConstants.USER_ID, friend.friend_id)
                intent.putExtra("USER_NAME", friend.name)
                intent.putExtra("USER_IMAGE", friend.image)
                intent.putExtra("USER_LANGUAGE", friend.language)
                intent.putExtra("USER_INTERESTS", "")
                intent.putExtra("USER_ABOUT", "")
                intent.putExtra("USER_AGE", 0)
                intent.putExtra("AUDIO_STATUS", friend.audio_status)
                intent.putExtra("VIDEO_STATUS", friend.video_status)
                startActivity(intent)
            }
        )

        binding.rvFriends.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFriends.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadMockData()
        }
    }

    private fun loadMockData() {
        // TODO: Replace with actual API call
        friendsList.clear()

        when (tabType) {
            TYPE_CHAT -> {
                // Mock chat data - All users including friends and recent chats
                friendsList.addAll(
                    listOf(
                        FriendData(
                            id = 1,
                            friend_id = 101,
                            name = "Priya Sharma",
                            image = "https://via.placeholder.com/150",
                            language = "Hindi",
                            audio_status = 1,
                            video_status = 1,
                            is_online = true,
                            last_seen = "Online"
                        ),
                        FriendData(
                            id = 2,
                            friend_id = 102,
                            name = "Ananya Reddy",
                            image = "https://via.placeholder.com/150",
                            language = "Telugu",
                            audio_status = 1,
                            video_status = 0,
                            is_online = false,
                            last_seen = "2 hours ago"
                        ),
                        FriendData(
                            id = 3,
                            friend_id = 103,
                            name = "Sneha Patel",
                            image = "https://via.placeholder.com/150",
                            language = "Gujarati",
                            audio_status = 1,
                            video_status = 1,
                            is_online = true,
                            last_seen = "Online"
                        ),
                        FriendData(
                            id = 8,
                            friend_id = 108,
                            name = "Neha Sharma",
                            image = "https://via.placeholder.com/150",
                            language = "Punjabi",
                            audio_status = 1,
                            video_status = 1,
                            is_online = true,
                            last_seen = "Online"
                        ),
                        FriendData(
                            id = 9,
                            friend_id = 109,
                            name = "Anjali Verma",
                            image = "https://via.placeholder.com/150",
                            language = "Hindi",
                            audio_status = 1,
                            video_status = 0,
                            is_online = false,
                            last_seen = "5 mins ago"
                        )
                    )
                )
            }
            TYPE_FRIENDS -> {
                // Mock friends data
                friendsList.addAll(
                    listOf(
                        FriendData(
                            id = 1,
                            friend_id = 101,
                            name = "Priya Sharma",
                            image = "https://via.placeholder.com/150",
                            language = "Hindi",
                            audio_status = 1,
                            video_status = 1,
                            is_online = true,
                            last_seen = "Online"
                        ),
                        FriendData(
                            id = 2,
                            friend_id = 102,
                            name = "Ananya Reddy",
                            image = "https://via.placeholder.com/150",
                            language = "Telugu",
                            audio_status = 1,
                            video_status = 0,
                            is_online = false,
                            last_seen = "2 hours ago"
                        ),
                        FriendData(
                            id = 3,
                            friend_id = 103,
                            name = "Sneha Patel",
                            image = "https://via.placeholder.com/150",
                            language = "Gujarati",
                            audio_status = 1,
                            video_status = 1,
                            is_online = true,
                            last_seen = "Online"
                        )
                    )
                )
            }
            TYPE_MY_REQUESTS -> {
                // Mock "My Requests" - requests I sent
                friendsList.addAll(
                    listOf(
                        FriendData(
                            id = 6,
                            friend_id = 106,
                            name = "Kavya Nair",
                            image = "https://via.placeholder.com/150",
                            language = "Malayalam",
                            audio_status = 1,
                            video_status = 1,
                            is_online = false,
                            last_seen = "5 hours ago"
                        ),
                        FriendData(
                            id = 7,
                            friend_id = 107,
                            name = "Meera Kapoor",
                            image = "https://via.placeholder.com/150",
                            language = "Hindi",
                            audio_status = 1,
                            video_status = 1,
                            is_online = true,
                            last_seen = "Online"
                        )
                    )
                )
            }
            TYPE_THEIR_REQUESTS -> {
                // Mock "Their Requests" - requests I received
                friendsList.addAll(
                    listOf(
                        FriendData(
                            id = 4,
                            friend_id = 104,
                            name = "Divya Kumar",
                            image = "https://via.placeholder.com/150",
                            language = "Tamil",
                            audio_status = 1,
                            video_status = 1,
                            is_online = false,
                            last_seen = "1 day ago"
                        ),
                        FriendData(
                            id = 5,
                            friend_id = 105,
                            name = "Riya Singh",
                            image = "https://via.placeholder.com/150",
                            language = "Hindi",
                            audio_status = 1,
                            video_status = 0,
                            is_online = false,
                            last_seen = "3 days ago"
                        )
                    )
                )
            }
        }

        adapter.notifyDataSetChanged()
        binding.swipeRefresh.isRefreshing = false

        // Show/hide empty state
        if (friendsList.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvFriends.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvFriends.visibility = View.VISIBLE
        }
    }

    private fun removeFriend(friend: FriendData) {
        friendsList.remove(friend)
        adapter.notifyDataSetChanged()

        // Show empty state if list is empty
        if (friendsList.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvFriends.visibility = View.GONE
        }
    }
}

