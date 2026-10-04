package com.kregosh.mtglifetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the app's process in the foreground while the player is in a session, so the
 * connection (and their "online" status) survives switching to another app. It holds no
 * state of its own: the session lives in the view model, and this only shows a notification.
 */
class SessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getStringExtra(EXTRA_CODE).orEmpty()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(code),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        // After the process dies there's no session to keep alive, so don't restart.
        return START_NOT_STICKY
    }

    // Swiping the app away ends the activity and its session, so the service goes too.
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }

    private fun notification(code: String): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.session_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = getString(R.string.session_channel_description) },
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.session_notification_title))
            .setContentText(
                if (code.isNotEmpty()) getString(R.string.session_notification_text, code)
                else getString(R.string.session_notification_text_no_code),
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val CHANNEL_ID      = "session"
        const val NOTIFICATION_ID = 1
        const val EXTRA_CODE      = "code"

        /** Starts the service, or updates its notification with [code]. */
        fun start(context: Context, code: String) {
            // Android 12+ refuses to start one while the app is in the background; the
            // session then simply works as before, without the service.
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, SessionService::class.java).putExtra(EXTRA_CODE, code),
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionService::class.java))
        }
    }
}
