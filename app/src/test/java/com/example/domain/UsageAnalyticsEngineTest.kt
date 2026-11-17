package com.example.domain

import com.example.domain.engine.UsageAnalyticsEngine
import com.example.domain.model.AppUsageSummary
import com.example.domain.model.DailyUsageSummary
import com.example.domain.model.SnapshotCompleteness
import com.example.domain.model.UsageQueryResult
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
    fun `daily summary preserves sub-minute apps and distinguishes zero usage from unavailable`() {
        val date = LocalDate.of(2026, 11, 16)

        val unavailable = UsageAnalyticsEngine.buildDailySummary(
            date,
            UsageQueryResult.Unavailable("No buckets"),
            emptySet()
        )
        assertFalse(unavailable.hasUsableData)
        assertEquals(SnapshotCompleteness.UNAVAILABLE, unavailable.completeness)

        val zeroUsage = UsageAnalyticsEngine.buildDailySummary(
            date,
            UsageQueryResult.SuccessZeroUsage(isPartialDay = true),
            emptySet()
        )
        assertTrue(zeroUsage.hasUsableData)
        assertEquals(SnapshotCompleteness.ZERO_USAGE, zeroUsage.completeness)
        assertEquals(0, zeroUsage.totalObservedMinutes)

        // Three 45s apps total 135,000 ms (2 minutes total) even though each is <1m individually
        val subMinuteSummary = UsageAnalyticsEngine.buildDailySummary(
            date,
            UsageQueryResult.SuccessWithData(
                items = listOf(
                    AppUsageSummary("com.a", "App A", 0, 45_000L, true),
                    AppUsageSummary("com.b", "App B", 0, 45_000L, true),
                    AppUsageSummary("com.c", "App C", 0, 45_000L, false)
                ),
                isPartialDay = true
            ),
            monitoredPackages = setOf("com.a", "com.b")
        )
        assertTrue(subMinuteSummary.hasUsableData)
        assertEquals(SnapshotCompleteness.PARTIAL_TODAY, subMinuteSummary.completeness)
        assertEquals(3, subMinuteSummary.topApps.size)
        assertEquals("<1m", subMinuteSummary.topApps[0].displayDuration)
        assertEquals(135_000L, subMinuteSummary.totalObservedMillis)
        assertEquals(2, subMinuteSummary.totalObservedMinutes)
        assertEquals(90_000L, subMinuteSummary.monitoredAppsMillis)
        assertEquals(1, subMinuteSummary.monitoredAppsMinutes)
    }

    @Test
    fun `baseline refuses partial-today percentage comparison and requires completed prior baseline`() {
        val today = LocalDate.of(2026, 11, 16)
        // Only 7 days [today-7..today-1] + partial today: today is NEVER compared, and yesterday only has 6 prior days!
        val partialSevenPriorMap = (1L..7L).associate { offset ->
            val d = today.minusDays(offset)
            d to DailyUsageSummary(d.toString(), 100, 100, hasUsableData = true, completeness = SnapshotCompleteness.COMPLETE)
        } + (today to DailyUsageSummary(today.toString(), 20, 20, hasUsableData = true, completeness = SnapshotCompleteness.PARTIAL_TODAY))

        val notYetComparable = UsageAnalyticsEngine.computeBaselineStatus(
            todayDate = today,
            summariesByDate = partialSevenPriorMap,
            useMonitoredSubset = false,
            dailyBudgetMinutes = 120
        )
        assertFalse(notYetComparable.isBaselineReady)
        assertEquals(6, notYetComparable.validBaselineDays)
        assertNull(notYetComparable.improvementPercent)
        assertEquals(20, notYetComparable.todayMinutes)
        assertTrue(notYetComparable.isTodayPartial)

        // Complete yesterday (today-1) = 70m, prior 7 completed days (today-2..today-8) = 100m each -> 30% improvement
        // Plus full prior week (today-8..today-14) = 100m/day (700m), current week (today-1..today-7) = 670m (~4% reduction)
        val fullTwoWeeks = (8L..14L).associate { offset ->
            val d = today.minusDays(offset)
            d to DailyUsageSummary(d.toString(), 100, 100, hasUsableData = true, completeness = SnapshotCompleteness.COMPLETE)
        } + (2L..7L).associate { offset ->
            val d = today.minusDays(offset)
            d to DailyUsageSummary(d.toString(), 100, 100, hasUsableData = true, completeness = SnapshotCompleteness.COMPLETE)
        } + (today.minusDays(1) to DailyUsageSummary(
            today.minusDays(1).toString(), 70, 70, hasUsableData = true, completeness = SnapshotCompleteness.COMPLETE
        )) + (today to DailyUsageSummary(
            today.toString(), 10, 10, hasUsableData = true, completeness = SnapshotCompleteness.PARTIAL_TODAY
        ))

        val ready = UsageAnalyticsEngine.computeBaselineStatus(
            todayDate = today,
            summariesByDate = fullTwoWeeks,
            useMonitoredSubset = false,
            dailyBudgetMinutes = 120
        )
        assertTrue(ready.isBaselineReady)
        assertEquals(100, ready.sevenDayAverageMinutes)
        assertEquals(today.minusDays(1).toString(), ready.comparableCompletedDayDate)
        assertEquals(70, ready.comparableCompletedDayMinutes)
        assertEquals(30, ready.improvementPercent)
        assertTrue(ready.isWeeklyBaselineReady)
        assertEquals(670, ready.weeklyCompletedMinutes)
        assertEquals(700, ready.weeklyPriorBaselineMinutes)
        assertEquals(4, ready.weeklyReductionPercent)
    }
}

