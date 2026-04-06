package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

// ===== List Rooms =====
data class IplRoomsListResponse(
    val success: Boolean,
    val message: String?,
    val data: ArrayList<IplRoomData>?
)

// ===== Create Room =====
data class IplRoomCreateResponse(
    val success: Boolean,
    val message: String?,
    val data: IplRoomData?
)

// ===== Join Room =====
data class IplRoomJoinResponse(
    val success: Boolean,
    val message: String?,
    val data: IplRoomJoinData?
)

data class IplRoomJoinData(
    @SerializedName("room_id") val roomId: Int,
    val members: ArrayList<IplMemberData>?
)

// ===== Room Details =====
data class IplRoomDetailResponse(
    val success: Boolean,
    val message: String?,
    val data: IplRoomDetailData?
)

data class IplRoomDetailData(
    val id: Int,
    val name: String,
    @SerializedName("team_a") val teamA: String,
    @SerializedName("team_b") val teamB: String,
    @SerializedName("creator_id") val creatorId: Int,
    @SerializedName("creator_name") val creatorName: String,
    @SerializedName("is_live") val isLive: Boolean,
    val members: ArrayList<IplMemberData>?
)

// ===== Leave Room =====
data class IplRoomLeaveResponse(
    val success: Boolean,
    val message: String?
)

// ===== Reaction =====
data class IplRoomReactionResponse(
    val success: Boolean,
    val message: String?
)

// ===== Mute =====
data class IplRoomMuteResponse(
    val success: Boolean,
    val message: String?
)

// ===== Match Suggestions =====
data class IplMatchSuggestionsResponse(
    val success: Boolean,
    val message: String?,
    val data: ArrayList<String>?
)

// ===== Shared Data Classes =====
data class IplRoomData(
    val id: Int,
    val name: String,
    @SerializedName("team_a") val teamA: String,
    @SerializedName("team_b") val teamB: String,
    @SerializedName("creator_id") val creatorId: Int,
    @SerializedName("creator_name") val creatorName: String,
    @SerializedName("member_count") val memberCount: Int,
    @SerializedName("max_members") val maxMembers: Int,
    @SerializedName("is_live") val isLive: Boolean,
    @SerializedName("created_at") val createdAt: String?
)

data class IplMemberData(
    val id: Int,
    val name: String,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("is_muted") val isMuted: Boolean,
    @SerializedName("is_speaking") val isSpeaking: Boolean?,
    @SerializedName("is_creator") val isCreator: Boolean,
    @SerializedName("elapsed_minutes") val elapsedMinutes: Int?,
    @SerializedName("remaining_minutes") val remainingMinutes: Int?
)
