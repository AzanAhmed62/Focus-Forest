package com.azanahmed.focusforest.data.repository

import com.azanahmed.focusforest.core.time.ClockProvider
import com.azanahmed.focusforest.data.local.db.FocusForestDatabase
import com.azanahmed.focusforest.data.local.db.entities.AchievementEntity
import com.azanahmed.focusforest.data.local.db.entities.ChallengeProgressEntity
import com.azanahmed.focusforest.data.local.db.entities.DailyStatsEntity
import com.azanahmed.focusforest.data.local.db.entities.ForestProgressEntity
import com.azanahmed.focusforest.data.local.db.entities.RewardLedgerEntity
import com.azanahmed.focusforest.data.local.db.entities.UserProfileEntity
import com.azanahmed.focusforest.domain.engine.AchievementEngine
import com.azanahmed.focusforest.domain.engine.ChallengeEvaluationOutput
import com.azanahmed.focusforest.domain.engine.ForestGrowthEngine
import com.azanahmed.focusforest.domain.engine.XpLevelEngine
import com.azanahmed.focusforest.domain.model.AchievementType
import com.azanahmed.focusforest.domain.model.AvatarStyle
import com.azanahmed.focusforest.domain.model.ChallengeItem
import com.azanahmed.focusforest.domain.model.ChallengeStatus
import com.azanahmed.focusforest.domain.model.ForestThemeSkin
import com.azanahmed.focusforest.domain.model.PersonalRecords
import com.azanahmed.focusforest.domain.model.UserIntention

/**
 * Single transactional authority for granting XP, Seeds, Leaf Points, and Achievements.
 * Every reward credit is guarded by a unique [RewardLedgerEntity.rewardKey] so retries,
 * concurrent worker + ViewModel completions, or repeated evaluations can never double-credit.
 */
