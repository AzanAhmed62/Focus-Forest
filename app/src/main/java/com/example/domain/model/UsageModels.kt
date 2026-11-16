package com.example.domain.model

import android.graphics.drawable.Drawable

enum class UsagePermissionStatus {
    GRANTED,
    DENIED
}

data class LaunchableAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null
)

data class AppUsageSummary(
    val packageName: String,
    val appName: String,
    val foregroundMinutes: Int,
    val foregroundMillis: Long,
    val isMonitored: Boolean,
    val icon: Drawable? = null
)

data class DailyUsageSummary(
    val localDate: String,
    val totalObservedMinutes: Int,
    val monitoredAppsMinutes: Int,
    val hasUsableData: Boolean,
    val topApps: List<AppUsageSummary> = emptyList()
)

data class UsageBaselineStatus(
    val validBaselineDays: Int,
    val requiredDays: Int = 7,
    val sevenDayAverageMinutes: Int?,
    val todayMinutes: Int?,
    val improvementPercent: Int?
) {
    val isBaselineReady: Boolean get() = validBaselineDays >= requiredDays
}

data class UsageDashboardState(
    val permissionStatus: UsagePermissionStatus = UsagePermissionStatus.DENIED,
    val isTrackingEnabled: Boolean = false,
    val isDataUnavailable: Boolean = false,
    val todaySummary: DailyUsageSummary? = null,
    val sevenDayHistory: List<DailyUsageSummary> = emptyList(),
    val baselineStatus: UsageBaselineStatus = UsageBaselineStatus(0, 7, null, null, null),
    val monitoredPackages: Set<String> = emptySet(),
    val availableLaunchableApps: List<LaunchableAppInfo> = emptyList(),
    val lastRefreshedMs: Long? = null
)
