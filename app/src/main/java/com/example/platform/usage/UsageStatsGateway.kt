package com.example.platform.usage

import com.example.domain.model.AppUsageSummary
import com.example.domain.model.LaunchableAppInfo
import com.example.domain.model.UsagePermissionStatus
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
}