class RewardAuthority(
    private val db: FocusForestDatabase,
    private val clockProvider: ClockProvider
) {
    private val rewardLedgerDao = db.rewardLedgerDao()
    private val challengeDao = db.challengeDao()
    private val userProfileDao = db.userProfileDao()
    private val dailyStatsDao = db.dailyStatsDao()
    private val forestDao = db.forestDao()
    private val achievementDao = db.achievementDao()

    companion object {
        const val SOURCE_SESSION = "SESSION"
        const val SOURCE_DAILY_CHALLENGE = "DAILY_CHALLENGE"
        const val SOURCE_WEEKLY_CHALLENGE = "WEEKLY_CHALLENGE"
        const val SOURCE_CUSTOM_CHALLENGE = "CUSTOM_CHALLENGE"
        const val SOURCE_ACHIEVEMENT = "ACHIEVEMENT"
        const val SOURCE_STREAK_MILESTONE = "STREAK_MILESTONE"
        const val MAX_REWARDED_CUSTOM_CHALLENGES_PER_DAY = 3
    }

    data class CreditedChallengeResult(
        val newlyCreditedChallenges: List<ChallengeItem>,
        val xpAdded: Int,
        val seedsAdded: Int
    )

    suspend fun tryClaimSessionLedger(
        sessionId: String,
        xpDelta: Int,
        seedsDelta: Int,
        nowMs: Long
    ): Boolean {
        val inserted = rewardLedgerDao.insertIfAbsent(
            RewardLedgerEntity(
                rewardKey = "session:$sessionId",
                sourceType = SOURCE_SESSION,
                sourceId = sessionId,
                xpDelta = xpDelta,
                seedsDelta = seedsDelta,
                createdAtMs = nowMs
            )
        )
        return inserted != -1L
    }

    suspend fun tryClaimStreakMilestone(
        streakDays: Int,
        nowMs: Long
    ): Int {
        val bonus = XpLevelEngine.streakMilestoneBonusXp(streakDays)
        if (bonus <= 0) return 0
        val inserted = rewardLedgerDao.insertIfAbsent(
            RewardLedgerEntity(
                rewardKey = "streak_milestone:$streakDays",
                sourceType = SOURCE_STREAK_MILESTONE,
                sourceId = streakDays.toString(),
                xpDelta = bonus,
                seedsDelta = 0,
                createdAtMs = nowMs
            )
        )
        return if (inserted != -1L) bonus else 0
    }

    /**
     * Persists challenge evaluation progress AND atomically claims XP/Seeds via [rewardLedgerDao]
     * inside an enclosing DB transaction.
     */
    suspend fun creditChallengeEvaluationInTransaction(
        evalOutput: ChallengeEvaluationOutput,
        nowMs: Long
    ): CreditedChallengeResult {
        val today = clockProvider.todayLocalDate()
        val startOfDayMs = today.atStartOfDay(clockProvider.zoneId()).toInstant().toEpochMilli()
        val needingMap = evalOutput.challengesNeedingReward.associateBy { it.id }

        val creditedList = mutableListOf<ChallengeItem>()
        var xpAdded = 0
        var seedsAdded = 0
        var customRewardCountToday = rewardLedgerDao.countBySourceSince(SOURCE_CUSTOM_CHALLENGE, startOfDayMs)

        val progressToPersist = evalOutput.evaluatedChallenges.map { item ->
            val candidate = needingMap[item.id]
            if (candidate == null) {
                ChallengeProgressEntity(
                    challengeId = item.id,
                    currentProgress = item.currentProgress,
                    status = item.status.name,
                    completedAtMs = item.completedAtMs,
                    rewardClaimed = item.rewardClaimed
                )
            } else {
                val sourceType = when {
                    candidate.isCustom -> SOURCE_CUSTOM_CHALLENGE
                    candidate.period.name == "WEEKLY" -> SOURCE_WEEKLY_CHALLENGE
                    else -> SOURCE_DAILY_CHALLENGE
                }
                val isCappedCustom = candidate.isCustom &&
                    customRewardCountToday >= MAX_REWARDED_CUSTOM_CHALLENGES_PER_DAY
                val effectiveXp = if (isCappedCustom) 0 else candidate.rewardXp
                val effectiveSeeds = if (isCappedCustom) 0 else candidate.rewardSeeds

                val inserted = rewardLedgerDao.insertIfAbsent(
                    RewardLedgerEntity(
                        rewardKey = "challenge:${candidate.id}",
                        sourceType = sourceType,
                        sourceId = candidate.id,
                        xpDelta = effectiveXp,
                        seedsDelta = effectiveSeeds,
                        createdAtMs = nowMs
                    )
                )
                if (inserted != -1L) {
                    if (candidate.isCustom && effectiveXp > 0) {
                        customRewardCountToday++
                    }
                    xpAdded += effectiveXp
                    seedsAdded += effectiveSeeds
                    creditedList.add(candidate.copy(rewardClaimed = true))
                }
                ChallengeProgressEntity(
                    challengeId = candidate.id,
                    currentProgress = candidate.currentProgress,
                    status = ChallengeStatus.COMPLETED.name,
                    completedAtMs = candidate.completedAtMs ?: nowMs,
                    rewardClaimed = true
                )
            }
        }

        challengeDao.upsertProgressList(progressToPersist)
        return CreditedChallengeResult(
            newlyCreditedChallenges = creditedList,
            xpAdded = xpAdded,
            seedsAdded = seedsAdded
        )
    }

    /**
     * Applies challenge rewards and checks resulting achievements when [ChallengeRepository.refreshProgress]
     * triggers completions outside of session finish (e.g. usage-reduction challenges).
     */
    suspend fun applyChallengeEvaluationAndSyncTotalsInTransaction(
        evalOutput: ChallengeEvaluationOutput,
        records: PersonalRecords,
        nowMs: Long,
        defaultGoalMinutes: Int
    ): CreditedChallengeResult {
        val creditResult = creditChallengeEvaluationInTransaction(evalOutput, nowMs)
        if (creditResult.xpAdded == 0 && creditResult.seedsAdded == 0) {
            return creditResult
        }

        val todayStr = clockProvider.todayLocalDate().toString()
        val prevProfile = userProfileDao.getProfileOnce() ?: defaultProfile(defaultGoalMinutes, nowMs)
        val prevForest = forestDao.getForestProgressOnce() ?: defaultForest()
        val prevDaily = dailyStatsDao.getDailyStatsForDateOnce(todayStr)

        var totalXpDelta = creditResult.xpAdded
        val completedCount = evalOutput.evaluatedChallenges.count {
            it.status == ChallengeStatus.COMPLETED && !it.isArchived
        }
        val achXpAndUnlocked = evaluateAndCreditAchievementsInTransaction(
            records = records,
            completedChallengesCount = completedCount,
            provisionalLevel = XpLevelEngine.levelForXp(prevProfile.lifetimeXp + totalXpDelta),
            nowMs = nowMs
        )
        totalXpDelta += achXpAndUnlocked.first

        val finalXp = prevProfile.lifetimeXp + totalXpDelta
        val finalLevel = XpLevelEngine.levelForXp(finalXp)
        userProfileDao.upsertProfile(
            prevProfile.copy(
                lifetimeXp = finalXp,
                currentLevel = finalLevel
            )
        )

        val newTotalSeeds = prevForest.totalSeeds + creditResult.seedsAdded
        val newSpendable = prevForest.spendableLeafPoints + creditResult.seedsAdded
        val realTrees = forestDao.getTreesOnce().size
        forestDao.upsertForestProgress(
            prevForest.copy(
                totalSeeds = newTotalSeeds,
                spendableLeafPoints = newSpendable,
                forestLevel = ForestGrowthEngine.forestLevelForSeeds(newTotalSeeds),
                totalTreesPlanted = realTrees
            )
        )

        val updatedDaily = (prevDaily ?: DailyStatsEntity(
            localDate = todayStr,
            focusMinutes = 0,
            completedSessions = 0,
            goalMinutes = defaultGoalMinutes,
            goalMet = false,
            xpEarned = 0,
            seedsEarned = 0,
            isRestDay = false
        )).let {
            it.copy(
                xpEarned = it.xpEarned + totalXpDelta,
                seedsEarned = it.seedsEarned + creditResult.seedsAdded
            )
        }
        dailyStatsDao.upsertDailyStats(updatedDaily)

        return creditResult
    }

    suspend fun evaluateAndCreditAchievementsInTransaction(
        records: PersonalRecords,
        completedChallengesCount: Int,
        provisionalLevel: Int,
        nowMs: Long
    ): Pair<Int, List<AchievementType>> {
        val existingAchMap = achievementDao.getAchievementsOnce()
            .mapNotNull { entity ->
                runCatching { AchievementType.valueOf(entity.type) }.getOrNull()?.let { type ->
                    type to if (entity.unlocked) entity.unlockedAtMs else null
                }
            }.toMap()

        val (_, newlyUnlocked) = AchievementEngine.evaluate(
            existing = existingAchMap,
            records = records,
            completedChallengesCount = completedChallengesCount,
            currentLevel = provisionalLevel,
            nowWallClockMs = nowMs
        )

        var bonusXp = 0
        val confirmedUnlocked = mutableListOf<AchievementType>()
        newlyUnlocked.forEach { type ->
            val inserted = rewardLedgerDao.insertIfAbsent(
                RewardLedgerEntity(
                    rewardKey = "achievement:${type.name}",
                    sourceType = SOURCE_ACHIEVEMENT,
                    sourceId = type.name,
                    xpDelta = type.bonusXp,
                    seedsDelta = 0,
                    createdAtMs = nowMs
                )
            )
            achievementDao.upsertAchievement(
                AchievementEntity(
                    type = type.name,
                    unlocked = true,
                    unlockedAtMs = nowMs
                )
            )
            if (inserted != -1L) {
                bonusXp += type.bonusXp
                confirmedUnlocked.add(type)
            }
        }
        return bonusXp to confirmedUnlocked
    }

    fun defaultProfile(dailyGoal: Int, nowMs: Long): UserProfileEntity = UserProfileEntity(
        id = 1,
        displayName = "",
        primaryIntention = UserIntention.CONCENTRATION.name,
        dailyFocusGoalMinutes = dailyGoal,
        lifetimeXp = 0,
        currentLevel = 1,
        currentStreakDays = 0,
        longestStreakDays = 0,
        equippedAvatarId = AvatarStyle.SEEDLING.id,
        equippedThemeId = ForestThemeSkin.CLASSIC_SAGE.id,
        createdAtMs = nowMs
    )

    fun defaultForest(): ForestProgressEntity = ForestProgressEntity(
        id = 1,
        totalSeeds = 0,
        spendableLeafPoints = 0,
        forestLevel = 1,
        totalTreesPlanted = 0,
        equippedEnvironmentId = "env_emerald_clearing",
        equippedDecorationsCsv = ""
    )
}
