package com.gmwapp.hima.retrofit.responses

import com.google.gson.annotations.SerializedName

data class StarCreatorSpeechResponse(
    val success: Boolean,
    val message: String,
    val user_id: Int?,
    val audio_speech_text: StarCreatorSpeechText?,
    val video_speech_text: StarCreatorSpeechText?,
    val question_1: StarCreatorQuestion?,
    val question_2: StarCreatorQuestion?
)

data class StarCreatorSpeechText(
    val id: Int,
    val text: String
)

data class StarCreatorQuestion(
    val title: String,
    val subtitle: String,
    val question: String
)
