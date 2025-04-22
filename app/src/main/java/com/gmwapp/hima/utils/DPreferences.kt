package com.gmwapp.hima.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.gmwapp.hima.retrofit.responses.SettingsResponseData
import com.gmwapp.hima.retrofit.responses.UserData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class DPreferences(context: Context) {
    private val mPrefsRead: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mPrefsWrite: SharedPreferences.Editor = mPrefsRead.edit()

    fun setUserData(userData: UserData?) {
        try {
            mPrefsWrite.putString(
                USER_DATA, Gson().toJson(userData)
            )
            mPrefsWrite.apply()
        } catch (e: Exception) {
            e.message?.let { Log.e("Dpreferences", it) }
        }
    }

    fun clearUserData() {
        try {
            mPrefsWrite.clear()
            mPrefsWrite.apply()
        } catch (e: Exception) {
            e.message?.let { Log.e("Dpreferences", it) }
        }
    }

    fun getUserData(): UserData? {
        try {
            return Gson().fromJson(mPrefsRead.getString(USER_DATA, ""), UserData::class.java)
        } catch (e: Exception) {
            return null
        }
    }

    fun setSettingsData(settingsData: SettingsResponseData) {
        try {
            mPrefsWrite.putString(
                SETTINGS_DATA, Gson().toJson(settingsData)
            )
            mPrefsWrite.apply()
        } catch (e: Exception) {
            e.message?.let { Log.e("Dpreferences", it) }
        }
    }

    fun getSettingsData(): SettingsResponseData? {
        try {
            return Gson().fromJson(mPrefsRead.getString(SETTINGS_DATA, ""), SettingsResponseData::class.java)
        } catch (e: Exception) {
            return null
        }
    }

    fun setAfterAddCoins(coins: String) {
        try {
            mPrefsWrite.putString("after_add_coins", coins)
            mPrefsWrite.apply()
        } catch (e: Exception) {
            e.message?.let { Log.e("DPreferences", it) }
        }
    }

    fun getAfterAddCoins(): String {
        return mPrefsRead.getString("after_add_coins", "0") ?: "0"
    }

    fun getSelectedOrderId(): String {
        return mPrefsRead.getString("selected_order_id", "0") ?: "0"
    }

    fun clearSelectedOrderId() {
        try {
            mPrefsWrite.remove("selected_user_id")
            mPrefsWrite.remove("selected_plan_id")
            mPrefsWrite.remove("selected_order_id")
            mPrefsWrite.apply()
        } catch (e: Exception) {
            e.message?.let { Log.e("DPreferences", it) }
        }
    }

    fun setSelectedOrderId(orderId: String) {
        try {
            mPrefsWrite.putString("selected_order_id", orderId)
            mPrefsWrite.apply()
        } catch (e: Exception) {
            e.message?.let { Log.e("DPreferences", it) }
        }
    }

    fun setSelectedUserId(userId: String) {
        try {
            mPrefsWrite.putString("selected_user_id", userId)
            mPrefsWrite.apply()
        } catch (e: Exception) {
            e.message?.let { Log.e("DPreferences", it) }
        }
    }

    fun getSelectedUserId(): String {
        return mPrefsRead.getString("selected_user_id", "0") ?: "0"
    }

    fun setSelectedPlanId(planId: String) {
        try {
            mPrefsWrite.putString("selected_plan_id", planId)
            mPrefsWrite.apply()
        } catch (e: Exception) {
            e.message?.let { Log.e("DPreferences", it) }
        }
    }

    fun getSelectedPlanId(): String {
        return mPrefsRead.getString("selected_plan_id", "0") ?: "0"
    }


    fun setSkuList(skuList: List<String>) {
        try {
            mPrefsWrite.putString("sku_list", Gson().toJson(skuList))
            mPrefsWrite.apply()
        } catch (e: Exception) {
            e.message?.let { Log.e("DPreferences", it) }
        }
    }

    fun getSkuList(): List<String> {
        return try {
            val json = mPrefsRead.getString("sku_list", null)
            if (!json.isNullOrEmpty()) {
                val type = object : TypeToken<List<String>>() {}.type
                Gson().fromJson(json, type)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("DPreferences", "Error reading sku list: ${e.message}")
            emptyList()
        }
    }



    fun setAuthenticationToken(authenticationToken: String?) {
        try {
            mPrefsWrite.putString(
                AUTHENTICATION_TOKEN, authenticationToken
            )
            mPrefsWrite.apply()
        } catch (e: Exception) {
            e.message?.let { Log.e("Dpreferences", it) }
        }
    }

    fun getAuthenticationToken(): String? {
        try {
            return mPrefsRead.getString(AUTHENTICATION_TOKEN, "")
        } catch (e: Exception) {
            return null
        }
    }


    // Save a processed order ID to the list
    fun markOrderAsProcessed(orderId: String) {
        try {
            val usedOrders = mPrefsRead.getStringSet("used_order_ids", mutableSetOf())?.toMutableSet()
                ?: mutableSetOf()
            usedOrders.add(orderId)
            mPrefsWrite.putStringSet("used_order_ids", usedOrders)
            mPrefsWrite.apply()
        } catch (e: Exception) {
            e.message?.let { Log.e("DPreferences", it) }
        }
    }

    // Check if an order ID is already processed
    fun isOrderAlreadyProcessed(orderId: String): Boolean {
        return try {
            val usedOrders = mPrefsRead.getStringSet("used_order_ids", mutableSetOf())
            usedOrders?.contains(orderId) ?: false
        } catch (e: Exception) {
            Log.e("DPreferences", "Error checking order ID: ${e.message}")
            false
        }
    }

    // Function to get all processed order IDs
    fun getAllProcessedOrderIds(): Set<String> {
        return try {
            // Retrieve the set of processed order IDs
            mPrefsRead.getStringSet("used_order_ids", mutableSetOf()) ?: mutableSetOf()
        } catch (e: Exception) {
            Log.e("DPreferences", "Error retrieving processed order IDs: ${e.message}")
            mutableSetOf()
        }
    }

    fun setReferralCode(code: String) {
        try {
            mPrefsWrite.putString("referral_code", code)
            mPrefsWrite.apply()
        } catch (e: Exception) {
            e.message?.let { Log.e("DPreferences", it) }
        }
    }

    fun getReferralCode(): String {
        return mPrefsRead.getString("referral_code", "") ?: ""
    }





    companion object {
        private const val AUTHENTICATION_TOKEN: String = "authentication_token"
        private const val SETTINGS_DATA: String = "settings_data"
        private const val USER_DATA = "user_data"
        private const val PREFS = "Hima"
    }
}