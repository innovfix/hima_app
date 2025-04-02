package com.gmwapp.hima.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.gmwapp.hima.retrofit.responses.SettingsResponseData
import com.gmwapp.hima.retrofit.responses.UserData
import com.google.gson.Gson


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

    companion object {
        private const val AUTHENTICATION_TOKEN: String = "authentication_token"
        private const val SETTINGS_DATA: String = "settings_data"
        private const val USER_DATA = "user_data"
        private const val PREFS = "Hima"
    }
}