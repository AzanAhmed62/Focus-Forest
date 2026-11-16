package com.example.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.core.time.SystemClockProvider
import com.example.data.local.datastore.UserPreferencesDataStore
import com.example.data.local.db.FocusForestDatabase
import com.example.domain.model.NotificationPreferences
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class SessionCompletionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = UserPreferencesDataStore(applicationContext).notificationPreferences.first()
        val sessionName = inputData.getString(KEY_SESSION_NAME) ?: "Focus"
        val minutes = inputData.getInt(KEY_MINUTES, 25)
        NotificationHelper.showSessionCompletedNotification(
            context = applicationContext,
            title = "$sessionName complete",
            body = "You finished $minutes minutes. Open Focus Forest to plant your seeds.",
            prefs = prefs
        )
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "focus_session_completion_alert"
        private const val KEY_SESSION_NAME = "session_name"
        private const val KEY_MINUTES = "minutes"

        fun schedule(context: Context, delayMs: Long, sessionName: String, plannedMinutes: Int) {
            if (delayMs <= 0L) return
            val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            val data = Data.Builder()
                .putString(KEY_SESSION_NAME, sessionName)
                .putInt(KEY_MINUTES, plannedMinutes)
                .build()
            val request = OneTimeWorkRequestBuilder<SessionCompletionWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
            wm.enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context) }
                .getOrNull()
                ?.cancelUniqueWork(WORK_NAME)
        }
    }
}

class FocusReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dataStore = UserPreferencesDataStore(applicationContext)
        val prefs = dataStore.notificationPreferences.first()
        if (!prefs.notificationsEnabled) return Result.success()

        val db = FocusForestDatabase.getInstance(applicationContext)
        val today = SystemClockProvider(applicationContext).todayLocalDate().toString()
        val todayStats = db.dailyStatsDao().getDailyStatsForDateOnce(today)
        val goalMinutes = dataStore.dailyGoalMinutes.first()
        val completedMinutes = todayStats?.focusMinutes ?: 0

        if (prefs.incompleteGoalReminderEnabled && completedMinutes < goalMinutes) {
            val remaining = goalMinutes - completedMinutes
            NotificationHelper.showGentleReminderNotification(
                context = applicationContext,
                title = "A calm moment for your forest",
                body = "$remaining minutes left toward today's ${goalMinutes}m focus intention — even a short 10m block counts.",
                prefs = prefs
            )
        } else if (prefs.dailyReminderEnabled && completedMinutes == 0) {
            NotificationHelper.showGentleReminderNotification(
                context = applicationContext,
                title = "Ready to plant today's first tree?",
                body = "Choose a quiet focus block whenever you're ready.",
                prefs = prefs
            )
        }
        return Result.success()
    }

    companion object {
        private const val DAILY_WORK_NAME = "focus_forest_daily_reminder"

        fun syncSchedule(context: Context, prefs: NotificationPreferences) {
            val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            if (!prefs.notificationsEnabled || (!prefs.dailyReminderEnabled && !prefs.incompleteGoalReminderEnabled)) {
                wm.cancelUniqueWork(DAILY_WORK_NAME)
                return
            }
            val now = LocalDateTime.now()
            val targetTime = LocalTime.of(
                prefs.dailyReminderHour.coerceIn(0, 23),
                prefs.dailyReminderMinute.coerceIn(0, 59)
            )
            var nextRun = now.toLocalDate().atTime(targetTime)
            if (!nextRun.isAfter(now)) {
                nextRun = nextRun.plusDays(1)
            }
            val initialDelayMinutes = Duration.between(now, nextRun).toMinutes().coerceAtLeast(1L)

            val request = PeriodicWorkRequestBuilder<FocusReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(
                DAILY_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
