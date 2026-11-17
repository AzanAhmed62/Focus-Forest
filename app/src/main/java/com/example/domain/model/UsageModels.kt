package com.example.domain.model

import android.graphics.drawable.Drawable

enum class UsagePermissionStatus {
    GRANTED,
    DENIED
}

enum class SnapshotCompleteness {
    COMPLETE,
    PARTIAL_TODAY,
    ZERO_USAGE,
    UNAVAILABLE
}

sealed class UsageQueryResult {
    data object PermissionDenied : UsageQueryResult()
    data object TrackingDisabled : UsageQueryResult()
    data class SuccessWithData(
        val items: List<AppUsageSummary>,
        val isPartialDay: Boolean
    ) : UsageQueryResult()
    data class SuccessZeroUsage(
        val isPartialDay: Boolean
    ) : UsageQueryResult()
    data class Unavailable(val reason: String) : UsageQueryResult()
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : UsageQueryResult()
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
) {
    val displayDuration: String
        get() = when {
            foregroundMillis in 1L..59_999L -> "<1m"
            else -> "${foregroundMinutes}m"
        }
}

data class DailyUsageSummary(
    val localDate: String,
    val totalObservedMinutes: Int,
    val monitoredAppsMinutes: Int,
    val hasUsableData: Boolean,
    val topApps: List<AppUsageSummary> = emptyList(),
    val totalObservedMillis: Long = totalObservedMinutes * 60_000L,
    val monitoredAppsMillis: Long = monitoredAppsMinutes * 60_000L,
    val completeness: SnapshotCompleteness = if (hasUsableData) {
        SnapshotCompleteness.COMPLETE
    } else {
        SnapshotCompleteness.UNAVAILABLE
    }
) {
    val isZeroUsage: Boolean
        get() = completeness == SnapshotCompleteness.ZERO_USAGE
}

/**
 * Honest baseline status.
 * Percentage reduction ([improvementPercent] and [weeklyReductionPercent]) is NEVER computed
 * by comparing today's partial elapsed hours against completed prior days.
 * Instead, [improvementPercent] compares the most recent COMPLETE day against the 7 completed
 * days prior to it, and [todayMinutes] is displayed alongside [dailyBudgetMinutes].
 */
data class UsageBaselineStatus(
    val validBaselineDays: Int,
    val requiredDays: Int = 7,
    val sevenDayAverageMinutes: Int?,
    val todayMinutes: Int?,
    val improvementPercent: Int?,
    val comparableCompletedDayMinutes: Int? = null,
    val comparableCompletedDayDate: String? = null,
    val dailyBudgetMinutes: Int = 120,
    val weeklyCompletedMinutes: Int? = null,
    val weeklyPriorBaselineMinutes: Int? = null,
    val weeklyReductionPercent: Int? = null,
    val isTodayPartial: Boolean = true
) {
    val isBaselineReady: Boolean get() = validBaselineDays >= requiredDays
    val isWeeklyBaselineReady: Boolean
        get() = weeklyCompletedMinutes != null && weeklyPriorBaselineMinutes != null && weeklyPriorBaselineMinutes > 0
}

data class UsageDashboardState(
    val permissionStatus: UsagePermissionStatus = UsagePermissionStatus.DENIED,
    val isTrackingEnabled: Boolean = false,
    val isDataUnavailable: Boolean = false,
    val todayCompleteness: SnapshotCompleteness = SnapshotCompleteness.UNAVAILABLE,
    val todaySummary: DailyUsageSummary? = null,
    val sevenDayHistory: List<DailyUsageSummary> = emptyList(),
    val baselineStatus: UsageBaselineStatus = UsageBaselineStatus(0, 7, null, null, null),
    val monitoredPackages: Set<String> = emptySet(),
    val availableLaunchableApps: List<LaunchableAppInfo> = emptyList(),
    val dailyUsageBudgetMinutes: Int = 120,
    val queryErrorMessage: String? = null,
    val isRefreshing: Boolean = false,
    val lastRefreshedMs: Long? = null
)
