package com.example.torrentclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*

class TorrentService : Service() {

    companion object {
        private const val CHANNEL_ID = "torrent_downloads_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        TorrentManager.start(applicationContext)

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Torrent engine running"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )

        observeDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        TorrentManager.stop()
        super.onDestroy()
    }

    private fun observeDownloads() {
        val manager = getSystemService(NotificationManager::class.java)
        serviceScope.launch {
            TorrentManager.torrents.collect { items ->
                val activeCount = items.count { !it.isPaused && it.progress < 1f }
                val totalSpeed = items.sumOf { it.downloadRateBytes }
                val summary = if (items.isEmpty()) {
                    "Idle — ready for magnet links"
                } else {
                    "Active: $activeCount | Down: ${formatSpeed(totalSpeed)}"
                }
                manager.notify(NOTIFICATION_ID, buildNotification(summary))
            }
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Torrent Downloader")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Torrent Downloads",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
