package com.azanahmed.focusforest.domain.engine

import com.azanahmed.focusforest.domain.model.DailyStats
import com.azanahmed.focusforest.domain.model.FocusSession
import com.azanahmed.focusforest.domain.model.SessionStatus
import com.azanahmed.focusforest.domain.model.SessionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

enum class TimeOfDayBucket(val label: String, val rangeLabel: String) {
    MORNING("Morning", "5:00 – 11:59"),
    AFTERNOON("Afternoon", "12:00 – 16:59"),
    EVENING("Evening", "17:00 – 21:59"),
    NIGHT("Night", "22:00 – 4:59")
}

data class SessionTypeBreakdown(
    val sessionType: SessionType,
    val count: Int,
    val minutes: Int,
    val shareFraction: Float
)

/**
 * Aggregated category or routine insight. [id] is the stable label id when it exists, but
 * [name] comes from the latest session snapshot so rename/delete never rewrites history.
 */
data class FocusContextBreakdown(
    val id: String,
    val name: String,
    val count: Int,
    val minutes: Int,
    val shareFraction: Float
)

data class AdvancedFocusInsights(
    val monthlyConsistencyPercent: Int = 0,
    val monthlyActiveOrRestDays: Int = 0,
    val bestFocusDayDate: String? = null,
    val bestFocusDayMinutes: Int = 0,
    val bestTimeOfDay: TimeOfDayBucket? = null,
    val timeOfDayMinutes: Map<TimeOfDayBucket, Int> = emptyMap(),
    val sessionTypeBreakdown: List<SessionTypeBreakdown> = emptyList(),
    val totalDaysTracked: Int = 0,
    val categoryBreakdown: List<FocusContextBreakdown> = emptyList(),
    val routineBreakdown: List<FocusContextBreakdown> = emptyList(),
    val averageCompletedFocusMinutes: Int = 0,
    val completedFocusSampleCount: Int = 0,
    val categorizedSessionCount: Int = 0,
    val routineSessionCount: Int = 0
)

enum class CalendarDayKind {
    GOAL_MET,
    FOCUS_ACTIVE,
    REST_DAY,
    MISSED,
    FUTURE
}

data class CalendarDayCell(
    val date: LocalDate,
    val kind: CalendarDayKind,
    val focusMinutes: Int,
    val completedSessions: Int,
    val goalMinutes: Int,
    val isRestDay: Boolean
)

object InsightsAnalyticsEngine {

    fun computeAdvancedInsights(
        sessions: List<FocusSession>,
        dailyStats: List<DailyStats>,
        today: LocalDate,
        zoneId: ZoneId,
        sessionsForBreakdown: List<FocusSession> = sessions
    ): AdvancedFocusInsights {
        val completedFocus = sessions.filter { it.status == SessionStatus.COMPLETED && !it.sessionType.isBreak }
        val completedAll = sessions.filter { it.status == SessionStatus.COMPLETED }

        val statsMap = dailyStats.associateBy { it.localDate }
        val last30Consistent = (0L..29L).count { offset ->
            val d = today.minusDays(offset).toString()
            val stat = statsMap[d]
            stat != null && (stat.completedSessions > 0 || stat.isRestDay)
        }
        val monthlyConsistencyPercent = ((last30Consistent * 100f) / 30f).toInt().coerceIn(0, 100)

        val bestDay = dailyStats
            .filter { it.focusMinutes > 0 }
            .maxByOrNull { it.focusMinutes }

        // Time-of-day strictly derived from genuine FocusSession.startedAtWallMs timestamps
        val bucketMinutes = TimeOfDayBucket.entries.associateWith { 0 }.toMutableMap()
        completedFocus.forEach { session ->
            val hour = Instant.ofEpochMilli(session.startedAtWallMs).atZone(zoneId).hour
            val bucket = when (hour) {
                in 5..11 -> TimeOfDayBucket.MORNING
                in 12..16 -> TimeOfDayBucket.AFTERNOON
                in 17..21 -> TimeOfDayBucket.EVENING
                else -> TimeOfDayBucket.NIGHT
            }
            bucketMinutes[bucket] = (bucketMinutes[bucket] ?: 0) + session.completedMinutes
        }
        val bestTimeOfDay = if (completedFocus.isEmpty()) {
            null
        } else {
            bucketMinutes.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
        }

        val totalCompletedMinutesAll = completedAll.sumOf { it.completedMinutes }.coerceAtLeast(1)
        val breakdown = SessionType.entries.mapNotNull { type ->
            val matching = completedAll.filter { it.sessionType == type }
            if (matching.isEmpty()) {
                null
            } else {
                val mins = matching.sumOf { it.completedMinutes }
                SessionTypeBreakdown(
                    sessionType = type,
                    count = matching.size,
                    minutes = mins,
                    shareFraction = (mins.toFloat() / totalCompletedMinutesAll.toFloat()).coerceIn(0f, 1f)
                )
            }
        }

        val windowFocus = sessionsForBreakdown.filter {
            it.status == SessionStatus.COMPLETED && !it.sessionType.isBreak
        }
        val averageCompletedMinutes = if (windowFocus.isEmpty()) {
            0
        } else {
            (windowFocus.sumOf { it.completedMinutes }.toFloat() / windowFocus.size.toFloat())
                .roundToInt()
        }
        val categoryBreakdown = buildContextBreakdown(
            sessions = windowFocus,
            idSelector = { it.categoryId },
            nameSelector = { it.categoryNameSnapshot }
        )
        val routineBreakdown = buildContextBreakdown(
            sessions = windowFocus,
            idSelector = { it.routineId },
            nameSelector = { it.routineNameSnapshot }
        )

        return AdvancedFocusInsights(
            monthlyConsistencyPercent = monthlyConsistencyPercent,
            monthlyActiveOrRestDays = last30Consistent,
            bestFocusDayDate = bestDay?.localDate,
            bestFocusDayMinutes = bestDay?.focusMinutes ?: 0,
            bestTimeOfDay = bestTimeOfDay,
            timeOfDayMinutes = bucketMinutes,
            sessionTypeBreakdown = breakdown,
            totalDaysTracked = dailyStats.count { it.completedSessions > 0 || it.isRestDay },
            categoryBreakdown = categoryBreakdown,
            routineBreakdown = routineBreakdown,
            averageCompletedFocusMinutes = averageCompletedMinutes,
            completedFocusSampleCount = windowFocus.size,
            categorizedSessionCount = windowFocus.count {
                it.categoryId != null || it.categoryNameSnapshot.isNotBlank()
            },
            routineSessionCount = windowFocus.count {
                it.routineId != null || it.routineNameSnapshot.isNotBlank()
            }
        )
    }

