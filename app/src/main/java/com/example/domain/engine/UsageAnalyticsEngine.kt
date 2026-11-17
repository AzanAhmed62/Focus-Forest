package com.example.domain.engine

import com.example.domain.model.AppUsageSummary
import com.example.domain.model.DailyUsageSummary
import com.example.domain.model.SnapshotCompleteness
import com.example.domain.model.UsageBaselineStatus
import com.example.domain.model.UsageQueryResult
import java.time.LocalDate
import kotlin.math.roundToInt

object UsageAnalyticsEngine {

    private val excludedSystemPrefixes = listOf(
        "com.android.systemui",
        "com.android.settings",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller"
    )

    fun isRelevantUserApp(
        packageName: String,
        selfPackageName: String,
        launchablePackages: Set<String>
    ): Boolean {
        if (packageName == selfPackageName) return false
        if (excludedSystemPrefixes.any { packageName.startsWith(it) }) return false
        if (launchablePackages.isNotEmpty()) {
            return packageName in launchablePackages
        }
        return true
    }

    fun buildDailySummary(
        localDate: LocalDate,
        queryResult: UsageQueryResult,
        monitoredPackages: Set<String>
    ): DailyUsageSummary {
        return when (queryResult) {
            is UsageQueryResult.SuccessWithData -> {
                val filtered = queryResult.items
                    .filter { it.foregroundMillis > 0L }
                    .sortedByDescending { it.foregroundMillis }
                    .map {
                        it.copy(
                            foregroundMinutes = (it.foregroundMillis / 60_000L).toInt(),
                            isMonitored = it.packageName in monitoredPackages
                        )
                    }

                if (filtered.isEmpty()) {
                    return DailyUsageSummary(
                        localDate = localDate.toString(),
                        totalObservedMinutes = 0,
                        monitoredAppsMinutes = 0,
                        hasUsableData = true,
                        topApps = emptyList(),
                        totalObservedMillis = 0L,
                        monitoredAppsMillis = 0L,
                        completeness = SnapshotCompleteness.ZERO_USAGE
                    )
                }

                // Aggregate exact milliseconds first before converting to display minutes
                val totalMillis = filtered.sumOf { it.foregroundMillis }
                val monitoredMillis = if (monitoredPackages.isEmpty()) {
                    totalMillis
                } else {
                    filtered.filter { it.isMonitored }.sumOf { it.foregroundMillis }
                }

                DailyUsageSummary(
                    localDate = localDate.toString(),
                    totalObservedMinutes = (totalMillis / 60_000L).toInt(),
                    monitoredAppsMinutes = (monitoredMillis / 60_000L).toInt(),
                    hasUsableData = true,
                    topApps = filtered,
                    totalObservedMillis = totalMillis,
                    monitoredAppsMillis = monitoredMillis,
                    completeness = if (queryResult.isPartialDay) {
                        SnapshotCompleteness.PARTIAL_TODAY
                    } else {
                        SnapshotCompleteness.COMPLETE
                    }
                )
            }
            is UsageQueryResult.SuccessZeroUsage -> {
                DailyUsageSummary(
                    localDate = localDate.toString(),
                    totalObservedMinutes = 0,
                    monitoredAppsMinutes = 0,
                    hasUsableData = true,
                    topApps = emptyList(),
                    totalObservedMillis = 0L,
                    monitoredAppsMillis = 0L,
                    completeness = SnapshotCompleteness.ZERO_USAGE
                )
            }
            UsageQueryResult.PermissionDenied,
            UsageQueryResult.TrackingDisabled,
            is UsageQueryResult.Unavailable,
            is UsageQueryResult.Failure -> {
                DailyUsageSummary(
                    localDate = localDate.toString(),
                    totalObservedMinutes = 0,
                    monitoredAppsMinutes = 0,
                    hasUsableData = false,
                    topApps = emptyList(),
                    totalObservedMillis = 0L,
                    monitoredAppsMillis = 0L,
                    completeness = SnapshotCompleteness.UNAVAILABLE
                )
            }
        }
    }

    fun buildDailySummary(
        localDate: LocalDate,
        items: List<AppUsageSummary>,
        monitoredPackages: Set<String>,
        isPartialDay: Boolean = false
    ): DailyUsageSummary {
        val result = if (items.none { it.foregroundMillis > 0L }) {
            UsageQueryResult.Unavailable("No foreground usage reported")
        } else {
            UsageQueryResult.SuccessWithData(items = items, isPartialDay = isPartialDay)
        }
        return buildDailySummary(localDate, result, monitoredPackages)
    }

