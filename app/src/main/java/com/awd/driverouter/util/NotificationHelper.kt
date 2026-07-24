package com.awd.driverouter.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.awd.driverouter.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val TRANSFER_CHANNEL_ID = "transfer_channel"
        const val TRANSFER_NOTIFICATION_ID = 1001
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.transfer_channel_name)
            val descriptionText = context.getString(R.string.transfer_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(TRANSFER_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun getTransferNotificationBuilder(title: String, content: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, TRANSFER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download) // Use system icon for better status bar visibility
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
    }

    fun getBackupNotificationBuilder(title: String, content: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, TRANSFER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
    }

    fun updateProgress(builder: NotificationCompat.Builder, progress: Int, notificationId: Int = TRANSFER_NOTIFICATION_ID) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        builder.setProgress(100, progress, false)
        notificationManager.notify(notificationId, builder.build())
    }

    fun notifyComplete(title: String, notificationId: Int = TRANSFER_NOTIFICATION_ID + 100) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, TRANSFER_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.transfer_complete))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(false)
            .setAutoCancel(true)
        notificationManager.notify(notificationId, builder.build())
    }

    fun notifyFailed(title: String, reason: String?, notificationId: Int = TRANSFER_NOTIFICATION_ID + 200) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, TRANSFER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(reason ?: context.getString(R.string.transfer_failed))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false)
            .setAutoCancel(true)
        notificationManager.notify(notificationId, builder.build())
    }

}
