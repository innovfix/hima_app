package com.gmwapp.hima.retrofit.responses

import com.google.gson.*
import java.lang.reflect.Type

data class FcmNotificationResponse(
    val success: Boolean,
    val message: String,
    val response: ResponseData?,
    val data_sent: NotificationData?,
    val fcm_token: String?,
    // B10/TC_009: backend ACCEPTED_DEAD_BLOCK replies (HTTP 200) with
    // error="call_already_ended" when the caller has already hung up. Surfaced
    // so the creator's Accept can abort instead of entering a blank, caller-gone
    // call. Nullable + default so the other deserializer branches stay valid.
    val error: String? = null
)

data class ResponseData(
    val name: String
)

data class NotificationData(
    val senderId: String,
    val receiverId: String,
    val callType: String,
    val channelName: String,
    val message: String
)

// ✅ Custom deserializer to handle both STRING and OBJECT responses
class FcmNotificationResponseDeserializer : JsonDeserializer<FcmNotificationResponse> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): FcmNotificationResponse {
        return try {
            when {
                json == null -> {
                    // Null response - return error response
                    FcmNotificationResponse(
                        success = false,
                        message = "Null response received",
                        response = null,
                        data_sent = null,
                        fcm_token = null
                    )
                }
                json.isJsonObject -> {
                    // Normal JSON object response - parse normally
                    val obj = json.asJsonObject
                    FcmNotificationResponse(
                        success = obj.get("success")?.asBoolean ?: false,
                        message = obj.get("message")?.asString ?: "No message",
                        response = if (obj.has("response") && obj.get("response").isJsonObject) {
                            ResponseData(obj.getAsJsonObject("response").get("name")?.asString ?: "")
                        } else null,
                        data_sent = if (obj.has("data_sent") && obj.get("data_sent").isJsonObject) {
                            val dataSent = obj.getAsJsonObject("data_sent")
                            NotificationData(
                                senderId = dataSent.get("senderId")?.asString ?: "",
                                receiverId = dataSent.get("receiverId")?.asString ?: "",
                                callType = dataSent.get("callType")?.asString ?: "",
                                channelName = dataSent.get("channelName")?.asString ?: "",
                                message = dataSent.get("message")?.asString ?: ""
                            )
                        } else null,
                        fcm_token = obj.get("fcm_token")?.asString,
                        error = obj.get("error")?.asString
                    )
                }
                json.isJsonPrimitive -> {
                    // String response - convert to valid response
                    val stringValue = json.asString
                    println("⚠️ WARNING: API returned STRING instead of JSON: $stringValue")
                    FcmNotificationResponse(
                        success = stringValue.contains("success", ignoreCase = true),
                        message = stringValue,
                        response = null,
                        data_sent = null,
                        fcm_token = null
                    )
                }
                else -> {
                    // Unknown type
                    FcmNotificationResponse(
                        success = false,
                        message = "Unknown response format",
                        response = null,
                        data_sent = null,
                        fcm_token = null
                    )
                }
            }
        } catch (e: Exception) {
            println("❌ Error deserializing FCM response: ${e.message}")
            println("📝 Exception type: ${e.javaClass.simpleName}")
            
            // Handle specific Gson exceptions
            when {
                e.message?.contains("was not fully consumed") == true -> {
                    // JSON document has extra content - likely multiple objects or malformed
                    println("⚠️ WARNING: API returned malformed/multiple JSON objects")
                    FcmNotificationResponse(
                        success = true,  // Assume success even if malformed
                        message = "Notification processed (malformed response)",
                        response = null,
                        data_sent = null,
                        fcm_token = null
                    )
                }
                e.message?.contains("Expected BEGIN_OBJECT") == true -> {
                    // String response caught by IOException
                    println("⚠️ WARNING: API returned non-JSON response")
                    FcmNotificationResponse(
                        success = true,
                        message = "Notification processed",
                        response = null,
                        data_sent = null,
                        fcm_token = null
                    )
                }
                else -> {
                    FcmNotificationResponse(
                        success = false,
                        message = "Error parsing response: ${e.message}",
                        response = null,
                        data_sent = null,
                        fcm_token = null
                    )
                }
            }
        }
    }
}