    /**
     * Computes non-misleading baseline status.
     * - Today ([todayDate]) is reported as partial in-progress usage against [dailyBudgetMinutes].
     * - Percentage improvements ([improvementPercent]) are NEVER computed from partial today vs full prior days.
     * - Instead, [improvementPercent] compares the most recent COMPLETE (or ZERO_USAGE) day prior to
     *   today against the 7 completed days immediately preceding that completed day.
     * - Weekly comparisons ([weeklyReductionPercent]) require 7 completed days in the current completed
     *   7-day window compared against 7 completed days in the prior 7-day window.
     */
    fun computeBaselineStatus(
        todayDate: LocalDate,
        summariesByDate: Map<LocalDate, DailyUsageSummary>,
        useMonitoredSubset: Boolean,
        dailyBudgetMinutes: Int = 120
    ): UsageBaselineStatus {
        val todaySummary = summariesByDate[todayDate]
        val todayMinutes = if (todaySummary != null && todaySummary.hasUsableData) {
            selectedMinutes(todaySummary, useMonitoredSubset)
        } else {
            null
        }

        val comparableCompletedDate = (1L..3L)
            .map { todayDate.minusDays(it) }
            .firstOrNull { d ->
                val s = summariesByDate[d]
                s != null && s.hasUsableData && s.completeness != SnapshotCompleteness.PARTIAL_TODAY
            }

        val comparableCompletedSummary = comparableCompletedDate?.let { summariesByDate[it] }
        val comparableCompletedMinutes = comparableCompletedSummary?.let { selectedMinutes(it, useMonitoredSubset) }

        val priorBaselineDays = if (comparableCompletedDate != null) {
            (1L..21L)
                .mapNotNull { offset -> summariesByDate[comparableCompletedDate.minusDays(offset)] }
                .filter { it.hasUsableData && it.completeness != SnapshotCompleteness.PARTIAL_TODAY }
                .take(7)
        } else {
            // Fallback count of any completed days before today for progress UI ("X / 7 valid days")
            (1L..21L)
                .mapNotNull { offset -> summariesByDate[todayDate.minusDays(offset)] }
                .filter { it.hasUsableData && it.completeness != SnapshotCompleteness.PARTIAL_TODAY }
                .take(7)
        }

        // Weekly completed windows: days [today-7..today-1] vs [today-14..today-8]
        val currentWeekCompleted = (1L..7L)
            .mapNotNull { offset -> summariesByDate[todayDate.minusDays(offset)] }
            .filter { it.hasUsableData && it.completeness != SnapshotCompleteness.PARTIAL_TODAY }
        val priorWeekCompleted = (8L..14L)
            .mapNotNull { offset -> summariesByDate[todayDate.minusDays(offset)] }
            .filter { it.hasUsableData && it.completeness != SnapshotCompleteness.PARTIAL_TODAY }

        val weeklyCurrentTotal = if (currentWeekCompleted.size == 7) {
            currentWeekCompleted.sumOf { selectedMinutes(it, useMonitoredSubset) }
        } else {
            null
        }
        val weeklyPriorTotal = if (priorWeekCompleted.size == 7) {
            priorWeekCompleted.sumOf { selectedMinutes(it, useMonitoredSubset) }
        } else {
            null
        }
        val weeklyReduction = if (weeklyCurrentTotal != null && weeklyPriorTotal != null && weeklyPriorTotal > 0) {
            (((weeklyPriorTotal - weeklyCurrentTotal).toDouble() / weeklyPriorTotal.toDouble()) * 100.0).roundToInt()
        } else {
            null
        }

        if (priorBaselineDays.size < 7 || comparableCompletedMinutes == null) {
            return UsageBaselineStatus(
                validBaselineDays = priorBaselineDays.size,
                requiredDays = 7,
                sevenDayAverageMinutes = null,
                todayMinutes = todayMinutes,
                improvementPercent = null,
                comparableCompletedDayMinutes = comparableCompletedMinutes,
                comparableCompletedDayDate = comparableCompletedDate?.toString(),
                dailyBudgetMinutes = dailyBudgetMinutes,
                weeklyCompletedMinutes = weeklyCurrentTotal,
                weeklyPriorBaselineMinutes = weeklyPriorTotal,
                weeklyReductionPercent = weeklyReduction,
                isTodayPartial = true
            )
        }

        val avgMinutes = priorBaselineDays
            .map { selectedMinutes(it, useMonitoredSubset) }
            .average()
            .roundToInt()

        val improvement = if (avgMinutes > 0) {
            (((avgMinutes - comparableCompletedMinutes).toDouble() / avgMinutes.toDouble()) * 100.0).roundToInt()
        } else {
            null
        }

        return UsageBaselineStatus(
            validBaselineDays = 7,
            requiredDays = 7,
            sevenDayAverageMinutes = avgMinutes,
            todayMinutes = todayMinutes,
            improvementPercent = improvement,
            comparableCompletedDayMinutes = comparableCompletedMinutes,
            comparableCompletedDayDate = comparableCompletedDate?.toString(),
            dailyBudgetMinutes = dailyBudgetMinutes,
            weeklyCompletedMinutes = weeklyCurrentTotal,
            weeklyPriorBaselineMinutes = weeklyPriorTotal,
            weeklyReductionPercent = weeklyReduction,
            isTodayPartial = true
        )
    }

    private fun selectedMinutes(summary: DailyUsageSummary, useMonitoredSubset: Boolean): Int {
        return if (useMonitoredSubset) summary.monitoredAppsMinutes else summary.totalObservedMinutes
    }
}
