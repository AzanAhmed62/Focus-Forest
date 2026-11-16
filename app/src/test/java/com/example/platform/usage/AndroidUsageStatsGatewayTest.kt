package com.example.platform.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.example.core.time.ClockProvider
import com.example.domain.model.UsagePermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

private class FixedUsageClock(
    private val today: LocalDate = LocalDate.of(2026, 11, 16)
) : ClockProvider {
    override fun nowWallClockMs(): Long =
        today.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() - 1000L
    override fun elapsedRealtimeMs(): Long = 50_000L
    override fun bootCount(): Int = 1
    override fun zoneId(): ZoneId = ZoneId.of("UTC")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidUsageStatsGatewayTest {

    @Test
    fun `gateway checks app ops permission and handles denied state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val shadowAppOps = Shadows.shadowOf(appOps)
        shadowAppOps.setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ERRORED
        )

        val gateway = AndroidUsageStatsGateway(context, FixedUsageClock())
        assertEquals(UsagePermissionStatus.DENIED, gateway.checkUsagePermission())
        assertTrue(gateway.queryDailyForegroundUsage(LocalDate.of(2026, 11, 16), emptySet()).isEmpty())
    }

    @Test
    fun `gateway returns empty list when granted but android reports no usage data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val shadowAppOps = Shadows.shadowOf(appOps)
        shadowAppOps.setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val gateway = AndroidUsageStatsGateway(context, FixedUsageClock())
        assertEquals(UsagePermissionStatus.GRANTED, gateway.checkUsagePermission())
        val usage = gateway.queryDailyForegroundUsage(LocalDate.of(2026, 11, 16), emptySet())
        assertTrue(usage.isEmpty())
        val apps = gateway.getLaunchableApps()
        assertTrue(apps.all { it.icon == null })
    }
}
