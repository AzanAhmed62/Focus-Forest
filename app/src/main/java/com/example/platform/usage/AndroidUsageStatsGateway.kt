package com.example.platform.usage

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.example.core.time.ClockProvider
import com.example.domain.engine.UsageAnalyticsEngine
import com.example.domain.model.AppUsageSummary
import com.example.domain.model.LaunchableAppInfo
import com.example.domain.model.UsagePermissionStatus
import java.time.LocalDate

class AndroidUsageStatsGateway(
    private val context: Context,
    private val clockProvider: ClockProvider
) : UsageStatsGateway {

    override fun checkUsagePermission(): UsagePermissionStatus {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return UsagePermissionStatus.DENIED
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        val granted = when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
            else -> false
        }
        return if (granted) UsagePermissionStatus.GRANTED else UsagePermissionStatus.DENIED
    }

    override fun getLaunchableApps(): List<LaunchableAppInfo> {
        val selfPkg = context.packageName
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        val discovered = linkedMapOf<String, LaunchableAppInfo>()

        if (launcherApps != null) {
            try {
                val activities = launcherApps.getActivityList(null, Process.myUserHandle())
                for (info in activities) {
                    val pkg = info.applicationInfo.packageName
                    if (pkg != selfPkg && !discovered.containsKey(pkg)) {
                        discovered[pkg] = LaunchableAppInfo(
                            packageName = pkg,
                            appName = info.label.toString(),
                            icon = null
                        )
                    }
                }
            } catch (_: Exception) {
                // Fallback below
            }
        }

        if (discovered.isEmpty()) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(intent, 0)
            for (ri in resolved) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg != selfPkg && !discovered.containsKey(pkg)) {
                    discovered[pkg] = LaunchableAppInfo(
                        packageName = pkg,
                        appName = ri.loadLabel(pm).toString(),
                        icon = null
                    )
                }
            }
        }

        return discovered.values.sortedBy { it.appName.lowercase() }
    }

    override fun queryDailyForegroundUsage(
        date: LocalDate,
        monitoredPackages: Set<String>
    ): List<AppUsageSummary> {
        return queryDailyForegroundUsage(
            date = date,
            monitoredPackages = monitoredPackages,
            preloadedLaunchableApps = getLaunchableApps()
        )
    }

    override fun queryDailyForegroundUsage(
        date: LocalDate,
        monitoredPackages: Set<String>,
        preloadedLaunchableApps: List<LaunchableAppInfo>
    ): List<AppUsageSummary> {
        if (checkUsagePermission() != UsagePermissionStatus.GRANTED) {
            return emptyList()
        }
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val zoneId = clockProvider.zoneId()
        val nowMs = clockProvider.nowWallClockMs()
        val dayStartMs = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEndMs = minOf(
            date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli(),
            nowMs
        )
        if (dayEndMs <= dayStartMs) return emptyList()

        val stats = runCatching {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                dayStartMs,
                dayEndMs
            )
        }.getOrNull().orEmpty()

        if (stats.isEmpty()) return emptyList()

        val launchableMap = preloadedLaunchableApps.associateBy { it.packageName }
        val launchablePackages = launchableMap.keys
        val pm = context.packageManager
        val selfPkg = context.packageName

        val aggregatedByPkg = linkedMapOf<String, Long>()
        for (item in stats) {
            val pkg = item.packageName ?: continue
            val fgMs = item.totalTimeInForeground
            if (fgMs <= 0L) continue
            if (!UsageAnalyticsEngine.isRelevantUserApp(pkg, selfPkg, launchablePackages)) continue
            // Filter out buckets completely outside the requested window when timestamps are populated
            if (item.lastTimeStamp > 0L && item.lastTimeStamp < dayStartMs) continue
            aggregatedByPkg[pkg] = (aggregatedByPkg[pkg] ?: 0L) + fgMs
        }

        return aggregatedByPkg.entries
            .map { (pkg, fgMs) ->
                val minutes = (fgMs / 60_000L).toInt()
                val launchInfo = launchableMap[pkg]
                val appLabel = launchInfo?.appName ?: resolveLabel(pm, pkg)
                val appIcon = launchInfo?.icon ?: resolveIconSafely(pm, pkg)
                AppUsageSummary(
                    packageName = pkg,
                    appName = appLabel,
                    foregroundMinutes = minutes,
                    foregroundMillis = fgMs,
                    isMonitored = pkg in monitoredPackages,
                    icon = appIcon
                )
            }
            .filter { it.foregroundMinutes > 0 }
            .sortedByDescending { it.foregroundMillis }
    }

    private fun resolveLabel(pm: PackageManager, pkg: String): String {
        return try {
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    /**
     * Resolves icons only for standalone user-installed apps so system/shared-library/RRO packages
     * cannot trigger AssetManager2 cross-package missing dynamic library resource logs.
     */
    private fun resolveIconSafely(pm: PackageManager, pkg: String): Drawable? {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(
                    pkg,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, PackageManager.GET_SHARED_LIBRARY_FILES)
            }
            val isSystem = (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            val hasSharedLibs = !info.sharedLibraryFiles.isNullOrEmpty()
            if (isSystem || hasSharedLibs || info.icon == 0) {
                null
            } else {
                pm.getApplicationIcon(info)
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun buildUsageAccessSettingsIntent(): Intent {
            return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
