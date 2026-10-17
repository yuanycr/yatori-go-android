package app.yatori.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import android.util.Log
import mobilecore.Mobilecore
import org.json.JSONArray
import org.json.JSONObject

class YatoriForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        YatoriTime.configure()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            when (intent?.action) {
                ACTION_STOP_ALL -> stopAllTasks()
            }
            // Promote immediately: the core can briefly report no running task
            // while StartTask is still being registered. Returning first would
            // make Android kill the process for not calling startForeground().
            startForegroundSafely(0)
            val running = runningCount()
            if (running <= 0) {
                stopForegroundSafely()
                stopSelfResult(startId)
                START_NOT_STICKY
            } else {
                startForegroundSafely(running)
                START_STICKY
            }
        } catch (t: Throwable) {
            // A malformed core response, denied notification permission, or a
            // platform FGS restriction must not crash the hosting process.
            Log.e(TAG, "Foreground service command failed", t)
            stopForegroundSafely()
            stopSelfResult(startId)
            START_NOT_STICKY
        }
    }

    private fun runningCount(): Int {
        if (!EngineRegistry.initialized) return 0
        return try {
            val raw = Mobilecore.getTaskStatusesJSON()
            val obj = JSONObject(raw)
            val arr = obj.optJSONArray("data") ?: return 0
            var count = 0
            for (i in 0 until arr.length()) {
                if (arr.optJSONObject(i)?.optString("state") == "running") count++
            }
            count
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to read task status", t)
            0
        }
    }

    private fun startForegroundSafely(running: Int) {
        val built = notification(running)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, built, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, built)
        }
    }

    private fun stopForegroundSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to stop foreground service", t)
        }
    }

    private fun notification(running: Int): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Yatori tasks", NotificationManager.IMPORTANCE_LOW))
        }
        val stopIntent = Intent(this, YatoriForegroundService::class.java).setAction(ACTION_STOP_ALL)
        val stopPending = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val openPending = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Yatori 正在运行")
            .setContentText("运行中任务：$running")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止全部", stopPending)
            .setOngoing(true)
            .build()
    }

    private fun stopAllTasks() {
        if (!EngineRegistry.initialized) return
        try {
            val arr: JSONArray = JSONObject(Mobilecore.getTaskStatusesJSON()).optJSONArray("data") ?: return
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                if (item.optString("state") == "running") {
                    try {
                        Mobilecore.stopTask(item.optString("uid"))
                    } catch (t: Throwable) {
                        Log.w(TAG, "Unable to stop task ${item.optString("uid")}", t)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to stop all tasks", t)
        }
    }

    companion object {
        const val ACTION_REFRESH = "app.yatori.android.REFRESH"
        const val ACTION_STOP_ALL = "app.yatori.android.STOP_ALL"
        private const val CHANNEL_ID = "yatori_tasks"
        private const val NOTIFICATION_ID = 42
        private const val TAG = "YatoriAndroid"
    }
}
