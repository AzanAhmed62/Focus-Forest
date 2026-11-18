package com.azanahmed.focusforest.platform.usage

import com.azanahmed.focusforest.domain.model.AppUsageSummary
import com.azanahmed.focusforest.domain.model.LaunchableAppInfo
import com.azanahmed.focusforest.domain.model.UsagePermissionStatus
import com.azanahmed.focusforest.domain.model.UsageQueryResult
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
