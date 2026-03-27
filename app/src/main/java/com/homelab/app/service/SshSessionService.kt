package com.homelab.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.homelab.app.MainActivity
import com.homelab.app.R
import com.homelab.app.data.repository.SessionRepository
import com.homelab.app.ssh.SshManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SshSessionService : LifecycleService() {

    @Inject lateinit var sshManager: SshManager
    @Inject lateinit var sessionRepository: SessionRepository

    companion object {
        private const val CHANNEL_ID = "ssh_sessions"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.homelab.app.STOP_SESSION"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("SSH sessions active"))

        lifecycleScope.launch {
            sessionRepository.getActiveSessions().collect { sessions ->
                if (sessions.isEmpty()) {
                    stopSelf()
                } else {
                    val notification = buildNotification("${sessions.size} active session(s)")
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            sshManager.disconnectAll()
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sshManager.disconnectAll()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSH Sessions",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background SSH session management"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val resumeIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, SshSessionService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Homelab")
            .setContentText(contentText)
            .setContentIntent(resumeIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect All", stopIntent)
            .setOngoing(true)
            .build()
    }
}
