package com.example.domain.engine

import com.example.domain.model.AppUsageSummary
import com.example.domain.model.DailyUsageSummary
import com.example.domain.model.UsageBaselineStatus
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
        items: List<AppUsageSummary>,
        monitoredPackages: Set<String>
    ): DailyUsageSummary {
        val sorted = items
            .filter { it.foregroundMinutes > 0 }
            .sortedByDescending { it.foregroundMillis }
            .map { it.copy(isMonitored = it.packageName in monitoredPackages) }

        val hasUsableData = sorted.isNotEmpty()
        val totalMinutes = sorted.sumOf { it.foregroundMinutes }
        val monitoredMinutes = if (monitoredPackages.isEmpty()) {
            totalMinutes
        } else {
            sorted.filter { it.isMonitored }.sumOf { it.foregroundMinutes }
        }

        return DailyUsageSummary(
            localDate = localDate.toString(),
            totalObservedMinutes = totalMinutes,
            monitoredAppsMinutes = monitoredMinutes,
            hasUsableData = hasUsableData,
            topApps = sorted
        )
    }

    /**
     * Computes 7-day baseline honesty status.
     * Never claims percentage improvements unless at least 7 prior valid days exist
     * and today has usable observed data.
     */
    fun computeBaselineStatus(
        todayDate: LocalDate,
        summariesByDate: Map<LocalDate, DailyUsageSummary>,
        useMonitoredSubset: Boolean
    ): UsageBaselineStatus {
        val priorValidDays = (1L..14L)
            .mapNotNull { offset -> summariesByDate[todayDate.minusDays(offset)] }
            .filter { it.hasUsableData }
            .take(7)

        val todaySummary = summariesByDate[todayDate]
        val todayMinutes = if (todaySummary != null && todaySummary.hasUsableData) {
            if (useMonitoredSubset) todaySummary.monitoredAppsMinutes else todaySummary.totalObservedMinutes
        } else {
            null
        }

        if (priorValidDays.size < 7) {
            return UsageBaselineStatus(
                validBaselineDays = priorValidDays.size,
                requiredDays = 7,
                sevenDayAverageMinutes = null,
                todayMinutes = todayMinutes,
                improvementPercent = null
            )
        }

        val avgMinutes = priorValidDays
            .map { if (useMonitoredSubset) it.monitoredAppsMinutes else it.totalObservedMinutes }
            .average()
            .roundToInt()

        val improvement = if (todayMinutes != null && avgMinutes > 0) {
            (((avgMinutes - todayMinutes).toDouble() / avgMinutes.toDouble()) * 100.0).roundToInt()
        } else {
            null
        }

        return UsageBaselineStatus(
            validBaselineDays = 7,
            requiredDays = 7,
            sevenDayAverageMinutes = avgMinutes,
            todayMinutes = todayMinutes,
            improvementPercent = improvement
        )
    }
}
