package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName
import com.gmwapp.hima.models.MessageReaction

data class AddReactionResponse(
    val success: Boolean,
    val message: String,
    val data: AddReactionData?
)

data class AddReactionData(
    @SerializedName("message_id")
    val messageId: Int,
    @SerializedName("user_id")
    val userId: Int,
    @SerializedName("reaction_emoji")
    val reactionEmoji: String?,
    @SerializedName("all_reactions")
    val allReactions: List<MessageReaction>?
)


