package com.sos.emergency

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * SosService - Optional Foreground Service
 *
 * Benefits of running as a foreground service:
 *  - Survives screen off, app backgrounding, and low-memory kills
 *  - Shows a persistent notification so the user knows SOS is active
 *  - Android guarantees it won't be killed while in foreground state
 *
 * HOW TO USE:
 *  1. Move your Bluetooth + SOS logic here from MainActivity
 *  2. Start with: startForegroundService(Intent(this, SosService::class.java))
 *  3. Stop with:  stopService(Intent(this, SosService::class.java))
 *
 * This stub sets up the notification channel and foreground binding.
 * Wire in BluetoothSocket + FusedLocationProviderClient the same way as MainActivity.
 */
class SosService : Service() {

    companion object {
        private const val CHANNEL_ID    = "sos_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.sos.emergency.STOP_SERVICE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // TODO: Initialize BluetoothSocket connection here
        // TODO: Start the listening thread (same logic as MainActivity.startListening)
        // TODO: On SOS detection, call handleSOS() and sendSMS() from here

        // START_STICKY — system will restart the service if killed
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: Close BluetoothSocket and InputStream here
    }

    // ─── Notification Helpers ─────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SOS Monitor",
                NotificationManager.IMPORTANCE_LOW  // LOW = no sound, just persistent icon
            ).apply {
                description = "Monitoring Bluetooth for SOS signals"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, SosService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SOS Monitor Active")
            .setContentText("Listening for emergency signals from ESP32")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPending)
            .setOngoing(true)
            .build()
    }
}
