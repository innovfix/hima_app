package com.gmwapp.hima.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.retrofit.responses.Interests

object Helper {
    private const val TAG = "Helper"

    fun checkNetworkConnection(): Boolean {
        try {
            val connectivityManager: ConnectivityManager = BaseApplication.getInstance()
                ?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            var activeNetwork: NetworkInfo? = null
            activeNetwork = connectivityManager.activeNetworkInfo
            if (activeNetwork != null) {
                return activeNetwork.isConnected
            }
        } catch (e: java.lang.Exception) {
            android.util.Log.e(TAG, "checkNetworkConnection: Exception: " + e.localizedMessage)
        }
        return false
    }

    fun getInterestObject(context: Context, interest: String): Interests {
        return when (interest) {
            "Politics"    -> Interests(context.getString(R.string.politics), R.drawable.politics, false)
            "Art"         -> Interests(context.getString(R.string.art), R.drawable.art, false)
            "Sports"      -> Interests(context.getString(R.string.sports), R.drawable.sports, false)
            "Movies"      -> Interests(context.getString(R.string.movies), R.drawable.movie, false)
            "Music"       -> Interests(context.getString(R.string.music), R.drawable.music, false)
            "Foodie"      -> Interests(context.getString(R.string.foodie), R.drawable.foodie, false)
            "Travel"      -> Interests(context.getString(R.string.travel), R.drawable.travel, false)
            "Photography" -> Interests(context.getString(R.string.photography), R.drawable.photography, false)
            "Love"        -> Interests(context.getString(R.string.love), R.drawable.love, false)
            "Cooking"     -> Interests(context.getString(R.string.cooking), R.drawable.cooking, false)
            // Unknown / newly-added interest from backend: show its real name with a
            // generic icon instead of silently mislabelling it as "Travel".
            else          -> Interests(interest, R.drawable.ic_interests, false)
        }
    }
}