package com.example.domain.engine

import com.example.domain.model.DailyStats
import com.example.domain.model.FocusSession
import com.example.domain.model.SessionStatus
import com.example.domain.model.SessionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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

data class AdvancedFocusInsights(
    val monthlyConsistencyPercent: Int = 0,
    val monthlyActiveOrRestDays: Int = 0,
    val bestFocusDayDate: String? = null,
    val bestFocusDayMinutes: Int = 0,
    val bestTimeOfDay: TimeOfDayBucket? = null,
    val timeOfDayMinutes: Map<TimeOfDayBucket, Int> = emptyMap(),
    val sessionTypeBreakdown: List<SessionTypeBreakdown> = emptyList(),
    val totalDaysTracked: Int = 0
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
        zoneId: ZoneId
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

        return AdvancedFocusInsights(
            monthlyConsistencyPercent = monthlyConsistencyPercent,
            monthlyActiveOrRestDays = last30Consistent,
            bestFocusDayDate = bestDay?.localDate,
            bestFocusDayMinutes = bestDay?.focusMinutes ?: 0,
            bestTimeOfDay = bestTimeOfDay,
            timeOfDayMinutes = bucketMinutes,
            sessionTypeBreakdown = breakdown,
            totalDaysTracked = dailyStats.count { it.completedSessions > 0 || it.isRestDay }
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
