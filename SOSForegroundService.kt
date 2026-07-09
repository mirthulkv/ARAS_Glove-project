package com.sos.emergency

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * SOSForegroundService
 *
 * A foreground service that maintains a persistent Bluetooth connection to
 * the ESP32 device even when the app is in the background or the screen is off.
 *
 * HOW TO USE:
 *   Start:  startForegroundService(Intent(this, SOSForegroundService::class.java).apply {
 *               putExtra("device_address", "XX:XX:XX:XX:XX:XX")
 *           })
 *   Stop:   stopService(Intent(this, SOSForegroundService::class.java))
 *
 * The service broadcasts status updates via LocalBroadcastManager using
 * action "com.sos.emergency.STATUS_UPDATE" with string extra "message".
 */
class SOSForegroundService : Service() {

    companion object {
        private const val TAG = "SOSService"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val EMERGENCY_PHONE_NUMBER = "+1234567890"  // ← REPLACE
        private const val CHANNEL_ID = "sos_channel"
        private const val NOTIF_ID = 101
        const val ACTION_STATUS = "com.sos.emergency.STATUS_UPDATE"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
    }

    private var bluetoothSocket: BluetoothSocket? = null
    private var listenThread: ListenThread? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var sosAlreadySent = false

    // ─── Service Lifecycle ────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceAddress = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)

        startForeground(NOTIF_ID, buildNotification("Connecting to ESP32..."))

        if (deviceAddress != null) {
            connectToDevice(deviceAddress)
        } else {
            broadcastStatus("❌ No device address provided")
            stopSelf()
        }

        // START_STICKY: restart service if killed by system
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        listenThread?.cancel()
        try { bluetoothSocket?.close() } catch (e: IOException) { /* ignore */ }
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Bluetooth ────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun connectToDevice(address: String) {
        Thread {
            try {
                val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter: BluetoothAdapter = btManager.adapter
                    ?: run { broadcastStatus("Bluetooth not available"); return@Thread }

                val device: BluetoothDevice = adapter.getRemoteDevice(address)
                adapter.cancelDiscovery()

                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                bluetoothSocket = socket

                broadcastStatus("✅ Connected. Listening for SOS...")
                updateNotification("✅ Connected. Listening for SOS...")

                listenThread = ListenThread(socket.inputStream)
                listenThread?.start()

            } catch (e: IOException) {
                Log.e(TAG, "Connection failed", e)
                broadcastStatus("❌ Connection failed: ${e.message}")
                updateNotification("❌ Connection failed — retrying in 10s")
                // Simple retry after 10 seconds
                Thread.sleep(10_000)
                stopSelf()
            }
        }.start()
    }

    // ─── Listen Thread ────────────────────────────────────────────────────────

    inner class ListenThread(private val inputStream: InputStream) : Thread() {
        @Volatile private var running = true
        private val buffer = StringBuilder()

        override fun run() {
            val bytes = ByteArray(1024)
            while (running) {
                try {
                    val count = inputStream.read(bytes)
                    if (count > 0) {
                        buffer.append(String(bytes, 0, count))
                        if (buffer.contains("SOS", ignoreCase = true)) {
                            buffer.clear()
                            handleSOS()
                        }
                        if (buffer.length > 2048) buffer.clear()
                    }
                } catch (e: IOException) {
                    if (running) {
                        broadcastStatus("⚠️ Connection lost: ${e.message}")
                        updateNotification("⚠️ Connection lost")
                        stopSelf()
                    }
                    break
                }
            }
        }

        fun cancel() { running = false }
    }

    // ─── SOS + Location + SMS ─────────────────────────────────────────────────

    private fun handleSOS() {
        if (sosAlreadySent) return
        sosAlreadySent = true

        broadcastStatus("🚨 SOS DETECTED! Fetching location...")
        updateNotification("🚨 SOS DETECTED! Sending emergency alert...")

        fetchLocationAndSend()

        // Reset debounce after 30 seconds
        Thread.sleep(30_000)
        sosAlreadySent = false
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationAndSend() {
        try {
            val cancel = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
                .addOnSuccessListener { loc -> sendSMS(loc) }
                .addOnFailureListener {
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { loc -> sendSMS(loc) }
                        .addOnFailureListener { sendSMS(null) }
                }
        } catch (e: SecurityException) {
            broadcastStatus("❌ Location permission missing")
            sendSMS(null)
        }
    }

    private fun sendSMS(location: Location?) {
        val message = if (location != null) {
            val url = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            "🚨 SOS EMERGENCY!\nLocation: $url\nCoords: ${location.latitude}, ${location.longitude}\nAccuracy: ±${location.accuracy.toInt()}m"
        } else {
            "🚨 SOS EMERGENCY!\nLocation unavailable — call back immediately!"
        }

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()

            smsManager.sendMultipartTextMessage(
                EMERGENCY_PHONE_NUMBER, null,
                smsManager.divideMessage(message), null, null
            )
            broadcastStatus("✅ Emergency SMS sent!")
            updateNotification("✅ SMS sent. Listening...")
        } catch (e: Exception) {
            broadcastStatus("❌ SMS failed: ${e.message}")
            Log.e(TAG, "SMS error", e)
        }
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "SOS Alert Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Persistent SOS monitoring service"
                setShowBadge(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SOS Emergency Monitor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    // ─── Broadcast ────────────────────────────────────────────────────────────

    private fun broadcastStatus(msg: String) {
        Log.d(TAG, msg)
        val intent = Intent(ACTION_STATUS).apply { putExtra(EXTRA_MESSAGE, msg) }
        sendBroadcast(intent)
    }
}
