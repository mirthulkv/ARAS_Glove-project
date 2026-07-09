package com.sos.emergency

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // ─── UI ───────────────────────────────────────────────────────────────────
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button

    // ─── Bluetooth ────────────────────────────────────────────────────────────
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var listenThread: Thread? = null
    private var isListening = false

    // Standard SPP UUID for Bluetooth serial (works with ESP32 AT firmware)
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ─── Location ─────────────────────────────────────────────────────────────
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ─── Config ───────────────────────────────────────────────────────────────
    private val SOS_PHONE_NUMBER = "+91XXXXXXXXXX"   // ← REPLACE with your emergency contact number
    private val ESP32_DEVICE_NAME = "ARAS_GLOVE"    // Must match your ESP32 BT name

    // ─── Handler for background→UI updates ────────────────────────────────────
    private val mainHandler = Handler(Looper.getMainLooper())

    private val REQUEST_ALL_PERMISSIONS = 100

    companion object {
        private const val TAG = "SOSApp"
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus    = findViewById(R.id.tvStatus)
        tvLog       = findViewById(R.id.tvLog)
        btnConnect  = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter

        btnConnect.setOnClickListener    { checkPermissionsAndConnect() }
        btnDisconnect.setOnClickListener { disconnect() }
        btnDisconnect.isEnabled = false

        updateStatus("Idle — press Connect to begin")
        checkPermissionsAndConnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }

    // =========================================================================
    // Permissions
    // =========================================================================

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            @Suppress("DEPRECATION")
            perms.add(Manifest.permission.BLUETOOTH)
            @Suppress("DEPRECATION")
            perms.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        return perms.toTypedArray()
    }

    private fun allPermissionsGranted(): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun checkPermissionsAndConnect() {
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, requiredPermissions(), REQUEST_ALL_PERMISSIONS)
        } else {
            connectToBluetooth()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ALL_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                appendLog("All permissions granted")
                connectToBluetooth()
            } else {
                val denied = permissions.zip(grantResults.toList())
                    .filter { it.second != PackageManager.PERMISSION_GRANTED }
                    .map { it.first.substringAfterLast(".") }
                appendLog("Permissions denied: $denied")
                Toast.makeText(this, "SOS requires all permissions!", Toast.LENGTH_LONG).show()
            }
        }
    }

    // =========================================================================
    // Bluetooth
    // =========================================================================

    @SuppressLint("MissingPermission")
    private fun connectToBluetooth() {
        if (bluetoothAdapter == null) {
            updateStatus("Bluetooth not supported on this device")
            return
        }
        if (!bluetoothAdapter!!.isEnabled) {
            updateStatus("Bluetooth is OFF — please enable it")
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter!!.bondedDevices
        val esp32 = pairedDevices.firstOrNull { it.name == ESP32_DEVICE_NAME }

        if (esp32 == null) {
            updateStatus("ESP32 not paired!\nPair '$ESP32_DEVICE_NAME' in BT settings first.")
            appendLog("Paired devices: ${pairedDevices.map { it.name }}")
            return
        }

        updateStatus("Connecting to ${esp32.name}…")
        appendLog("Found: ${esp32.name} [${esp32.address}]")

        Thread {
            try {
                bluetoothSocket = esp32.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothAdapter!!.cancelDiscovery()
                bluetoothSocket!!.connect()
                inputStream = bluetoothSocket!!.inputStream

                mainHandler.post {
                    updateStatus("✅ Connected to ${esp32.name}\nListening for SOS…")
                    appendLog("Connection established")
                    btnConnect.isEnabled = false
                    btnDisconnect.isEnabled = true
                }
                startListening()

            } catch (e: IOException) {
                Log.e(TAG, "BT connect failed", e)
                mainHandler.post {
                    updateStatus("❌ Connection failed\n${e.message}")
                    appendLog("Connect error: ${e.message}")
                    btnConnect.isEnabled = true
                    btnDisconnect.isEnabled = false
                }
                closeSilently()
            }
        }.start()
    }

    private fun startListening() {
        isListening = true
        listenThread = Thread {
            val buffer = ByteArray(1024)
            val msgBuf = StringBuilder()
            appendLog("Listener thread started")

            while (isListening) {
                try {
                    val bytesRead = inputStream?.read(buffer) ?: break
                    if (bytesRead == -1) {
                        mainHandler.post { appendLog("Stream closed by device") }
                        break
                    }

                    val chunk = String(buffer, 0, bytesRead)
                    Log.d(TAG, "BT chunk: $chunk")
                    msgBuf.append(chunk)

                    if (msgBuf.contains("SOS", ignoreCase = true)) {
                        msgBuf.clear()
                        mainHandler.post {
                            appendLog("🚨 SOS received!")
                            updateStatus("🚨 SOS DETECTED!\nFetching location…")
                            handleSOS()
                        }
                    }

                    // Prevent unbounded growth
                    if (msgBuf.length > 4096) {
                        msgBuf.delete(0, msgBuf.length - 512)
                    }

                } catch (e: IOException) {
                    if (isListening) {
                        Log.e(TAG, "Read error", e)
                        mainHandler.post {
                            appendLog("Read error: ${e.message}")
                            updateStatus("⚠️ Connection lost\nPress Connect to retry")
                            btnConnect.isEnabled = true
                            btnDisconnect.isEnabled = false
                        }
                    }
                    break
                }
            }
            mainHandler.post { appendLog("Listener thread stopped") }
        }
        listenThread!!.start()
    }

    private fun disconnect() {
        isListening = false
        listenThread?.interrupt()
        listenThread = null
        closeSilently()
        updateStatus("Disconnected")
        appendLog("Disconnected")
        btnConnect.isEnabled = true
        btnDisconnect.isEnabled = false
    }

    private fun closeSilently() {
        try { inputStream?.close() } catch (_: IOException) {}
        try { bluetoothSocket?.close() } catch (_: IOException) {}
        inputStream = null
        bluetoothSocket = null
    }

    // =========================================================================
    // SOS Handler
    // =========================================================================

    private fun handleSOS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            appendLog("No location permission — sending without location")
            sendSMS(null)
            return
        }

        val cancelToken = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancelToken.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                appendLog("GPS: ${location.latitude}, ${location.longitude} ±${location.accuracy.toInt()}m")
                updateStatus("📍 Location found\nSending SMS…")
                sendSMS(location)
            } else {
                appendLog("GPS null — trying lastLocation")
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { last -> sendSMS(last) }
                    .addOnFailureListener  { sendSMS(null) }
            }
        }.addOnFailureListener { e ->
            appendLog("Location error: ${e.message}")
            sendSMS(null)
        }
    }

    // =========================================================================
    // SMS
    // =========================================================================

    private fun sendSMS(location: Location?) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            appendLog("No SMS permission")
            updateStatus("⚠️ SMS permission missing")
            return
        }

        val body = if (location != null) {
            val link = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            "EMERGENCY SOS!\nLocation: $link\nAccuracy: ~${location.accuracy.toInt()}m\nPlease respond immediately!"
        } else {
            "EMERGENCY SOS!\nCould not determine location.\nPlease call or track device immediately!"
        }

        try {
            val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val activeSubscriptionInfoList = subscriptionManager.activeSubscriptionInfoList
                // SIM 2 is usually at index 1 or the one with higher simSlotIndex
                val sim2Subscription = activeSubscriptionInfoList?.find { it.simSlotIndex == 1 }
                
                val smsManager: SmsManager = if (sim2Subscription != null) {
                    appendLog("Using SIM 2 (SubID: ${sim2Subscription.subscriptionId})")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        getSystemService(SmsManager::class.java).createForSubscriptionId(sim2Subscription.subscriptionId)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(sim2Subscription.subscriptionId)
                    }
                } else {
                    appendLog("SIM 2 not found, using default SIM")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                }

                val parts = smsManager.divideMessage(body)
                smsManager.sendMultipartTextMessage(SOS_PHONE_NUMBER, null, parts, null, null)

                appendLog("✅ SMS sent to $SOS_PHONE_NUMBER")
                updateStatus("✅ SOS SMS Sent!\nListening for next SOS…")
                Toast.makeText(this, "SOS SMS sent!", Toast.LENGTH_LONG).show()
            } else {
                appendLog("READ_PHONE_STATE permission missing, using default SIM")
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                val parts = smsManager.divideMessage(body)
                smsManager.sendMultipartTextMessage(SOS_PHONE_NUMBER, null, parts, null, null)
            }

        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed", e)
            appendLog("❌ SMS failed: ${e.message}")
            updateStatus("❌ SMS failed: ${e.message}")
        }
    }

    // =========================================================================
    // UI Helpers
    // =========================================================================

    private fun updateStatus(msg: String) {
        mainHandler.post { tvStatus.text = msg }
    }

    private fun appendLog(msg: String) {
        mainHandler.post {
            val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val updated = "[$ts] $msg\n${tvLog.text}"
            tvLog.text = updated.take(3000)
            Log.d(TAG, msg)
        }
    }
}
