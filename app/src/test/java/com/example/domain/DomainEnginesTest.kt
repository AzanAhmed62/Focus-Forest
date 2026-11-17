package com.example.domain

import com.example.domain.engine.AchievementEngine
import com.example.domain.engine.CalendarDayKind
import com.example.domain.engine.ChallengeEngine
import com.example.domain.engine.ForestGrowthEngine
import com.example.domain.engine.InsightsAnalyticsEngine
import com.example.domain.engine.StreakCalculator
import com.example.domain.engine.TimeOfDayBucket
import com.example.domain.engine.XpLevelEngine
import com.example.domain.model.AchievementType
import com.example.domain.model.ChainConfig
import com.example.domain.model.ChallengeMetricType
import com.example.domain.model.ChallengePeriod
import com.example.domain.model.ChallengeStatus
import com.example.domain.model.DailyStats
import com.example.domain.model.FocusSession
import com.example.domain.model.GrowthStage
import com.example.domain.model.PersonalRecords
import com.example.domain.model.SessionStatus
import com.example.domain.model.SessionType
import com.example.domain.model.UsageBaselineStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class DomainEnginesTest {

    @Test
    fun `xp and seed calculations award focus work only and zero for breaks`() {
        val pomodoro = XpLevelEngine.calculateSessionReward(SessionType.POMODORO, 25)
        assertEquals(50, pomodoro.xp)
        assertEquals(2, pomodoro.seeds)

        val deepWork = XpLevelEngine.calculateSessionReward(SessionType.DEEP_WORK, 50)
        assertEquals(120, deepWork.xp)
        assertEquals(5, deepWork.seeds)

        val shortBreak = XpLevelEngine.calculateSessionReward(SessionType.SHORT_BREAK, 5)
        assertEquals(0, shortBreak.xp)
        assertEquals(0, shortBreak.seeds)

        val longBreak = XpLevelEngine.calculateSessionReward(SessionType.LONG_BREAK, 15)
        assertEquals(0, longBreak.xp)
        assertEquals(0, longBreak.seeds)

        val zero = XpLevelEngine.calculateSessionReward(SessionType.CUSTOM, 0)
        assertEquals(0, zero.xp)
        assertEquals(0, zero.seeds)
    }

    @Test
    fun `level progression advances monotonically at thresholds`() {
        assertEquals(1, XpLevelEngine.levelForXp(0))
        assertEquals(1, XpLevelEngine.levelForXp(149))
        assertEquals(2, XpLevelEngine.levelForXp(150))
        assertEquals(3, XpLevelEngine.levelForXp(350))
        assertEquals(5, XpLevelEngine.levelForXp(900))
        val progress = XpLevelEngine.levelProgress(250)
        assertEquals(2, progress.level)
        assertEquals(0.5f, progress.progressFraction, 0.01f)
    }

    @Test
    fun `streak calculator supports yesterday intact, rest days, and gentle grace`() {
        val today = LocalDate.of(2026, 11, 16)
        val activeOnlyPast = setOf(today.minusDays(1), today.minusDays(2))
        val res1 = StreakCalculator.calculateStreaks(activeOnlyPast, emptySet(), today)
        assertEquals(2, res1.currentStreakDays)

        val withRest = setOf(today, today.minusDays(2), today.minusDays(3))
        val restDays = setOf(today.minusDays(1))
        val res2 = StreakCalculator.calculateStreaks(withRest, restDays, today)
        assertEquals(3, res2.currentStreakDays)

        val broken = setOf(today, today.minusDays(3), today.minusDays(4))
        val res3 = StreakCalculator.calculateStreaks(broken, emptySet(), today)
        assertEquals(1, res3.currentStreakDays)
        assertEquals(2, res3.longestStreakDays)
    }

    @Test
    fun `daily challenges are deterministic per date and defer rewardClaimed to RewardAuthority`() {
        val date = LocalDate.of(2026, 11, 16)
        val firstGen = ChallengeEngine.generateDailyChallenges(date, 1000L, baselineReady = false)
        val secondGen = ChallengeEngine.generateDailyChallenges(date, 2000L, baselineReady = false)
        assertEquals(3, firstGen.size)
        assertEquals(firstGen.map { it.id }, secondGen.map { it.id })

        val session = FocusSession(
            id = "s1",
            sessionType = SessionType.DEEP_WORK,
            plannedMinutes = 50,
            completedMinutes = 50,
            status = SessionStatus.COMPLETED,
            startedAtWallMs = 1000L,
            endedAtWallMs = 3_001_000L,
            localDate = date.toString(),
            xpAwarded = 120,
            seedsAwarded = 5,
            rewardGranted = true
        )
        val sessionsByChallenge = firstGen.associate { it.id to listOf(session) }
        val output = ChallengeEngine.evaluate(
            challenges = firstGen,
            sessionsInPeriodByChallengeId = sessionsByChallenge,
            usageBaseline = UsageBaselineStatus(0, 7, null, null, null),
            todayDate = date,
            nowWallClockMs = 5000L
        )
        assertTrue(output.evaluatedChallenges.any { it.status == ChallengeStatus.COMPLETED })
        assertTrue(output.challengesNeedingReward.isNotEmpty())
        // Pure domain engine never sets rewardClaimed=true; RewardAuthority flips it after DB commit
        assertTrue(output.evaluatedChallenges.all { !it.rewardClaimed })

        // Once RewardAuthority persists rewardClaimed=true, re-evaluation emits zero duplicate reward candidates
        val committed = output.evaluatedChallenges.map { item ->
            if (item.status == ChallengeStatus.COMPLETED) item.copy(rewardClaimed = true) else item
        }
        val secondEval = ChallengeEngine.evaluate(
            challenges = committed,
            sessionsInPeriodByChallengeId = sessionsByChallenge,
            usageBaseline = null,
            todayDate = date,
            nowWallClockMs = 6000L
        )
        assertTrue(secondEval.challengesNeedingReward.isEmpty())
    }

    @Test
    fun `weekly challenges progress and include usage challenge only when baseline ready`() {
        val date = LocalDate.of(2026, 11, 16)
        val withoutBaseline = ChallengeEngine.generateWeeklyChallenges(date, 1000L, baselineReady = false)
        val withBaseline = ChallengeEngine.generateWeeklyChallenges(date, 1000L, baselineReady = true)
        assertEquals(2, withoutBaseline.size)
        assertEquals(3, withBaseline.size)
    }

    @Test
    fun `custom challenge validation and period expiry enforce bounds`() {
        assertFalse(ChallengeEngine.validateCustomChallenge("A", ChallengeMetricType.FOCUS_MINUTES, 60).isValid)
        assertFalse(ChallengeEngine.validateCustomChallenge("X".repeat(41), ChallengeMetricType.FOCUS_MINUTES, 60).isValid)
        assertFalse(ChallengeEngine.validateCustomChallenge("Quick", ChallengeMetricType.FOCUS_MINUTES, 5).isValid)
        assertFalse(ChallengeEngine.validateCustomChallenge("Quick", ChallengeMetricType.COMPLETED_SESSIONS, 25).isValid)
        assertTrue(ChallengeEngine.validateCustomChallenge("Thesis Block", ChallengeMetricType.FOCUS_MINUTES, 90).isValid)

        val today = LocalDate.of(2026, 11, 16)
        val yesterdayChallenges = ChallengeEngine.generateDailyChallenges(today.minusDays(1), 1000L, false)
        val todayChallenges = ChallengeEngine.generateDailyChallenges(today, 1000L, false)
        val lastWeekChallenges = ChallengeEngine.generateWeeklyChallenges(today.minusWeeks(1), 1000L, false)
        assertTrue(ChallengeEngine.isPeriodExpired(yesterdayChallenges.first(), today))
        assertFalse(ChallengeEngine.isPeriodExpired(todayChallenges.first(), today))
        assertTrue(ChallengeEngine.isPeriodExpired(lastWeekChallenges.first(), today))
        val custom = todayChallenges.first().copy(period = ChallengePeriod.CUSTOM, periodKey = "custom")
        assertFalse(ChallengeEngine.isPeriodExpired(custom, today))
    }

    @Test
    fun `insights analytics computes genuine time-of-day buckets, breakdown, and calendar grid`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 11, 16)
        val morningWall = LocalDateTime.of(2026, 11, 15, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val eveningWall = LocalDateTime.of(2026, 11, 16, 19, 30).atZone(zone).toInstant().toEpochMilli()

        val sessions = listOf(
            FocusSession(
                id = "m1",
                sessionType = SessionType.POMODORO,
                plannedMinutes = 25,
                completedMinutes = 25,
                status = SessionStatus.COMPLETED,
                startedAtWallMs = morningWall,
                endedAtWallMs = morningWall + 25 * 60_000L,
                localDate = "2026-11-15",
                xpAwarded = 50,
                seedsAwarded = 2,
                rewardGranted = true
            ),
            FocusSession(
                id = "e1",
                sessionType = SessionType.DEEP_WORK,
                plannedMinutes = 50,
                completedMinutes = 50,
                status = SessionStatus.COMPLETED,
                startedAtWallMs = eveningWall,
                endedAtWallMs = eveningWall + 50 * 60_000L,
                localDate = "2026-11-16",
                xpAwarded = 120,
                seedsAwarded = 5,
                rewardGranted = true
            )
        )
        val stats = listOf(
            DailyStats("2026-11-14", 0, 0, 50, goalMet = false, isRestDay = true),
            DailyStats("2026-11-15", 25, 1, 50, goalMet = false, isRestDay = false),
            DailyStats("2026-11-16", 50, 1, 50, goalMet = true, isRestDay = false)
        )

        val insights = InsightsAnalyticsEngine.computeAdvancedInsights(sessions, stats, today, zone)
        assertEquals("2026-11-16", insights.bestFocusDayDate)
        assertEquals(50, insights.bestFocusDayMinutes)
        assertEquals(TimeOfDayBucket.EVENING, insights.bestTimeOfDay)
        assertEquals(25, insights.timeOfDayMinutes[TimeOfDayBucket.MORNING])
        assertEquals(50, insights.timeOfDayMinutes[TimeOfDayBucket.EVENING])
        assertEquals(10, insights.monthlyConsistencyPercent) // 3 / 30 days = 10%
        assertEquals(2, insights.sessionTypeBreakdown.size)

        val trend30 = InsightsAnalyticsEngine.buildTrendWindow(stats, today, 30L, 50)
        assertEquals(30, trend30.size)

        val calendar = InsightsAnalyticsEngine.buildCalendarMonth(YearMonth.of(2026, 11), stats, today, 50)
        assertEquals(30, calendar.size)
        assertEquals(CalendarDayKind.REST_DAY, calendar.first { it.date == LocalDate.of(2026, 11, 14) }.kind)
        assertEquals(CalendarDayKind.FOCUS_ACTIVE, calendar.first { it.date == LocalDate.of(2026, 11, 15) }.kind)
        assertEquals(CalendarDayKind.GOAL_MET, calendar.first { it.date == LocalDate.of(2026, 11, 16) }.kind)
        assertEquals(CalendarDayKind.FUTURE, calendar.first { it.date == LocalDate.of(2026, 11, 20) }.kind)
    }

    @Test
    fun `chain config clamps values to safe ranges`() {
        val clamped = ChainConfig(
            focusMinutes = 2,
            shortBreakMinutes = 99,
            longBreakMinutes = 0,
            totalFocusSessions = 20,
            longBreakFrequency = 10
        ).validated()
        assertEquals(5, clamped.focusMinutes)
        assertEquals(30, clamped.shortBreakMinutes)
        assertEquals(5, clamped.longBreakMinutes)
        assertEquals(8, clamped.totalFocusSessions)
        assertEquals(8, clamped.longBreakFrequency)
    }

    @Test
    fun `achievements unlock once and evaluate progress labels`() {
        val records = PersonalRecords(
            totalFocusMinutes = 650,
            totalCompletedSessions = 12,
            longestSessionMinutes = 50,
            currentStreakDays = 7,
            longestStreakDays = 7,
            goalCompletionRatePercent = 80,
            totalActiveDays = 7
        )
        val (items, newlyUnlocked) = AchievementEngine.evaluate(
            existing = mapOf(AchievementType.FIRST_FOCUS to 100L),
            records = records,
            completedChallengesCount = 5,
            currentLevel = 5,
            nowWallClockMs = 9999L
        )
        assertFalse(AchievementType.FIRST_FOCUS in newlyUnlocked)
        assertTrue(AchievementType.THREE_DAY_CONSISTENCY in newlyUnlocked)
        assertTrue(AchievementType.SEVEN_DAY_CONSISTENCY in newlyUnlocked)
        assertTrue(AchievementType.TEN_SESSIONS in newlyUnlocked)
        assertTrue(AchievementType.TEN_FOCUS_HOURS in newlyUnlocked)
        assertTrue(AchievementType.FIRST_FIVE_CHALLENGES in newlyUnlocked)
        assertTrue(AchievementType.LEVEL_FIVE in newlyUnlocked)
        assertEquals(AchievementType.entries.size, items.size)
    }

    @Test
    fun `forest growth stage advances cleanly with seeds`() {
        assertEquals(GrowthStage.SEED, ForestGrowthEngine.stageForSeeds(0))
        assertEquals(GrowthStage.SPROUT, ForestGrowthEngine.stageForSeeds(3))
        assertEquals(GrowthStage.SAPLING, ForestGrowthEngine.stageForSeeds(10))
        assertEquals(GrowthStage.ANCIENT_GROVE, ForestGrowthEngine.stageForSeeds(105))
        assertEquals(2, ForestGrowthEngine.forestLevelForSeeds(12))
    }
}

