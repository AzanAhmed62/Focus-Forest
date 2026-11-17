package com.example.platform.usage

import com.example.domain.model.AppUsageSummary
import com.example.domain.model.LaunchableAppInfo
import com.example.domain.model.UsagePermissionStatus
import com.example.domain.model.UsageQueryResult
import java.time.LocalDate

interface UsageStatsGateway {
    fun checkUsagePermission(): UsagePermissionStatus
    fun getLaunchableApps(): List<LaunchableAppInfo>

    fun queryDailyForegroundUsage(
        date: LocalDate,
        monitoredPackages: Set<String>
    ): List<AppUsageSummary>

    fun queryDailyForegroundUsage(
        date: LocalDate,
        monitoredPackages: Set<String>,
        preloadedLaunchableApps: List<LaunchableAppInfo>
    ): List<AppUsageSummary> = queryDailyForegroundUsage(date, monitoredPackages)

    fun queryDailyForegroundUsageResult(
        date: LocalDate,
        monitoredPackages: Set<String>,
        preloadedLaunchableApps: List<LaunchableAppInfo> = getLaunchableApps()
    ): UsageQueryResult {
        if (checkUsagePermission() != UsagePermissionStatus.GRANTED) {
            return UsageQueryResult.PermissionDenied
        }
        val items = queryDailyForegroundUsage(date, monitoredPackages, preloadedLaunchableApps)
        return if (items.isEmpty()) {
            UsageQueryResult.Unavailable("No foreground usage reported for $date.")
        } else {
            UsageQueryResult.SuccessWithData(items = items, isPartialDay = false)
        }
    }
}
