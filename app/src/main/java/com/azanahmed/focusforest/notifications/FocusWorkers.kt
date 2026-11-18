package com.azanahmed.focusforest.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.azanahmed.focusforest.core.time.ClockProvider
import com.azanahmed.focusforest.core.time.SystemClockProvider
import com.azanahmed.focusforest.data.local.datastore.UserPreferencesDataStore
import com.azanahmed.focusforest.data.local.db.FocusForestDatabase
import com.azanahmed.focusforest.data.repository.FocusRepository
import com.azanahmed.focusforest.domain.engine.FocusTimerEngine
import com.azanahmed.focusforest.domain.engine.TimerRecoveryResult
import com.azanahmed.focusforest.domain.model.NotificationPreferences
import com.azanahmed.focusforest.domain.model.SessionStatus
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
    data class Continued(val sessionId: String) : WorkerCompletionAction()
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
        if (saved == null) {
            // The worker may be the only component alive after process death. Reconcile a chain
            // whose previous step completed but whose next timer was never created.
            val recovered = focusRepository.recoverChainState()
            val next = recovered.nextTimer
            return if (next != null) {
                scheduleReschedule(
                    next.plannedDurationMs,
                    next.sessionId,
                    next.sessionType.displayName,
                    (next.plannedDurationMs / 60_000L).toInt().coerceAtLeast(1)
                )
                WorkerCompletionAction.Continued(next.sessionId)
            } else {
                WorkerCompletionAction.IgnoredStaleOrMissing
            }
        }
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
                focusRepository.markChainWaitingAfterInterruption(eval.state.chainId)
                WorkerCompletionAction.Interrupted(eval.state.sessionId)
            }
            is TimerRecoveryResult.ReadyToComplete -> {
                val outcome = focusRepository.recordCompletedSession(eval.state)
                val continuation = focusRepository.continueChainAfterCompletion(
                    chainId = eval.state.chainId,
                    session = outcome.session
                )
                if (outcome.duplicateSuppressed) {
                    // Rewards were already credited (e.g. the UI crashed after completion but before
                    // persisting the next step). Still drive the idempotent chain continuation so a
                    // worker-only process can recover the chain instead of waiting for a cold start.
                    val next = continuation.nextTimer
                    return if (next != null) {
                        scheduleReschedule(
                            next.plannedDurationMs,
                            next.sessionId,
                            next.sessionType.displayName,
                            (next.plannedDurationMs / 60_000L).toInt().coerceAtLeast(1)
                        )
                        WorkerCompletionAction.Continued(next.sessionId)
                    } else {
                        WorkerCompletionAction.IgnoredStaleOrMissing
                    }
                }
                continuation.nextTimer?.let { next ->
                    scheduleReschedule(
                        next.plannedDurationMs,
                        next.sessionId,
                        next.sessionType.displayName,
                        (next.plannedDurationMs / 60_000L).toInt().coerceAtLeast(1)
                    )
                }
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
        return try {
            coordinator.execute(
                expectedSessionId = sessionId,
                fallbackSessionName = sessionName,
                fallbackMinutes = minutes
            )
            Result.success()
        } catch (_: Throwable) {
            // Transient storage failure: let WorkManager retry so a chain step is never lost.
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
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
