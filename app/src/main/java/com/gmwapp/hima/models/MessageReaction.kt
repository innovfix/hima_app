package com.gmwapp.hima.models

import com.google.gson.annotations.SerializedName

data class MessageReaction(
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("reaction_emoji")
    val reactionEmoji: String
)




