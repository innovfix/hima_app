package com.gmwapp.hima.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.gmwapp.hima.BaseApplication
import com.gmwapp.hima.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tester log capture, shared via the system share sheet (Slack / WhatsApp).
 *
 * Continuous capture runs from the moment TesterAccess.unlock() is called
 * (7-tap on version label). Normal users: no background process, no disk write.
 * Testers: full session captured from unlock until share — one share at end of
 * day covers everything. Rolling cap = 100 MB (10 × 10 MB files).
 */
object LogCapture {

    private const val DIR = "logs"
    private const val SESSION = "hima_session.log"
    private const val ROTATE_SIZE_KB = 10240  // 10 MB per file
    private const val ROTATE_FILES = 10       // 10 files × 10 MB = 100 MB rolling

    @Volatile
    private var started = false

    fun startSessionLog(context: Context) {
        if (started) return
        started = true
        try {
            val dir = File(context.getExternalFilesDir(null), DIR).apply { mkdirs() }
            val path = File(dir, SESSION).absolutePath
            Thread {
                try {
                    Runtime.getRuntime().exec(
                        arrayOf(
                            "logcat", "-v", "time",
                            "-f", path,
                            "-r", ROTATE_SIZE_KB.toString(),
                            "-n", ROTATE_FILES.toString()
                        )
                    ).waitFor()
                } catch (e: Exception) {
                    Log.e("LogCapture", "session log thread ended", e)
                }
            }.apply { isDaemon = true; name = "hima-logcat" }.start()
        } catch (e: Exception) {
            Log.e("LogCapture", "startSessionLog failed", e)
        }
    }

    fun shareLogs(activity: Activity) {
        Toast.makeText(activity, "Collecting logs…", Toast.LENGTH_SHORT).show()
        Thread {
            val file = buildShareFile(activity)
            activity.runOnUiThread {
                if (file == null || !file.exists() || file.length() == 0L) {
                    Toast.makeText(activity, "Couldn't collect logs on this device", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val uri = FileProvider.getUriForFile(
                    activity, "${activity.packageName}.fileprovider", file
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, file.name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(send, "Share logs"))
            }
        }.start()
    }

    private fun buildShareFile(context: Context): File? {
        return try {
            val dir = File(context.getExternalFilesDir(null), DIR).apply { mkdirs() }
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val uid = BaseApplication.getInstance()?.getPrefs()?.getUserData()?.id ?: 0
            val out = File(dir, "hima_log_u${uid}_${Build.MODEL.replace(" ", "_")}_$ts.txt")

            out.writeText(
                buildString {
                    append("HIMA debug log\n")
                    append("userId=$uid\n")
                    append("device=${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("android=${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})\n")
                    append("build=${BuildConfig.FLAVOR} v${BuildConfig.VERSION_NAME}\n")
                    append("time=$ts\n\n")
                }
            )

            var appended = false
            for (i in ROTATE_FILES downTo 1) {
                val f = File(dir, "$SESSION.$i")
                if (f.exists()) { out.appendText(f.readText()); appended = true }
            }
            File(dir, SESSION).let {
                if (it.exists()) { out.appendText(it.readText()); appended = true }
            }

            if (!appended) {
                val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
                out.appendText(proc.inputStream.bufferedReader().use { it.readText() })
            }

            dir.listFiles { f -> f.name.startsWith("hima_log_u") }
                ?.sortedBy { it.lastModified() }?.dropLast(4)?.forEach { it.delete() }

            out
        } catch (e: Exception) {
            Log.e("LogCapture", "buildShareFile failed", e)
            null
        }
    }
}
