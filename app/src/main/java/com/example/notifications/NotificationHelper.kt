package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.NotificationPreferences
import java.time.LocalTime

object NotificationHelper {
    const val CHANNEL_SESSION_COMPLETE = "focus_session_complete"
    const val CHANNEL_REMINDERS = "focus_gentle_reminders"

    private const val ID_SESSION_COMPLETE = 1001
    private const val ID_DAILY_REMINDER = 1002
    private const val ID_WEEKLY_SUMMARY = 1003

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val sessionChannel = NotificationChannel(
            CHANNEL_SESSION_COMPLETE,
            context.getString(R.string.channel_focus_completion_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_focus_completion_desc)
        }
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
        }
        manager.createNotificationChannel(sessionChannel)
        manager.createNotificationChannel(reminderChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isInQuietHours(prefs: NotificationPreferences, nowTime: LocalTime = LocalTime.now()): Boolean {
        if (!prefs.quietHoursEnabled) return false
        val hour = nowTime.hour
        val start = prefs.quietHoursStartHour
        val end = prefs.quietHoursEndHour
        return if (start < end) {
            hour in start until end
        } else {
            hour >= start || hour < end
        }
    }

    fun showSessionCompletedNotification(
        context: Context,
        title: String,
        body: String,
        prefs: NotificationPreferences
    ) {
        if (!prefs.notificationsEnabled || !prefs.sessionCompletionEnabled) return
        if (!hasNotificationPermission(context)) return
        if (isInQuietHours(prefs)) return

        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SESSION_COMPLETE)
            .setSmallIcon(R.drawable.ic_stat_focus_forest)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(ID_SESSION_COMPLETE, notification)
        }
    }

    fun showGentleReminderNotification(
        context: Context,
        title: String,
        body: String,
        prefs: NotificationPreferences,
        isWeekly: Boolean = false
    ) {
        if (!prefs.notificationsEnabled) return
        if (!hasNotificationPermission(context)) return
        if (isInQuietHours(prefs)) return

        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            if (isWeekly) 2 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_focus_forest)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val id = if (isWeekly) ID_WEEKLY_SUMMARY else ID_DAILY_REMINDER
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
