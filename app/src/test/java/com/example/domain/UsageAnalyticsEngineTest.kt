package com.example.domain

import com.example.domain.engine.UsageAnalyticsEngine
import com.example.domain.model.AppUsageSummary
import com.example.domain.model.DailyUsageSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UsageAnalyticsEngineTest {

    @Test
    fun `app filtering excludes focus forest and system packages`() {
        val selfPkg = "com.azanahmed.focusforest"
        val launchable = setOf(selfPkg, "com.android.settings", "com.social.app", "com.notes.app")

        assertFalse(UsageAnalyticsEngine.isRelevantUserApp(selfPkg, selfPkg, launchable))
        assertFalse(UsageAnalyticsEngine.isRelevantUserApp("com.android.settings", selfPkg, launchable))
        assertFalse(UsageAnalyticsEngine.isRelevantUserApp("com.android.systemui", selfPkg, launchable))
        assertTrue(UsageAnalyticsEngine.isRelevantUserApp("com.social.app", selfPkg, launchable))
    }

    @Test
    fun `daily summary flags unavailable when no foreground minutes exist`() {
        val date = LocalDate.of(2026, 11, 16)
        val emptySummary = UsageAnalyticsEngine.buildDailySummary(date, emptyList(), emptySet())
        assertFalse(emptySummary.hasUsableData)

        val validSummary = UsageAnalyticsEngine.buildDailySummary(
            date,
            listOf(
                AppUsageSummary("com.social.app", "Social", 42, 42 * 60_000L, true),
                AppUsageSummary("com.notes.app", "Notes", 18, 18 * 60_000L, false)
            ),
            monitoredPackages = setOf("com.social.app")
        )
        assertTrue(validSummary.hasUsableData)
        assertEquals(60, validSummary.totalObservedMinutes)
        assertEquals(42, validSummary.monitoredAppsMinutes)
    }

    @Test
    fun `seven day baseline refuses improvement claim until seven valid days exist`() {
        val today = LocalDate.of(2026, 11, 16)
        val partialMap = (1L..5L).associate { offset ->
            val d = today.minusDays(offset)
            d to DailyUsageSummary(d.toString(), 100, 100, hasUsableData = true)
        } + (today to DailyUsageSummary(today.toString(), 60, 60, hasUsableData = true))

        val insufficient = UsageAnalyticsEngine.computeBaselineStatus(today, partialMap, useMonitoredSubset = false)
        assertFalse(insufficient.isBaselineReady)
        assertEquals(5, insufficient.validBaselineDays)
        assertNull(insufficient.improvementPercent)

        val fullMap = (1L..7L).associate { offset ->
            val d = today.minusDays(offset)
            d to DailyUsageSummary(d.toString(), 100, 100, hasUsableData = true)
        } + (today to DailyUsageSummary(today.toString(), 70, 70, hasUsableData = true))

        val ready = UsageAnalyticsEngine.computeBaselineStatus(today, fullMap, useMonitoredSubset = false)
        assertTrue(ready.isBaselineReady)
        assertEquals(100, ready.sevenDayAverageMinutes)
        assertEquals(30, ready.improvementPercent)
    }
}
