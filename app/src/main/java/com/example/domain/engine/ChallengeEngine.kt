package com.example.domain.engine

import com.example.domain.model.ChallengeIconType
import com.example.domain.model.ChallengeItem
import com.example.domain.model.ChallengeMetricType
import com.example.domain.model.ChallengePeriod
import com.example.domain.model.ChallengeStatus
import com.example.domain.model.DailyStats
import com.example.domain.model.FocusSession
import com.example.domain.model.SessionStatus
import com.example.domain.model.SessionType
import com.example.domain.model.UsageBaselineStatus
import java.time.LocalDate
import java.time.temporal.WeekFields
import kotlin.math.abs

object ChallengeEngine {

    private data class ChallengeTemplate(
        val key: String,
        val title: String,
        val description: String,
        val iconType: ChallengeIconType,
        val metricType: ChallengeMetricType,
        val targetValue: Int,
        val rewardXp: Int,
        val rewardSeeds: Int
    )

    private val dailyTemplates = listOf(
        ChallengeTemplate(
            key = "pomodoro_one",
            title = "Single Pomodoro Rhythm",
            description = "Complete 1 Pomodoro session (25 min) today.",
            iconType = ChallengeIconType.TIMER,
            metricType = ChallengeMetricType.POMODORO_SESSIONS,
            targetValue = 1,
            rewardXp = 35,
            rewardSeeds = 2
        ),
        ChallengeTemplate(
            key = "focus_50_min",
            title = "Fifty Mindful Minutes",
            description = "Accumulate 50 minutes of focused work today.",
            iconType = ChallengeIconType.SUNRISE,
            metricType = ChallengeMetricType.FOCUS_MINUTES,
            targetValue = 50,
            rewardXp = 50,
            rewardSeeds = 3
        ),
        ChallengeTemplate(
            key = "two_sessions",
            title = "Steady Cadence",
            description = "Complete 2 focus sessions today.",
            iconType = ChallengeIconType.SPARK,
            metricType = ChallengeMetricType.COMPLETED_SESSIONS,
            targetValue = 2,
            rewardXp = 45,
            rewardSeeds = 2
        ),
        ChallengeTemplate(
            key = "deep_work_one",
            title = "Deep Canopy Block",
            description = "Complete 1 Deep Work session (50 min) today.",
            iconType = ChallengeIconType.PINE_TREE,
            metricType = ChallengeMetricType.DEEP_WORK_SESSIONS,
            targetValue = 1,
            rewardXp = 60,
            rewardSeeds = 4
        ),
        ChallengeTemplate(
            key = "planned_break",
            title = "Mindful Pause",
            description = "Take 1 planned restorative break session today.",
            iconType = ChallengeIconType.CUP_TEA,
            metricType = ChallengeMetricType.PLANNED_BREAKS,
            targetValue = 1,
            rewardXp = 25,
            rewardSeeds = 1
        )
    )

    fun weekKeyFor(date: LocalDate): String {
        val wf = WeekFields.ISO
        val week = date.get(wf.weekOfWeekBasedYear())
        val year = date.get(wf.weekBasedYear())
        return "%d-W%02d".format(year, week)
    }

    /**
     * Generates 3 deterministic daily challenges for [date].
     * Includes a usage-reduction challenge only when a valid 7-day baseline exists.
     */
    fun generateDailyChallenges(
        date: LocalDate,
        createdAtMs: Long,
        baselineReady: Boolean
    ): List<ChallengeItem> {
        val dateKey = date.toString()
        val seed = abs(dateKey.hashCode())
        val selected = mutableListOf<ChallengeTemplate>()
        var idx = seed % dailyTemplates.size
        while (selected.size < 3) {
            val candidate = dailyTemplates[idx % dailyTemplates.size]
            if (candidate !in selected) {
                selected.add(candidate)
            }
            idx++
        }

        val items = selected.mapIndexed { position, tpl ->
            ChallengeItem(
                id = "daily_${dateKey}_${tpl.key}",
                title = tpl.title,
                description = tpl.description,
                iconType = tpl.iconType,
                period = ChallengePeriod.DAILY,
                metricType = tpl.metricType,
                targetValue = tpl.targetValue,
                currentProgress = 0,
                rewardXp = tpl.rewardXp,
                rewardSeeds = tpl.rewardSeeds,
                status = ChallengeStatus.ACTIVE,
                periodKey = dateKey,
                createdAtMs = createdAtMs + position,
                completedAtMs = null,
                rewardClaimed = false
            )
        }.toMutableList()

        if (baselineReady) {
            items[2] = ChallengeItem(
                id = "daily_${dateKey}_usage_reduce_10",
                title = "Lighter Screen Day",
                description = "Keep monitored app foreground usage 10% below your 7-day average.",
                iconType = ChallengeIconType.SHIELD_LEAF,
                period = ChallengePeriod.DAILY,
                metricType = ChallengeMetricType.USAGE_REDUCTION_PERCENT,
                targetValue = 10,
                currentProgress = 0,
                rewardXp = 55,
                rewardSeeds = 3,
                status = ChallengeStatus.ACTIVE,
                periodKey = dateKey,
                createdAtMs = createdAtMs + 2,
                completedAtMs = null,
                rewardClaimed = false
            )
        }
        return items
    }