    /**
     * Groups completed focus sessions by their recorded label. Sessions without a label are
     * excluded so the denominator stays honest. The displayed name is the most recent non-blank
     * snapshot, and ids are never resolved against the current routines/categories table.
     */
    private fun buildContextBreakdown(
        sessions: List<FocusSession>,
        idSelector: (FocusSession) -> String?,
        nameSelector: (FocusSession) -> String
    ): List<FocusContextBreakdown> {
        val labelled = sessions.filter { session ->
            !idSelector(session).isNullOrBlank() || nameSelector(session).isNotBlank()
        }
        if (labelled.isEmpty()) return emptyList()

        val grouped = LinkedHashMap<String, MutableList<FocusSession>>()
        labelled.forEach { session ->
            val id = idSelector(session)?.takeIf { it.isNotBlank() }
            val name = nameSelector(session).trim()
            val key = id ?: "name:$name"
            grouped.getOrPut(key) { mutableListOf() }.add(session)
        }

        val totalMinutes = labelled.sumOf { it.completedMinutes }.coerceAtLeast(1)
        return grouped.map { (id, matching) ->
            val minutes = matching.sumOf { it.completedMinutes }
            FocusContextBreakdown(
                id = id,
                name = matching.firstNotNullOfOrNull { session ->
                    nameSelector(session).trim().takeIf { it.isNotBlank() }
                } ?: "Unnamed",
                count = matching.size,
                minutes = minutes,
                shareFraction = (minutes.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)
            )
        }.sortedWith(
            compareByDescending<FocusContextBreakdown> { it.minutes }
                .thenByDescending { it.count }
                .thenBy { it.name }
        )
    }

    fun buildTrendWindow(
        dailyStats: List<DailyStats>,
        today: LocalDate,
        daysCount: Long,
        defaultGoalMinutes: Int
    ): List<DailyStats> {
        val statsMap = dailyStats.associateBy { it.localDate }
        val safeDays = daysCount.coerceIn(1L, 90L)
        return ((safeDays - 1L) downTo 0L).map { offset ->
            val d = today.minusDays(offset)
            statsMap[d.toString()] ?: DailyStats(localDate = d.toString(), goalMinutes = defaultGoalMinutes)
        }
    }

    fun buildCalendarMonth(
        month: YearMonth,
        dailyStats: List<DailyStats>,
        today: LocalDate,
        defaultGoalMinutes: Int
    ): List<CalendarDayCell> {
        val statsMap = dailyStats.associateBy { it.localDate }
        val totalDays = month.lengthOfMonth()
        return (1..totalDays).map { day ->
            val date = month.atDay(day)
            val stat = statsMap[date.toString()]
            val kind = when {
                date.isAfter(today) -> CalendarDayKind.FUTURE
                stat != null && stat.goalMet -> CalendarDayKind.GOAL_MET
                stat != null && (stat.completedSessions > 0 || stat.focusMinutes > 0) -> CalendarDayKind.FOCUS_ACTIVE
                stat != null && stat.isRestDay -> CalendarDayKind.REST_DAY
                else -> CalendarDayKind.MISSED
            }
            CalendarDayCell(
                date = date,
                kind = kind,
                focusMinutes = stat?.focusMinutes ?: 0,
                completedSessions = stat?.completedSessions ?: 0,
                goalMinutes = stat?.goalMinutes ?: defaultGoalMinutes,
                isRestDay = stat?.isRestDay ?: false
            )
        }
    }
}
