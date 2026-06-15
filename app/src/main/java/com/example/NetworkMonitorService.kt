package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.TrafficStats
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class NetworkMonitorService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var notificationManager: NotificationManager

    private var lastRxBytes = TrafficStats.getTotalRxBytes()
    private var lastTxBytes = TrafficStats.getTotalTxBytes()
    private var lastTime = SystemClock.elapsedRealtime()

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Wait", "Starting...", -1f, 0))
        startMonitoring()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                delay(1000)
                val currentRx = TrafficStats.getTotalRxBytes()
                val currentTx = TrafficStats.getTotalTxBytes()
                val currentTime = SystemClock.elapsedRealtime()

                var rxSpeed = 0f
                var txSpeed = 0f
                if (lastTime > 0) {
                    val durationSec = (currentTime - lastTime) / 1000f
                    if (durationSec > 0) {
                        rxSpeed = (currentRx - lastRxBytes) / durationSec
                        txSpeed = (currentTx - lastTxBytes) / durationSec
                    }
                }

                lastRxBytes = currentRx
                lastTxBytes = currentTx
                lastTime = currentTime

                val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val unitPref = prefs.getString("PREF_UNIT", "BYTES") ?: "BYTES"
                val displayPref = prefs.getString("PREF_DISPLAY", "DOWN") ?: "DOWN"

                val pingResult = measurePing()
                val totalSpeed = rxSpeed + txSpeed
                
                val displayedSpeed = when (displayPref) {
                    "DOWN" -> rxSpeed
                    "UP" -> txSpeed
                    else -> totalSpeed
                }
                
                val speedIconText = formatSpeedIcon(displayedSpeed, unitPref)
                val contentText = formatContentText(rxSpeed, txSpeed, pingResult.first, pingResult.second, unitPref)

                val notification = createNotification(speedIconText, contentText, pingResult.first, pingResult.second)
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun measurePing(): Pair<Float, Int> {
        try {
            val process = Runtime.getRuntime().exec("ping -c 1 -W 1 8.8.8.8")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var latency = -1f
            var packetLoss = 100
            while (reader.readLine().also { line = it } != null) {
                val text = line ?: ""
                if (text.contains("time=")) {
                    val timeStr = text.substringAfter("time=").substringBefore(" ms")
                    latency = timeStr.toFloatOrNull() ?: -1f
                }
                if (text.contains("packet loss")) {
                    val lossStr = text.substringBefore("% packet loss").substringAfterLast(" ")
                    packetLoss = lossStr.toIntOrNull() ?: packetLoss
                }
            }
            process.waitFor()
            return Pair(latency, packetLoss)
        } catch (e: Exception) {
            return Pair(-1f, 100)
        }
    }

    private fun formatSpeedIcon(bytesPerSec: Float, unitPref: String): String {
        return if (unitPref == "BITS") {
            val bits = bytesPerSec * 8
            when {
                bits >= 1000000 -> String.format(Locale.US, "%.1f\nMbps", bits / 1000000)
                bits >= 1000 -> String.format(Locale.US, "%.0f\nkbps", bits / 1000)
                else -> String.format(Locale.US, "%.0f\nbps", bits)
            }
        } else {
            when {
                bytesPerSec >= 1048576 -> String.format(Locale.US, "%.1f\nMB/s", bytesPerSec / 1048576)
                bytesPerSec >= 1024 -> String.format(Locale.US, "%.0f\nKB/s", bytesPerSec / 1024)
                else -> String.format(Locale.US, "%.0f\nB/s", bytesPerSec)
            }
        }
    }

    private fun formatContentText(rx: Float, tx: Float, ping: Float, loss: Int, unitPref: String): String {
        val df = java.text.DecimalFormat("#.##")
        val rxStr: String
        val txStr: String
        if (unitPref == "BITS") {
            val rxBits = rx * 8
            val txBits = tx * 8
            rxStr = when {
                rxBits >= 1000000 -> "${df.format(rxBits / 1000000)} Mbps"
                rxBits >= 1000 -> "${df.format(rxBits / 1000)} kbps"
                else -> "${df.format(rxBits)} bps"
            }
            txStr = when {
                txBits >= 1000000 -> "${df.format(txBits / 1000000)} Mbps"
                txBits >= 1000 -> "${df.format(txBits / 1000)} kbps"
                else -> "${df.format(txBits)} bps"
            }
        } else {
            rxStr = when {
                rx >= 1048576 -> "${df.format(rx / 1048576)} MB/s"
                rx >= 1024 -> "${df.format(rx / 1024)} KB/s"
                else -> "${df.format(rx)} B/s"
            }
            txStr = when {
                tx >= 1048576 -> "${df.format(tx / 1048576)} MB/s"
                tx >= 1024 -> "${df.format(tx / 1024)} KB/s"
                else -> "${df.format(tx)} B/s"
            }
        }
        val pingStr = if (ping >= 0) "${ping}ms" else "Timeout"
        return "▼ $rxStr   ▲ $txStr\nPing: $pingStr | Loss: $loss%"
    }

    private fun createNotification(iconText: String, contentText: String, ping: Float, loss: Int): Notification {
        val stopIntent = Intent(this, NetworkMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(createTextIcon(iconText))
            .setContentTitle("Network Speed")
            .setContentText(contentText)
            .setStyle(Notification.BigTextStyle().bigText(contentText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_delete, "Stop Monitoring", stopPendingIntent)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        builder.setContentIntent(pendingIntent)

        return builder.build()
    }

    private fun createTextIcon(text: String): Icon {
        val config = Bitmap.Config.ARGB_8888
        val bitmap = Bitmap.createBitmap(128, 128, config)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val lines = text.split("\n")
        if (lines.size == 2) {
            paint.textSize = 52f
            canvas.drawText(lines[0], 64f, 56f, paint)
            paint.textSize = if (lines[1].length > 2) 32f else 40f
            canvas.drawText(lines[1], 64f, 106f, paint)
        } else {
            paint.textSize = 52f
            canvas.drawText(text, 64f, 80f, paint)
        }
        return Icon.createWithBitmap(bitmap)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Network Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Real-time network speed monitor"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "network_monitor_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.ACTION_STOP"
    }
}