    fun generateWeeklyChallenges(
        date: LocalDate,
        createdAtMs: Long,
        baselineReady: Boolean
    ): List<ChallengeItem> {
        val weekKey = weekKeyFor(date)
        val list = mutableListOf(
            ChallengeItem(
                id = "weekly_${weekKey}_five_sessions",
                title = "Weekly Grove Builder",
                description = "Complete 5 focus sessions this week.",
                iconType = ChallengeIconType.PINE_TREE,
                period = ChallengePeriod.WEEKLY,
                metricType = ChallengeMetricType.COMPLETED_SESSIONS,
                targetValue = 5,
                currentProgress = 0,
                rewardXp = 120,
                rewardSeeds = 6,
                status = ChallengeStatus.ACTIVE,
                periodKey = weekKey,
                createdAtMs = createdAtMs,
                completedAtMs = null,
                rewardClaimed = false
            ),
            ChallengeItem(
                id = "weekly_${weekKey}_300_minutes",
                title = "300 Reclaimed Minutes",
                description = "Reach 300 total focus minutes across this week.",
                iconType = ChallengeIconType.SPARK,
                period = ChallengePeriod.WEEKLY,
                metricType = ChallengeMetricType.FOCUS_MINUTES,
                targetValue = 300,
                currentProgress = 0,
                rewardXp = 180,
                rewardSeeds = 9,
                status = ChallengeStatus.ACTIVE,
                periodKey = weekKey,
                createdAtMs = createdAtMs + 1,
                completedAtMs = null,
                rewardClaimed = false
            )
        )

        if (baselineReady) {
            list.add(
                ChallengeItem(
                    id = "weekly_${weekKey}_usage_improve_15",
                    title = "Intentional Habits Week",
                    description = "Lower today's monitored app usage 15% under your 7-day baseline.",
                    iconType = ChallengeIconType.SHIELD_LEAF,
                    period = ChallengePeriod.WEEKLY,
                    metricType = ChallengeMetricType.USAGE_REDUCTION_PERCENT,
                    targetValue = 15,
                    currentProgress = 0,
                    rewardXp = 160,
                    rewardSeeds = 7,
                    status = ChallengeStatus.ACTIVE,
                    periodKey = weekKey,
                    createdAtMs = createdAtMs + 2,
                    completedAtMs = null,
                    rewardClaimed = false
                )
            )
        }
        return list
    }

    fun evaluateChallenges(
        challenges: List<ChallengeItem>,
        sessionsInPeriodByChallengeId: Map<String, List<FocusSession>>,
        usageBaseline: UsageBaselineStatus?,
        nowWallClockMs: Long
    ): Pair<List<ChallengeItem>, List<ChallengeItem>> {
        val updated = mutableListOf<ChallengeItem>()
        val newlyCompleted = mutableListOf<ChallengeItem>()

        for (challenge in challenges) {
            if (challenge.status == ChallengeStatus.COMPLETED && challenge.rewardClaimed) {
                updated.add(challenge)
                continue
            }

            val sessions = sessionsInPeriodByChallengeId[challenge.id].orEmpty()
                .filter { it.status == SessionStatus.COMPLETED }

            val rawProgress = when (challenge.metricType) {
                ChallengeMetricType.FOCUS_MINUTES ->
                    sessions.filter { !it.sessionType.isBreak }.sumOf { it.completedMinutes }
                ChallengeMetricType.COMPLETED_SESSIONS ->
                    sessions.count { !it.sessionType.isBreak }
                ChallengeMetricType.DEEP_WORK_SESSIONS ->
                    sessions.count { it.sessionType == SessionType.DEEP_WORK }
                ChallengeMetricType.POMODORO_SESSIONS ->
                    sessions.count { it.sessionType == SessionType.POMODORO }
                ChallengeMetricType.PLANNED_BREAKS ->
                    sessions.count { it.sessionType.isBreak }
                ChallengeMetricType.USAGE_REDUCTION_PERCENT -> {
                    if (usageBaseline != null && usageBaseline.isBaselineReady) {
                        (usageBaseline.improvementPercent ?: 0).coerceAtLeast(0)
                    } else {
                        0
                    }
                }
            }

            val clampedProgress = rawProgress.coerceAtMost(challenge.targetValue)
            val isNowComplete = rawProgress >= challenge.targetValue

            if (isNowComplete) {
                val completedItem = challenge.copy(
                    currentProgress = challenge.targetValue,
                    status = ChallengeStatus.COMPLETED,
                    completedAtMs = challenge.completedAtMs ?: nowWallClockMs,
                    rewardClaimed = true
                )
                updated.add(completedItem)
                if (!challenge.rewardClaimed) {
                    newlyCompleted.add(completedItem)
                }
            } else {
                updated.add(
                    challenge.copy(
                        currentProgress = clampedProgress
                    )
                )
            }
        }
        return updated to newlyCompleted
    }
}
