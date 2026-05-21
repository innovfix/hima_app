package com.gmwapp.hima.utils

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.bumptech.glide.Glide
import com.gmwapp.hima.repositories.GiftImageRepository
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.GiftData
import com.gmwapp.hima.retrofit.responses.GiftImageResponse
import retrofit2.Call
import retrofit2.Response

/**
 * Cache of gift catalog (list + icon URLs warmed into Glide's disk cache).
 *
 * B072 — the gift bottom sheet used to fire `fetchGiftImages()` every time it
 * opened, then Glide downloaded every icon URL fresh, so the first open in a
 * session showed a long blank grid. We now prefetch at MainActivity start so
 * the sheet renders from the in-memory cache and Glide just hits its disk
 * cache for thumbnails. Subsequent opens become near-instant.
 */
object GiftManager {

    private const val TAG = "GiftManager"

    @Volatile private var cachedGifts: List<GiftData> = emptyList()
    private val giftMap = mutableMapOf<String, GiftData>()

    /**
     * Observable cache. Bottom sheet observes this — gets cached list
     * immediately if prefetch has already finished, otherwise gets the value
     * as soon as the in-flight prefetch resolves.
     */
    val cachedGiftsLiveData = MutableLiveData<List<GiftData>>()

    @Volatile private var prefetchInFlight = false

    fun updateGifts(giftData: List<GiftData>) {
        cachedGifts = giftData
        giftMap.clear()
        for (gift in giftData) {
            giftMap[gift.gift_icon] = gift
        }
        cachedGiftsLiveData.postValue(giftData)
    }

    fun getCachedGifts(): List<GiftData> = cachedGifts

    fun hasCache(): Boolean = cachedGifts.isNotEmpty()

    fun getGiftIconsWithCoins(): Map<String, GiftData> = giftMap

    /**
     * Idempotent: once we have a cache, repeated calls are no-ops. Otherwise
     * fires the gift-list API and, on success, asks Glide to download every
     * icon URL so the disk cache is warm by the time the user opens a sheet.
     */
    fun prefetch(context: Context, repository: GiftImageRepository) {
        if (cachedGifts.isNotEmpty()) return
        if (prefetchInFlight) return
        prefetchInFlight = true

        val appContext = context.applicationContext
        repository.getGiftImages(object : NetworkCallback<GiftImageResponse> {
            override fun onResponse(
                call: Call<GiftImageResponse>,
                response: Response<GiftImageResponse>
            ) {
                prefetchInFlight = false
                val body = response.body() ?: return
                if (!body.success) return
                val list = body.data
                updateGifts(list)
                warmGlideCache(appContext, list)
            }

            override fun onFailure(call: Call<GiftImageResponse>, t: Throwable) {
                prefetchInFlight = false
                Log.w(TAG, "Gift prefetch failed: ${t.message}")
            }

            override fun onNoNetwork() {
                prefetchInFlight = false
                Log.w(TAG, "Gift prefetch skipped — no network")
            }
        })
    }

    /**
     * downloadOnly populates Glide's disk cache without binding to a view, so
     * later `Glide.with(...).load(url).into(iv)` calls in the bottom-sheet
     * adapter resolve from disk instead of fetching over the network.
     */
    private fun warmGlideCache(context: Context, gifts: List<GiftData>) {
        for (gift in gifts) {
            val url = gift.gift_icon
            if (url.isBlank()) continue
            runCatching {
                Glide.with(context)
                    .downloadOnly()
                    .load(url)
                    .preload()
            }.onFailure {
                Log.w(TAG, "warmGlideCache failed for $url: ${it.message}")
            }
        }
    }
}
