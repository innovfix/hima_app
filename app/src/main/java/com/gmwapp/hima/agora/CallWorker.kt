package com.gmwapp.hima.agora

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CallWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val callType = inputData.getString("CALL_TYPE")
        val senderId = inputData.getInt("SENDER_ID", -1)
        val channelName = inputData.getString("CHANNEL_NAME")
        val callId = inputData.getString("CALL_ID")

        val serviceIntent = Intent(applicationContext, FcmCallService::class.java).apply {
            putExtra("CALL_TYPE", callType)
            putExtra("SENDER_ID", senderId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALL_ID", callId)
        }

        applicationContext.startForegroundService(serviceIntent)

        return Result.success()
    }
}
