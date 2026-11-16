package com.example.domain

import com.example.domain.engine.AchievementEngine
import com.example.domain.engine.ChallengeEngine
import com.example.domain.engine.ForestGrowthEngine
import com.example.domain.engine.StreakCalculator
import com.example.domain.engine.XpLevelEngine
import com.example.domain.model.AchievementType
import com.example.domain.model.ChallengeStatus
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

class DomainEnginesTest {

    @Test
    fun `xp and seed calculations match session types`() {
        val pomodoro = XpLevelEngine.calculateSessionReward(SessionType.POMODORO, 25)
        assertEquals(50, pomodoro.xp)
        assertEquals(2, pomodoro.seeds)

        val deepWork = XpLevelEngine.calculateSessionReward(SessionType.DEEP_WORK, 50)
        assertEquals(120, deepWork.xp)
        assertEquals(5, deepWork.seeds)

        val shortBreak = XpLevelEngine.calculateSessionReward(SessionType.SHORT_BREAK, 5)
        assertEquals(10, shortBreak.xp)
        assertEquals(1, shortBreak.seeds)

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
        // Yesterday + day before active, today not yet completed -> 2-day streak alive
        val activeOnlyPast = setOf(today.minusDays(1), today.minusDays(2))
        val res1 = StreakCalculator.calculateStreaks(activeOnlyPast, emptySet(), today)
        assertEquals(2, res1.currentStreakDays)

        // Rest day bridges a gap without resetting
        val withRest = setOf(today, today.minusDays(2), today.minusDays(3))
        val restDays = setOf(today.minusDays(1))
        val res2 = StreakCalculator.calculateStreaks(withRest, restDays, today)
        assertEquals(3, res2.currentStreakDays)

        // 2 consecutive missed days without rest breaks streak
        val broken = setOf(today, today.minusDays(3), today.minusDays(4))
        val res3 = StreakCalculator.calculateStreaks(broken, emptySet(), today)
        assertEquals(1, res3.currentStreakDays)
        assertEquals(2, res3.longestStreakDays)
    }

    @Test
    fun `daily challenges are deterministic per date and reward idempotently`() {
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
        val (eval1, newlyDone1) = ChallengeEngine.evaluateChallenges(
            challenges = firstGen,
            sessionsInPeriodByChallengeId = sessionsByChallenge,
            usageBaseline = UsageBaselineStatus(0, 7, null, null, null),
            nowWallClockMs = 5000L
        )
        // Re-evaluating same completed challenges must NOT trigger duplicate rewards
        val (_, newlyDone2) = ChallengeEngine.evaluateChallenges(
            challenges = eval1,
            sessionsInPeriodByChallengeId = sessionsByChallenge,
            usageBaseline = null,
            nowWallClockMs = 6000L
        )
        assertTrue(eval1.any { it.status == ChallengeStatus.COMPLETED })
        assertTrue(newlyDone1.isNotEmpty())
        assertTrue(newlyDone2.isEmpty())
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
