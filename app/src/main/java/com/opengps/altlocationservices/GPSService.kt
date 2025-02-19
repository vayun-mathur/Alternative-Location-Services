package com.opengps.altlocationservices

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GPSService : Service() {

    private val CHANNEL_ID = "MyForegroundServiceChannel"
    private val NOTIFICATION_ID = 1
    private val UPDATE_INTERVAL = 2*1000L // 5 seconds
    private var serviceJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("MyForegroundService", "onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Starting..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MyForegroundService", "onStartCommand")
        startUpdatingNotification()
        return START_STICKY
    }

    private fun startUpdatingNotification() {
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            var result: Pair<Pair<Double, Double>, Double>? = null
            while (true) {
                try {
                    result = func() // Call your function here
                    withContext(Dispatchers.Main) {
                        updateNotification("Coord: ${result.first}")
                    }
                } catch (e: Exception) {
                    Log.e("MyForegroundService", "Error in func() or updating notification", e)
                    withContext(Dispatchers.Main) {
                        updateNotification("Error: ${e.message}")
                    }
                }
                for(i in 0..60) {
                    if (setMock(result!!.first.first, result.first.second, result.second, this@GPSService)){
                        status.value = "Working"
                    } else {
                        status.value = "Mocking not allowed"
                    }
                    delay(UPDATE_INTERVAL)
                }
            }
        }
    }

    private suspend fun func(): Pair<Pair<Double, Double>, Double> {
        val coords = getCellInfo(this@GPSService)
        // Replace this with your actual function
        Log.d("MyForegroundService", "func() called")
        return coords
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "My Foreground Service Channel",
            NotificationManager.IMPORTANCE_NONE
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AltLocationServices")
            .setContentText(text)
            .setSmallIcon(R.drawable.baseline_notifications_24) // Replace with your icon
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MyForegroundService", "onDestroy")
        serviceJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}