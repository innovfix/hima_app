package com.gmwapp.hima.models

data class IplRoom(
    val id: Int,
    val name: String,
    val teamA: IplTeam,
    val teamB: IplTeam,
    val creatorName: String,
    val members: List<RoomMember>,
    val memberCount: Int = 0,
    val maxMembers: Int = 4,
    val isLive: Boolean = true
)

data class RoomMember(
    val id: Int,
    val name: String,
    val avatarUrl: String = "",
    val isMuted: Boolean = false,
    val isSpeaking: Boolean = false,
    val isCreator: Boolean = false,
    val elapsedMinutes: Int = 0,
    val remainingMinutes: Int = 0
)

enum class IplTeam(
    val teamName: String,
    val abbreviation: String,
    val primaryColor: String,
    val secondaryColor: String
) {
    MI("Mumbai Indians", "MI", "#004BA0", "#D1AB3E"),
    CSK("Chennai Super Kings", "CSK", "#FFC107", "#0081E9"),
    RCB("Royal Challengers Bengaluru", "RCB", "#EC1C24", "#2B2A29"),
    KKR("Kolkata Knight Riders", "KKR", "#3A225D", "#B3A123"),
    DC("Delhi Capitals", "DC", "#004C93", "#EF1B23"),
    SRH("Sunrisers Hyderabad", "SRH", "#FF822A", "#000000"),
    RR("Rajasthan Royals", "RR", "#EA1A85", "#254AA5"),
    PBKS("Punjab Kings", "PBKS", "#DD1F2D", "#A7A9AC"),
    GT("Gujarat Titans", "GT", "#1C1C1C", "#0B4973"),
    LSG("Lucknow Super Giants", "LSG", "#A72056", "#FFCC00")
}
