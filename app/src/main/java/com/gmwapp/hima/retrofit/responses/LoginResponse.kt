package com.gmwapp.hima.retrofit.responses

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

data class LoginResponse(
    val success: Boolean,
    val registered: Boolean,
    val message: String,
    val token: String?,
    val usernumber: String?,
    val data: UserData?,
)



data class UserData (
    val id: Int,
    val name: String,
    @SerializedName("user_gender")
    val gender: String,
    val image: String,
    val language: String,
    val interests: String?,
    val mobile: String,
    val avatar_id: Int,
    val datetime: String,
    val updated_at: String,
    val created_at: String,
    val age:Int?,
    val describe_yourself:String?,
    val voice:String?,
    val status:Int?,
    val audio_status:Int?,
    val video_status:Int?,
    val balance:Float?,
    val coins:Int?,
    val bank:String?,
    val account_num:String?,
    val branch:String?,
    val ifsc:String?,
    val holder_name:String?,
    val upi_id:String?,
    val refer_code:String?,
    val referred_by:String?,
    val referral_coins_gained:String?,
    val referral_amount_gained:String?,
    val total_referrals:String?,
    val coins_per_referral:String?,
    val money_per_referral:String?,
    val pancard_name:String?,
    val pancard_number:String?,
    val disclaimer:String?,
    val payment_type:String?,
    @SerializedName("name_changed")
    val name_changed:Int?,
    val star: Int?,
    val ipl_team: String? = null,
    val ipl_rooms_enabled: Int? = 0,
    val dnd_enabled: Int? = 0,
    val dnd_until: String? = null,
    @JsonAdapter(FlexibleBooleanDeserializer::class)
    val play_ludo: Boolean? = false
)

class FlexibleBooleanDeserializer : JsonDeserializer<Boolean> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Boolean {
        if (json == null || json.isJsonNull) return false
        if (!json.isJsonPrimitive) return false
        val primitive = json.asJsonPrimitive
        if (primitive.isBoolean) return primitive.asBoolean
        if (primitive.isNumber) return primitive.asInt != 0
        val str = primitive.asString?.trim().orEmpty()
        return when (str.lowercase()) {
            "true", "1", "yes" -> true
            else -> false
        }
    }
}