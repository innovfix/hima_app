package com.gmwapp.hima.activities

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.R
import com.gmwapp.hima.adapters.CreatorNotificationAdapter
import com.gmwapp.hima.constants.DConstants
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.ListCreatorOnlineNotificationsResponse
import com.gmwapp.hima.utils.NotifyOnlinePrefsHelper
import com.gmwapp.hima.utils.setOnSingleClickListener
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Response
import javax.inject.Inject

@AndroidEntryPoint
class ManageNotificationsActivity : AppCompatActivity() {

    @Inject
    lateinit var apiManager: ApiManager

    private lateinit var creatorAdapter: CreatorNotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = BaseApplication.getInstance()?.getPrefs()
        if (prefs?.getUserData()?.gender?.equals(DConstants.MALE, ignoreCase = true) != true) {
            finish()
            return
        }

        setContentView(R.layout.activity_manage_notifications)
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        WindowInsetsControllerCompat(window, findViewById(android.R.id.content)).isAppearanceLightStatusBars =
            true

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<TextView>(R.id.tv_flow_title).text = getString(R.string.manage_notifications_title)
        findViewById<CardView>(R.id.cv_back).setOnSingleClickListener { finish() }

        val maleId = prefs.getUserData()?.id ?: 0
        if (maleId == 0) {
            finish()
            return
        }

        val rv = findViewById<RecyclerView>(R.id.rv_creator_notifications)
        rv.layoutManager = LinearLayoutManager(this)
        val list = NotifyOnlinePrefsHelper.loadSubscribedCreators(this).toMutableList()
        creatorAdapter = CreatorNotificationAdapter(this, apiManager, maleId, list) {
            loadCreatorsFromApi()
        }
        rv.adapter = creatorAdapter
        applyCreatorList(list)
        loadCreatorsFromApi()
    }

    private fun applyCreatorList(mapped: List<NotifyOnlinePrefsHelper.SubscribedCreator>) {
        if (!::creatorAdapter.isInitialized) return
        creatorAdapter.replaceItems(mapped.toMutableList())
        val empty = findViewById<View>(R.id.tv_empty_creators)
        empty.visibility = if (mapped.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadCreatorsFromApi() {
        val maleId = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: return
        apiManager.listCreatorOnlineNotifications(
            userId = maleId,
            callback = object : NetworkCallback<ListCreatorOnlineNotificationsResponse> {
                override fun onResponse(
                    call: Call<ListCreatorOnlineNotificationsResponse>,
                    response: Response<ListCreatorOnlineNotificationsResponse>
                ) {
                    val body = response.body()
                    val rows = body?.data?.creators.orEmpty()
                    if (!response.isSuccessful || body?.success != true) return
                    val mapped = rows.map { row ->
                        NotifyOnlinePrefsHelper.SubscribedCreator(
                            id = row.creatorId,
                            displayName = row.user?.name?.takeIf { it.isNotBlank() } ?: "User",
                            imageUrl = row.user?.image,
                            triggerReason = row.triggerReason,
                            isOnline = (row.isOnline ?: 0) == 1,
                            subscribedFromApi = true
                        )
                    }
                    runOnUiThread {
                        applyCreatorList(mapped)
                    }
                }

                override fun onFailure(
                    call: Call<ListCreatorOnlineNotificationsResponse>,
                    t: Throwable
                ) {
                    // Keep local-cache list already on screen
                }

                override fun onNoNetwork() {
                    // Keep local-cache list already on screen
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (::creatorAdapter.isInitialized) {
            loadCreatorsFromApi()
        }
    }
}
