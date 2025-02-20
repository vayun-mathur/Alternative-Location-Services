package com.opengps.altlocationservices

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GPSService : Service() {

    private val CHANNEL_ID = "Mock Location"
    private val NOTIFICATION_ID = 1
    private var serviceJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("MockLocationService", "onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Starting..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startUpdatingNotification()
        return START_STICKY
    }

    private fun startUpdatingNotification() {
        val locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            var result: LocationValue? = null
            while (true) {
                if (
                    !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                        && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                ) {
                  status.value = "Location disabled"
                  updateNotification(status.value)
                  delay(5000L)
                  continue
                }
                status.value = "Waiting for location"
                try {
                    Log.d("MockLocationService", "getCellInfo() called")
                    result = getCellInfo(this@GPSService)
                    withContext(Dispatchers.Main) {
                        updateNotification("Coord: ${result.lon}, ${result.lat}")
                    }
                } catch (e: Exception) {
                    Log.e("MockLocationService", "Error in getCellInfo() or updating notification", e)
                    withContext(Dispatchers.Main) {
                        when (e) {
                            is IOException, is UnresolvedAddressException -> status.value = "Network error"
                            else -> status.value = "Error: ${e.message}"
                        }
                        updateNotification(status.value)
                    }
                }
                if (result == null) {
                  delay(curTimeout*1000L)
                } else {
                    for(i in 0..(curTimeout/5)) {
                        status.value =
                            if (setMock(result, this@GPSService)) "Working" else "Mocking not allowed"
                        delay(5000L) // 5 seconds
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Mock Location",
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
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MockLocationService", "onDestroy")
        serviceJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}