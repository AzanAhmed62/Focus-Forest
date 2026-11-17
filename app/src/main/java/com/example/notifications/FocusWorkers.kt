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
import com.example.core.time.ClockProvider
import com.example.core.time.SystemClockProvider
import com.example.data.local.datastore.UserPreferencesDataStore
import com.example.data.local.db.FocusForestDatabase
import com.example.data.repository.FocusRepository
import com.example.domain.engine.FocusTimerEngine
import com.example.domain.engine.TimerRecoveryResult
import com.example.domain.model.NotificationPreferences
import com.example.domain.model.SessionStatus
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

sealed class WorkerCompletionAction {
    data object IgnoredStaleOrMissing : WorkerCompletionAction()
    data class Rescheduled(val remainingMs: Long) : WorkerCompletionAction()
    data class Interrupted(val sessionId: String) : WorkerCompletionAction()
    data class Completed(val sessionId: String, val notificationPosted: Boolean) : WorkerCompletionAction()
}

class SessionCompletionCoordinator(
    private val context: Context,
    private val focusRepository: FocusRepository,
    private val prefs: UserPreferencesDataStore,
    private val clockProvider: ClockProvider,
    private val scheduleReschedule: (delayMs: Long, sessionId: String, sessionName: String, plannedMinutes: Int) -> Unit =
        { delayMs, sessionId, sessionName, plannedMinutes ->
            SessionCompletionWorker.schedule(context, delayMs, sessionId, sessionName, plannedMinutes)
        }
) {
    suspend fun execute(
        expectedSessionId: String?,
        fallbackSessionName: String,
        fallbackMinutes: Int
    ): WorkerCompletionAction {
        val saved = focusRepository.activeTimerState.first()
            ?: return WorkerCompletionAction.IgnoredStaleOrMissing
        if (saved.status != SessionStatus.RUNNING) {
            return WorkerCompletionAction.IgnoredStaleOrMissing
        }
        if (!expectedSessionId.isNullOrBlank() && saved.sessionId != expectedSessionId) {
            return WorkerCompletionAction.IgnoredStaleOrMissing
        }

        val nowWall = clockProvider.nowWallClockMs()
        val nowElapsed = clockProvider.elapsedRealtimeMs()
        val bootCount = clockProvider.bootCount()
        val nowTime = Instant.ofEpochMilli(nowWall).atZone(clockProvider.zoneId()).toLocalTime()

        return when (val eval = FocusTimerEngine.evaluateState(saved, nowWall, nowElapsed, bootCount)) {
            is TimerRecoveryResult.Active -> {
                val plannedMinutes = (eval.state.plannedDurationMs / 60_000L).toInt().coerceAtLeast(1)
                scheduleReschedule(
                    eval.state.remainingMs.coerceAtLeast(1_000L),
                    eval.state.sessionId,
                    eval.state.sessionType.displayName,
                    plannedMinutes
                )
                WorkerCompletionAction.Rescheduled(eval.state.remainingMs)
            }
            is TimerRecoveryResult.InterruptedAfterReboot -> {
                focusRepository.recordAbortedSession(eval.state, SessionStatus.INTERRUPTED)
                WorkerCompletionAction.Interrupted(eval.state.sessionId)
            }
            is TimerRecoveryResult.ReadyToComplete -> {
                val outcome = focusRepository.recordCompletedSession(eval.state)
                if (outcome.duplicateSuppressed) {
                    return WorkerCompletionAction.IgnoredStaleOrMissing
                }
                focusRepository.advanceChainForCompletedSession(eval.state.chainId, outcome.session)
                val notifPrefs = prefs.notificationPreferences.first()
                val title = "${fallbackSessionName.ifBlank { outcome.session.sessionType.displayName }} complete"
                val body = if (outcome.session.sessionType.isBreak) {
                    "Restorative ${fallbackMinutes.coerceAtLeast(outcome.session.completedMinutes)}m break finished."
                } else {
                    "You finished ${outcome.session.completedMinutes} minutes (+${outcome.session.xpAwarded} XP, +${outcome.session.seedsAwarded} seeds)."
                }
                val posted = NotificationHelper.showSessionCompletedNotification(
                    context = context,
                    title = title,
                    body = body,
                    prefs = notifPrefs,
                    nowTime = nowTime
                )
                WorkerCompletionAction.Completed(outcome.session.id, posted)
            }
        }
    }
}

class SessionCompletionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val clock = SystemClockProvider(applicationContext)
        val db = FocusForestDatabase.getInstance(applicationContext)
        val prefs = UserPreferencesDataStore(applicationContext)
        val focusRepo = FocusRepository(db, prefs, clock)
        val coordinator = SessionCompletionCoordinator(applicationContext, focusRepo, prefs, clock)

        val sessionId = inputData.getString(KEY_SESSION_ID)
        val sessionName = inputData.getString(KEY_SESSION_NAME) ?: "Focus"
        val minutes = inputData.getInt(KEY_MINUTES, 25)
        coordinator.execute(
            expectedSessionId = sessionId,
            fallbackSessionName = sessionName,
            fallbackMinutes = minutes
        )
        return Result.success()
    }

    companion object {
        internal const val WORK_NAME = "focus_session_completion_alert"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_SESSION_NAME = "session_name"
        private const val KEY_MINUTES = "minutes"

        fun schedule(
            context: Context,
            delayMs: Long,
            sessionId: String,
            sessionName: String,
            plannedMinutes: Int
        ) {
            if (delayMs <= 0L) return
            val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            val data = Data.Builder()
                .putString(KEY_SESSION_ID, sessionId)
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
        val clock = SystemClockProvider(applicationContext)
        val dataStore = UserPreferencesDataStore(applicationContext)
        val prefs = dataStore.notificationPreferences.first()
        if (!prefs.notificationsEnabled) return Result.success()

        val db = FocusForestDatabase.getInstance(applicationContext)
        val today = clock.todayLocalDate().toString()
        val nowTime = Instant.ofEpochMilli(clock.nowWallClockMs()).atZone(clock.zoneId()).toLocalTime()
        val todayStats = db.dailyStatsDao().getDailyStatsForDateOnce(today)
        val goalMinutes = dataStore.dailyGoalMinutes.first()
        val completedMinutes = todayStats?.focusMinutes ?: 0

        if (prefs.incompleteGoalReminderEnabled && completedMinutes < goalMinutes) {
            val remaining = goalMinutes - completedMinutes
            NotificationHelper.showGentleReminderNotification(
                context = applicationContext,
                title = "A calm moment for your forest",
                body = "$remaining minutes left toward today's ${goalMinutes}m focus intention — even a short 10m block counts.",
                prefs = prefs,
                nowTime = nowTime
            )
        } else if (prefs.dailyReminderEnabled && completedMinutes == 0) {
            NotificationHelper.showGentleReminderNotification(
                context = applicationContext,
                title = "Ready to plant today's first tree?",
                body = "Choose a quiet focus block whenever you're ready.",
                prefs = prefs,
                nowTime = nowTime
            )
        }
        return Result.success()
    }

    companion object {
        internal const val DAILY_WORK_NAME = "focus_forest_daily_reminder"

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

class WeeklySummaryCoordinator(
    private val context: Context,
    private val db: FocusForestDatabase,
    private val prefs: UserPreferencesDataStore,
    private val clockProvider: ClockProvider
) {
    suspend fun execute(): Boolean {
        val notifPrefs = prefs.notificationPreferences.first()
        if (!notifPrefs.notificationsEnabled || !notifPrefs.weeklySummaryEnabled) return false

        val today = clockProvider.todayLocalDate()
        val nowTime = Instant.ofEpochMilli(clockProvider.nowWallClockMs()).atZone(clockProvider.zoneId()).toLocalTime()
        val statsMap = db.dailyStatsDao().getAllDailyStatsOnce().associateBy { it.localDate }

        var weeklyMinutes = 0
        var weeklySessions = 0
        var goalMetDays = 0
        for (offset in 0L..6L) {
            val d = today.minusDays(offset).toString()
            val stat = statsMap[d] ?: continue
            weeklyMinutes += stat.focusMinutes
            weeklySessions += stat.completedSessions
            if (stat.goalMet) goalMetDays++
        }

        val body = if (weeklySessions == 0) {
            "A new week is open — even one calm 15-minute session grows your forest."
        } else {
            "$weeklyMinutes focus min across $weeklySessions session(s) this week ($goalMetDays goal days met)."
        }

        return NotificationHelper.showGentleReminderNotification(
            context = context,
            title = "Weekly Focus Forest Reflection",
            body = body,
            prefs = notifPrefs,
            isWeekly = true,
            nowTime = nowTime
        )
    }
}

class WeeklySummaryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val clock = SystemClockProvider(applicationContext)
        val db = FocusForestDatabase.getInstance(applicationContext)
        val prefs = UserPreferencesDataStore(applicationContext)
        WeeklySummaryCoordinator(applicationContext, db, prefs, clock).execute()
        return Result.success()
    }

    companion object {
        internal const val WEEKLY_WORK_NAME = "focus_forest_weekly_summary"

        fun syncSchedule(context: Context, prefs: NotificationPreferences) {
            val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            if (!prefs.notificationsEnabled || !prefs.weeklySummaryEnabled) {
                wm.cancelUniqueWork(WEEKLY_WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .build()
            wm.enqueueUniquePeriodicWork(
                WEEKLY_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}

object FocusWorkers {
    fun syncAllSchedules(context: Context, prefs: NotificationPreferences) {
        FocusReminderWorker.syncSchedule(context, prefs)
        WeeklySummaryWorker.syncSchedule(context, prefs)
    }

    fun cancelAll(context: Context) {
        SessionCompletionWorker.cancel(context)
        val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        wm.cancelUniqueWork(FocusReminderWorker.DAILY_WORK_NAME)
        wm.cancelUniqueWork(WeeklySummaryWorker.WEEKLY_WORK_NAME)
    }
}
