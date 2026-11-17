package com.example.domain.engine

import com.example.domain.model.ChallengeIconType
import com.example.domain.model.ChallengeItem
import com.example.domain.model.ChallengeMetricType
import com.example.domain.model.ChallengePeriod
import com.example.domain.model.ChallengeStatus
import com.example.domain.model.FocusSession
import com.example.domain.model.SessionStatus
import com.example.domain.model.SessionType
import com.example.domain.model.UsageBaselineStatus
import java.time.LocalDate
import java.time.temporal.WeekFields
import kotlin.math.abs

data class ChallengeEvaluationOutput(
    val evaluatedChallenges: List<ChallengeItem>,
    val challengesNeedingReward: List<ChallengeItem>
)

data class CustomChallengeValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

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

    fun isPeriodExpired(challenge: ChallengeItem, today: LocalDate): Boolean {
        return when (challenge.period) {
            ChallengePeriod.DAILY -> {
                val periodDate = runCatching { LocalDate.parse(challenge.periodKey) }.getOrNull()
                periodDate != null && periodDate.isBefore(today)
            }
            ChallengePeriod.WEEKLY -> {
                challenge.periodKey < weekKeyFor(today)
            }
            ChallengePeriod.CUSTOM -> false
        }
    }

    fun validateCustomChallenge(
        title: String,
        metricType: ChallengeMetricType,
        targetValue: Int
    ): CustomChallengeValidationResult {
        val trimmed = title.trim()
        if (trimmed.length < 2) {
            return CustomChallengeValidationResult(false, "Enter a challenge title (at least 2 characters).")
        }
        if (trimmed.length > 40) {
            return CustomChallengeValidationResult(false, "Title must be 40 characters or fewer.")
        }
        val (minTarget, maxTarget, unitLabel) = targetBoundsFor(metricType)
        if (targetValue !in minTarget..maxTarget) {
            return CustomChallengeValidationResult(
                false,
                "Target for ${metricType.name.lowercase().replace('_', ' ')} must be between $minTarget and $maxTarget $unitLabel."
            )
        }
        return CustomChallengeValidationResult(true)
    }

    fun targetBoundsFor(metricType: ChallengeMetricType): Triple<Int, Int, String> = when (metricType) {
        ChallengeMetricType.FOCUS_MINUTES -> Triple(15, 600, "minutes")
        ChallengeMetricType.WEEKLY_FOCUS_GOAL -> Triple(60, 1200, "minutes")
        ChallengeMetricType.COMPLETED_SESSIONS,
        ChallengeMetricType.POMODORO_SESSIONS,
        ChallengeMetricType.DEEP_WORK_SESSIONS,
        ChallengeMetricType.PLANNED_BREAKS -> Triple(1, 20, "sessions")
        ChallengeMetricType.USAGE_REDUCTION_PERCENT -> Triple(5, 50, "%")
        ChallengeMetricType.DAILY_USAGE_BUDGET -> Triple(1, 7, "completed days")
    }

    /**
     * Generates 3 deterministic daily challenges for [date].
     * Includes a completed-day usage-reduction challenge only when a valid 7-day completed baseline exists.
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
                title = "Lighter Completed Day",
                description = "Keep your latest completed day's usage 10% below your 7-day completed average.",
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
                metricType = ChallengeMetricType.WEEKLY_FOCUS_GOAL,
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
                    description = "Lower your completed 7-day usage total 15% under the prior 7-day baseline.",
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

    /**
     * Evaluates challenge progress and expiry.
     * IMPORTANT: Does NOT set `rewardClaimed = true`! Only the transactional RewardAuthority
     * in the data layer may flip `rewardClaimed = true` after crediting XP, seeds, and ledger.
     */
    fun evaluate(
        challenges: List<ChallengeItem>,
        sessionsInPeriodByChallengeId: Map<String, List<FocusSession>>,
        usageBaseline: UsageBaselineStatus?,
        todayDate: LocalDate,
        nowWallClockMs: Long
    ): ChallengeEvaluationOutput {
        val evaluated = mutableListOf<ChallengeItem>()
        val needingReward = mutableListOf<ChallengeItem>()

        for (challenge in challenges) {
            if (challenge.rewardClaimed) {
                evaluated.add(
                    challenge.copy(
                        status = ChallengeStatus.COMPLETED,
                        currentProgress = challenge.targetValue
                    )
                )
                continue
            }

            if (challenge.status == ChallengeStatus.EXPIRED || isPeriodExpired(challenge, todayDate)) {
                evaluated.add(challenge.copy(status = ChallengeStatus.EXPIRED))
                continue
            }

            val sessions = sessionsInPeriodByChallengeId[challenge.id].orEmpty()
                .filter { it.status == SessionStatus.COMPLETED }

            val rawProgress = when (challenge.metricType) {
                ChallengeMetricType.FOCUS_MINUTES,
                ChallengeMetricType.WEEKLY_FOCUS_GOAL ->
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
                    when (challenge.period) {
                        ChallengePeriod.WEEKLY -> {
                            if (usageBaseline != null && usageBaseline.isWeeklyBaselineReady) {
                                (usageBaseline.weeklyReductionPercent ?: 0).coerceAtLeast(0)
                            } else {
                                0
                            }
                        }
                        ChallengePeriod.DAILY,
                        ChallengePeriod.CUSTOM -> {
                            if (usageBaseline != null && usageBaseline.isBaselineReady) {
                                (usageBaseline.improvementPercent ?: 0).coerceAtLeast(0)
                            } else {
                                0
                            }
                        }
                    }
                }
                ChallengeMetricType.DAILY_USAGE_BUDGET -> {
                    val compMin = usageBaseline?.comparableCompletedDayMinutes
                    if (compMin != null && compMin <= usageBaseline.dailyBudgetMinutes) {
                        1
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
                    rewardClaimed = false
                )
                evaluated.add(completedItem)
                needingReward.add(completedItem)
            } else {
                evaluated.add(
                    challenge.copy(
                        currentProgress = clampedProgress
                    )
                )
            }
        }
        return ChallengeEvaluationOutput(
            evaluatedChallenges = evaluated,
            challengesNeedingReward = needingReward
        )
    }

    // Legacy compatibility helper for callers/tests; keeps rewardClaimed false until ledger credits it
    fun evaluateChallenges(
        challenges: List<ChallengeItem>,
        sessionsInPeriodByChallengeId: Map<String, List<FocusSession>>,
        usageBaseline: UsageBaselineStatus?,
        nowWallClockMs: Long,
        todayDate: LocalDate = LocalDate.now()
    ): Pair<List<ChallengeItem>, List<ChallengeItem>> {
        val res = evaluate(
            challenges = challenges,
            sessionsInPeriodByChallengeId = sessionsInPeriodByChallengeId,
            usageBaseline = usageBaseline,
            todayDate = todayDate,
            nowWallClockMs = nowWallClockMs
        )
        return res.evaluatedChallenges to res.challengesNeedingReward
    }
}
