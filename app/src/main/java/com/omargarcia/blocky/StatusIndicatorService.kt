package com.omargarcia.blocky

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.omargarcia.blocky.data.AppDatabase
import com.omargarcia.blocky.utils.DateGroupHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class StatusIndicatorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var countCollectorJob: Job? = null
    private var currentDailyCount: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        createNotificationChannel()
        val initialNotification = createNotification(currentDailyCount)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        observeDailyBlockedCalls()
        
        return START_STICKY
    }

    private fun observeDailyBlockedCalls() {
        countCollectorJob?.cancel()
        countCollectorJob = serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val historyDao = db.blockedCallDao()
            val startOfToday = DateGroupHelper(applicationContext).startOfTodayMillis

            historyDao.getDailyBlockedCount(startOfToday).collect { count ->
                currentDailyCount = count
                val updatedNotification = createNotification(count)
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, updatedNotification)
            }
        }
    }

    private fun createNotification(dailyCount: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val countText = when (dailyCount) {
            0 -> getString(R.string.notification_active_with_count_zero)
            1 -> getString(R.string.notification_active_with_count_one)
            else -> getString(R.string.notification_active_with_count_many, dailyCount)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(countText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Ensures prominent status bar display
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // Updates text smoothly without re-alerting
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        return builder.build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        // Remove old low-importance channels so Android doesn't keep cached silent settings
        try {
            manager.deleteNotificationChannel("blocky_status_channel_v1")
            manager.deleteNotificationChannel("blocky_status_channel_v2")
        } catch (_: Exception) {}

        // Use IMPORTANCE_DEFAULT without sound/vibration so the status bar icon is always visible
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_text)
            setSound(null, null) // Completely silent
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(serviceChannel)
    }

    override fun onDestroy() {
        super.onDestroy()
        countCollectorJob?.cancel()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "blocky_status_channel_v3" // v3 ensures fresh channel with IMPORTANCE_DEFAULT
        private const val NOTIFICATION_ID = 1001
    }
}
