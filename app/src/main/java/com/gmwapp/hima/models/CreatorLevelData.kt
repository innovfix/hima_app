package com.gmwapp.hima.models

/**
 * Mirrors GET /api/auth/creator/my-level response.
 *
 * All fields can be swapped with real API data when the backend endpoint is ready.
 */
data class CreatorLevelData(
    /** Current level 1–5 */
    val level: Int,
    /** Display name, e.g. "Rising Creator" */
    val levelName: String,
    /** Payout per heart/star in INR, e.g. 1.25 */
    val ratePerStar: Float,
    /** Progress toward next level 0–100 */
    val progressPercent: Int,
    /** Next level name, e.g. "Bright Star" */
    val nextLevelName: String,
    /** Next level number, e.g. 3 */
    val nextLevel: Int,
    /** Calls taken in last 30 days */
    val calls30d: Int,
    /** Active days in last 30 days */
    val activeDays30d: Int,
    /** Revenue in last 30 days (INR) */
    val revenue30d: Float,
    /** Avg user rating last 30d, scale 1.0–3.0 */
    val avgRating30d: Float,
    /** Drop rate % last 30d (lower is better) */
    val dropRate30d: Float,
    /** Total stars collected all-time */
    val totalStars: Int,
    /** Earnings from stars (totalStars × ratePerStar) */
    val totalEarned: Float,
    /** Admin-edited motivational message per level + language (BL-13). */
    val customMessage: String,
    /** True when creator is at max level (L5) */
    val isMaxLevel: Boolean = (level >= 5),
)

/** Dummy data — replace with real API call when backend is ready. */
object CreatorLevelDummy {

    private val ALL_LEVELS = listOf(
        Triple(1, "Newbie",          1.20f),
        Triple(2, "Rising Star",     1.25f),
        Triple(3, "Bright Star",     1.32f),
        Triple(4, "Super Star",      1.45f),
        Triple(5, "Galaxy Star",     1.65f),
    )

    /** Min revenue (last 30d) required to qualify for [level], per spec. */
    fun revenueThresholdFor(level: Int): Float? = when (level) {
        2 -> 500f
        3 -> 2000f
        4 -> 5000f
        5 -> 10000f
        else -> null
    }

    /** Default motivational messages per spec BL-13. Replace with API value when backend ships. */
    fun defaultMessageFor(level: Int): String = when (level) {
        1 -> "Welcome! Make more calls to reach Rising Star and earn higher rate"
        2 -> "Great start. Stay active to climb to Bright Star"
        3 -> "You're doing well. Keep going to reach Super Star"
        4 -> "Almost there — top performers earn the highest rate"
        5 -> "🏆 You're a top creator. Keep up the excellent work"
        else -> ""
    }

    fun get(): CreatorLevelData {
        val currentLevel = 2
        val stars = 847
        val rate = ALL_LEVELS[currentLevel - 1].third
        val nextLvl = ALL_LEVELS.getOrNull(currentLevel) // index = level number
        return CreatorLevelData(
            level           = currentLevel,
            levelName       = ALL_LEVELS[currentLevel - 1].second,
            ratePerStar     = rate,
            progressPercent = 45,
            nextLevelName   = nextLvl?.second ?: "Galaxy Star",
            nextLevel       = nextLvl?.first ?: 5,
            calls30d        = 247,
            activeDays30d   = 18,
            revenue30d      = 2100f,
            avgRating30d    = 1.4f,
            dropRate30d     = 5.0f,
            totalStars      = stars,
            totalEarned     = stars * rate,
            customMessage   = defaultMessageFor(currentLevel),
        )
    }

    fun starsLabel(level: Int): String = "⭐".repeat(level)
}
