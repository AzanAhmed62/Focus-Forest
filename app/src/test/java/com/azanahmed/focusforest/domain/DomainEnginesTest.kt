package com.azanahmed.focusforest.domain

import com.azanahmed.focusforest.domain.engine.AchievementEngine
import com.azanahmed.focusforest.domain.engine.CalendarDayKind
import com.azanahmed.focusforest.domain.engine.ChallengeEngine
import com.azanahmed.focusforest.domain.engine.ForestGrowthEngine
import com.azanahmed.focusforest.domain.engine.InsightsAnalyticsEngine
import com.azanahmed.focusforest.domain.engine.StreakCalculator
import com.azanahmed.focusforest.domain.engine.TimeOfDayBucket
import com.azanahmed.focusforest.domain.engine.XpLevelEngine
import com.azanahmed.focusforest.domain.model.AchievementType
import com.azanahmed.focusforest.domain.model.ChainConfig
import com.azanahmed.focusforest.domain.model.ChallengeMetricType
import com.azanahmed.focusforest.domain.model.ChallengePeriod
import com.azanahmed.focusforest.domain.model.ChallengeStatus
import com.azanahmed.focusforest.domain.model.DailyStats
import com.azanahmed.focusforest.domain.model.FocusSession
import com.azanahmed.focusforest.domain.model.GrowthStage
import com.azanahmed.focusforest.domain.model.PersonalRecords
import com.azanahmed.focusforest.domain.model.SessionStatus
import com.azanahmed.focusforest.domain.model.SessionType
import com.azanahmed.focusforest.domain.model.UsageBaselineStatus
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
    fun `extreme lifetime xp cannot overflow level calculation`() {
        val cappedThreshold = XpLevelEngine.xpRequiredForLevel(Int.MAX_VALUE)
        assertTrue(cappedThreshold > 0)

        val level = XpLevelEngine.levelForXp(Int.MAX_VALUE)
        assertTrue(level in 2..10_000)
        assertTrue(XpLevelEngine.xpRequiredForLevel(level) <= Int.MAX_VALUE)
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
    fun `category and routine insights stay inside the supplied window and keep snapshot names`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 11, 16)
        fun session(
            id: String,
            type: SessionType,
            minutes: Int,
            status: SessionStatus = SessionStatus.COMPLETED,
            categoryId: String? = null,
            categoryName: String = "",
            routineId: String? = null,
            routineName: String = ""
        ) = FocusSession(
            id = id,
            sessionType = type,
            plannedMinutes = minutes,
            completedMinutes = minutes,
            status = status,
            startedAtWallMs = 1_763_300_000_000L,
            endedAtWallMs = 1_763_300_000_000L + minutes * 60_000L,
            localDate = "2026-11-16",
            xpAwarded = if (status == SessionStatus.COMPLETED) 50 else 0,
            seedsAwarded = if (status == SessionStatus.COMPLETED) 2 else 0,
            categoryId = categoryId,
            categoryNameSnapshot = categoryName,
            routineId = routineId,
            routineNameSnapshot = routineName
        )

        // Newest first, as the repository returns them. The second Study row carries the newer
        // snapshot name; ids are never resolved against the current categories table.
        val newestStudy = session(
            "s2", SessionType.DEEP_WORK, 50,
            categoryId = "cat_study", categoryName = "Study Renamed",
            routineId = "routine_study", routineName = "Study"
        )
        val olderStudy = session(
            "s1", SessionType.POMODORO, 25,
            categoryId = "cat_study", categoryName = "Study",
            routineId = "routine_study", routineName = "Study"
        )
        val coding = session(
            "s3", SessionType.CUSTOM, 35,
            categoryId = "cat_coding", categoryName = "Coding",
            routineId = "routine_deep_work", routineName = "Deep Work"
        )
        val unlabelled = session("s4", SessionType.POMODORO, 25)
        val deletedLabel = session(
            "s7", SessionType.POMODORO, 15,
            categoryId = "cat_deleted", categoryName = "Old Study",
            routineId = "routine_deleted", routineName = "Old Routine"
        )
        val aborted = session(
            "s5", SessionType.POMODORO, 25, status = SessionStatus.ABORTED,
            categoryId = "cat_study", categoryName = "Study"
        )
        val breakSession = session(
            "s6", SessionType.SHORT_BREAK, 5,
            categoryId = "cat_study", categoryName = "Study",
            routineId = "routine_study", routineName = "Study"
        )
        val window = listOf(newestStudy, olderStudy, coding, unlabelled, deletedLabel)

        val insights = InsightsAnalyticsEngine.computeAdvancedInsights(
            sessions = window + listOf(aborted, breakSession),
            dailyStats = emptyList(),
            today = today,
            zoneId = zone,
            sessionsForBreakdown = window
        )

        assertEquals(5, insights.completedFocusSampleCount)
        assertEquals(30, insights.averageCompletedFocusMinutes)
        assertEquals(4, insights.categorizedSessionCount)
        assertEquals(4, insights.routineSessionCount)

        assertEquals(listOf("Study Renamed", "Coding", "Old Study"), insights.categoryBreakdown.map { it.name })
        assertEquals(listOf(2, 1, 1), insights.categoryBreakdown.map { it.count })
        assertEquals(listOf(75, 35, 15), insights.categoryBreakdown.map { it.minutes })
        assertEquals(0.6f, insights.categoryBreakdown[0].shareFraction, 0.0001f)
        assertEquals("cat_deleted", insights.categoryBreakdown[2].id)

        assertEquals(listOf("Study", "Deep Work", "Old Routine"), insights.routineBreakdown.map { it.name })
        assertEquals(listOf(75, 35, 15), insights.routineBreakdown.map { it.minutes })

        // A narrower window recomputes averages and labels instead of reusing all-time numbers.
        val narrow = InsightsAnalyticsEngine.computeAdvancedInsights(
            sessions = window + listOf(aborted, breakSession),
            dailyStats = emptyList(),
            today = today,
            zoneId = zone,
            sessionsForBreakdown = listOf(coding)
        )
        assertEquals(1, narrow.completedFocusSampleCount)
        assertEquals(35, narrow.averageCompletedFocusMinutes)
        assertEquals(listOf("Coding"), narrow.categoryBreakdown.map { it.name })
        assertEquals(listOf("Deep Work"), narrow.routineBreakdown.map { it.name })
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

    @Test
    fun `forest next-stage hint is honest at every threshold`() {
        assertEquals(3, ForestGrowthEngine.seedsToNextStage(0))
        assertEquals(7, ForestGrowthEngine.seedsToNextStage(3))
        assertEquals(15, ForestGrowthEngine.seedsToNextStage(10))
        assertEquals(30, ForestGrowthEngine.seedsToNextStage(25))
        assertEquals(45, ForestGrowthEngine.seedsToNextStage(55))
        assertEquals(null, ForestGrowthEngine.seedsToNextStage(100))
        assertEquals(1, ForestGrowthEngine.forestLevelForSeeds(0))
        assertEquals(1, ForestGrowthEngine.forestLevelForSeeds(11))
        assertEquals(2, ForestGrowthEngine.forestLevelForSeeds(12))
        assertEquals(3, ForestGrowthEngine.forestLevelForSeeds(24))
    }
}

