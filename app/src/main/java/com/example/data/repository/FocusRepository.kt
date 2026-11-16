package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.time.ClockProvider
import com.example.data.local.datastore.UserPreferencesDataStore
import com.example.data.local.db.FocusForestDatabase
import com.example.data.local.db.entities.AchievementEntity
import com.example.data.local.db.entities.ChallengeProgressEntity
import com.example.data.local.db.entities.DailyStatsEntity
import com.example.data.local.db.entities.FocusSessionEntity
import com.example.data.local.db.entities.ForestProgressEntity
import com.example.data.local.db.entities.ForestTreeEntity
import com.example.data.local.db.entities.UserProfileEntity
import com.example.domain.engine.AchievementEngine
import com.example.domain.engine.ChallengeEngine
import com.example.domain.engine.FocusTimerEngine
import com.example.domain.engine.ForestGrowthEngine
import com.example.domain.engine.StreakCalculator
import com.example.domain.engine.XpLevelEngine
import com.example.domain.model.AchievementItem
import com.example.domain.model.AchievementType
import com.example.domain.model.ActiveTimerState
import com.example.domain.model.AvatarStyle
import com.example.domain.model.ChallengeItem
import com.example.domain.model.ChallengePeriod
import com.example.domain.model.ChallengeStatus
import com.example.domain.model.DailyStats
import com.example.domain.model.FocusSession
import com.example.domain.model.ForestThemeSkin
import com.example.domain.model.PersonalRecords
import com.example.domain.model.SessionStatus
import com.example.domain.model.SessionType
import com.example.domain.model.UsageBaselineStatus
import com.example.domain.model.UserIntention
import com.example.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class SessionCompletionOutcome(
    val session: FocusSession,
    val newlyUnlockedAchievements: List<AchievementType>,
    val newlyCompletedChallenges: List<ChallengeItem>,
    val streakBonusXp: Int,
    val newLevel: Int,
    val leveledUp: Boolean,
    val duplicateSuppressed: Boolean = false
)

class FocusRepository(
    private val db: FocusForestDatabase,
    private val prefs: UserPreferencesDataStore,
    private val clockProvider: ClockProvider
) {
    private val userProfileDao = db.userProfileDao()
    private val focusSessionDao = db.focusSessionDao()
    private val dailyStatsDao = db.dailyStatsDao()
    private val challengeDao = db.challengeDao()
    private val achievementDao = db.achievementDao()
    private val forestDao = db.forestDao()

    val userProfile: Flow<UserProfile?> = userProfileDao.observeProfile().map { entity ->
        entity?.toDomain()
    }

    val allSessions: Flow<List<FocusSession>> = focusSessionDao.observeAllSessions().map { list ->
        list.map { it.toDomain() }
    }

    val allDailyStats: Flow<List<DailyStats>> = dailyStatsDao.observeAllDailyStats().map { list ->
        list.map { it.toDomain() }
    }

    val activeTimerState: Flow<ActiveTimerState?> = prefs.activeTimerState

    val personalRecords: Flow<PersonalRecords> = combine(
        allSessions,
        allDailyStats,
        userProfile
    ) { sessions, dailyStats, profile ->
        val completedFocus = sessions.filter { it.status == SessionStatus.COMPLETED && !it.sessionType.isBreak }
        val totalMinutes = completedFocus.sumOf { it.completedMinutes }
        val longestSession = completedFocus.maxOfOrNull { it.completedMinutes } ?: 0
        val activeDays = dailyStats.count { it.completedSessions > 0 }
        val goalMetDays = dailyStats.count { it.goalMet }
        val completionRate = if (activeDays > 0) ((goalMetDays * 100f) / activeDays).toInt() else 0

        val activeDates = dailyStats.filter { it.completedSessions > 0 }
            .mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }
            .toSet()
        val restDates = dailyStats.filter { it.isRestDay }
            .mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }
            .toSet()
        val streaks = StreakCalculator.calculateStreaks(activeDates, restDates, clockProvider.todayLocalDate())

        PersonalRecords(
            totalFocusMinutes = totalMinutes,
            totalCompletedSessions = completedFocus.size,
            longestSessionMinutes = longestSession,
            currentStreakDays = streaks.currentStreakDays,
            longestStreakDays = maxOf(streaks.longestStreakDays, profile?.longestStreakDays ?: 0),
            goalCompletionRatePercent = completionRate,
            totalActiveDays = activeDays
        )
    }

    val achievements: Flow<List<AchievementItem>> = combine(
        achievementDao.observeAchievements(),
        personalRecords,
        challengeDao.observeChallengeProgress(),
        userProfile
    ) { entities, records, progressList, profile ->
        val map = entities.associate {
            runCatching { AchievementType.valueOf(it.type) }.getOrNull() to if (it.unlocked) it.unlockedAtMs else null
        }.filterKeys { it != null }.mapKeys { it.key!! }
        val completedChallenges = progressList.count { it.status == ChallengeStatus.COMPLETED.name }
        AchievementEngine.evaluate(
            existing = map,
            records = records,
            completedChallengesCount = completedChallenges,
            currentLevel = profile?.currentLevel ?: 1,
            nowWallClockMs = clockProvider.nowWallClockMs()
        ).first
    }

    suspend fun completeOnboarding(
        displayName: String,
        dailyGoalMinutes: Int,
        intention: UserIntention
    ) {
        val nowMs = clockProvider.nowWallClockMs()
        db.withTransaction {
            val existing = userProfileDao.getProfileOnce()
            val profile = existing?.copy(
                displayName = displayName.trim(),
                primaryIntention = intention.name,
                dailyFocusGoalMinutes = dailyGoalMinutes.coerceIn(10, 480)
            ) ?: UserProfileEntity(
                id = 1,
                displayName = displayName.trim(),
                primaryIntention = intention.name,
                dailyFocusGoalMinutes = dailyGoalMinutes.coerceIn(10, 480),
                lifetimeXp = 0,
                currentLevel = 1,
                currentStreakDays = 0,
                longestStreakDays = 0,
                equippedAvatarId = AvatarStyle.SEEDLING.id,
                equippedThemeId = ForestThemeSkin.CLASSIC_SAGE.id,
                createdAtMs = nowMs
            )
            userProfileDao.upsertProfile(profile)

            if (forestDao.getForestProgressOnce() == null) {
                forestDao.upsertForestProgress(
                    ForestProgressEntity(
                        id = 1,
                        totalSeeds = 0,
                        spendableLeafPoints = 0,
                        forestLevel = 1,
                        totalTreesPlanted = 0,
                        equippedEnvironmentId = "env_emerald_clearing",
                        equippedDecorationsCsv = ""
                    )
                )
            }
        }
        prefs.setDailyGoalMinutes(dailyGoalMinutes)
        prefs.setOnboardingCompleted(true)
    }

    suspend fun updateProfileNameAndGoal(displayName: String, dailyGoalMinutes: Int) {
        db.withTransaction {
            val existing = userProfileDao.getProfileOnce() ?: UserProfileEntity(
                id = 1,
                displayName = displayName.trim(),
                primaryIntention = UserIntention.CONCENTRATION.name,
                dailyFocusGoalMinutes = dailyGoalMinutes,
                lifetimeXp = 0,
                currentLevel = 1,
                currentStreakDays = 0,
                longestStreakDays = 0,
                equippedAvatarId = AvatarStyle.SEEDLING.id,
                equippedThemeId = ForestThemeSkin.CLASSIC_SAGE.id,
                createdAtMs = clockProvider.nowWallClockMs()
            )
            userProfileDao.upsertProfile(
                existing.copy(
                    displayName = displayName.trim(),
                    dailyFocusGoalMinutes = dailyGoalMinutes.coerceIn(10, 480)
                )
            )
        }
        prefs.setDailyGoalMinutes(dailyGoalMinutes)
    }

    suspend fun saveActiveTimerState(state: ActiveTimerState?) {
        prefs.saveActiveTimerState(state)
    }

    suspend fun markRestDay(date: LocalDate = clockProvider.todayLocalDate()) {
        val goal = prefs.dailyGoalMinutes.first()
        db.withTransaction {
            val existing = dailyStatsDao.getDailyStatsForDateOnce(date.toString())
            val updated = existing?.copy(isRestDay = true) ?: DailyStatsEntity(
                localDate = date.toString(),
                focusMinutes = 0,
                completedSessions = 0,
                goalMinutes = goal,
                goalMet = false,
                xpEarned = 0,
                seedsEarned = 0,
                isRestDay = true
            )
            dailyStatsDao.upsertDailyStats(updated)
        }
    }

    suspend fun recordAbortedSession(
        activeState: ActiveTimerState,
        status: SessionStatus = SessionStatus.ABORTED
    ): FocusSession {
        val nowMs = clockProvider.nowWallClockMs()
        val today = clockProvider.todayLocalDate()
        val aborted = FocusTimerEngine.buildAbortedSession(
            state = activeState,
            nowWallClockMs = nowMs,
            localDate = today,
            status = status
        )
        db.withTransaction {
            val existing = focusSessionDao.getSessionById(aborted.id)
            if (existing == null || !existing.rewardGranted) {
                focusSessionDao.upsertSession(aborted.toEntity())
            }
        }
        prefs.saveActiveTimerState(null)
        return aborted
    }

    suspend fun recordCompletedSession(
        activeState: ActiveTimerState,
        usageBaseline: UsageBaselineStatus? = null
    ): SessionCompletionOutcome {
        val nowMs = clockProvider.nowWallClockMs()
        val today = clockProvider.todayLocalDate()
        val todayStr = today.toString()
        val dailyGoal = prefs.dailyGoalMinutes.first()

        val outcome = db.withTransaction {
            val rewardedIds = focusSessionDao.getRewardedSessionIds().toSet()
            val completedSession = FocusTimerEngine.buildCompletedSession(
                state = activeState,
                nowWallClockMs = nowMs,
                localDate = today,
                alreadyRewardedSessionIds = rewardedIds
            )

            // Idempotency guard: if already rewarded, return existing without adding XP/seeds again
            if (!completedSession.rewardGranted) {
                val existing = focusSessionDao.getSessionById(completedSession.id)?.toDomain() ?: completedSession
                val profile = userProfileDao.getProfileOnce()
                return@withTransaction SessionCompletionOutcome(
                    session = existing,
                    newlyUnlockedAchievements = emptyList(),
                    newlyCompletedChallenges = emptyList(),
                    streakBonusXp = 0,
                    newLevel = profile?.currentLevel ?: 1,
                    leveledUp = false,
                    duplicateSuppressed = true
                )
            }

            // 1. Persist completed session
            focusSessionDao.upsertSession(completedSession.toEntity())

            // 2. Update daily stats
            val isFocusWork = !completedSession.sessionType.isBreak
            val prevDaily = dailyStatsDao.getDailyStatsForDateOnce(todayStr)
            val newFocusMin = (prevDaily?.focusMinutes ?: 0) + if (isFocusWork) completedSession.completedMinutes else 0
            val newSessionCount = (prevDaily?.completedSessions ?: 0) + if (isFocusWork) 1 else 0
            val goalMet = newFocusMin >= dailyGoal

            // 3. Plant tree & update forest progress
            val tree = ForestTreeEntity(
                id = completedSession.id,
                species = completedSession.sessionType.treeSpecies.name,
                plantedDate = todayStr,
                focusMinutes = completedSession.completedMinutes,
                plantedAtWallMs = nowMs
            )
            forestDao.insertTreeIfAbsent(tree)

            val prevForest = forestDao.getForestProgressOnce() ?: ForestProgressEntity(
                id = 1,
                totalSeeds = 0,
                spendableLeafPoints = 0,
                forestLevel = 1,
                totalTreesPlanted = 0,
                equippedEnvironmentId = "env_emerald_clearing",
                equippedDecorationsCsv = ""
            )
            var totalSeedsDelta = completedSession.seedsAwarded
            var totalXpDelta = completedSession.xpAwarded

            // 4. Recalculate streaks
            val allStats = dailyStatsDao.getAllDailyStatsOnce()
                .associateBy { it.localDate }
                .toMutableMap()
            val updatedTodayStats = DailyStatsEntity(
                localDate = todayStr,
                focusMinutes = newFocusMin,
                completedSessions = newSessionCount,
                goalMinutes = dailyGoal,
                goalMet = goalMet,
                xpEarned = (prevDaily?.xpEarned ?: 0) + completedSession.xpAwarded,
                seedsEarned = (prevDaily?.seedsEarned ?: 0) + completedSession.seedsAwarded,
                isRestDay = prevDaily?.isRestDay ?: false
            )
            allStats[todayStr] = updatedTodayStats

            val activeDates = allStats.values.filter { it.completedSessions > 0 }
                .mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }
                .toSet()
            val restDates = allStats.values.filter { it.isRestDay }
                .mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }
                .toSet()
            val streakResult = StreakCalculator.calculateStreaks(activeDates, restDates, today)

            val prevProfile = userProfileDao.getProfileOnce() ?: UserProfileEntity(
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
            val streakBonus = if (streakResult.currentStreakDays > prevProfile.currentStreakDays) {
                XpLevelEngine.streakMilestoneBonusXp(streakResult.currentStreakDays)
            } else {
                0
            }
            totalXpDelta += streakBonus

            // 5. Evaluate challenges in transaction
            val allChallengeEntities = challengeDao.getAllChallengesOnce()
            val progressMap = challengeDao.getAllProgressOnce().associateBy { it.challengeId }
            val domainChallenges = allChallengeEntities.map { c ->
                val p = progressMap[c.id]
                c.toDomain(p)
            }
            val allCompletedSessions = focusSessionDao.getAllSessionsOnce().map { it.toDomain() }
            val currentWeekKey = ChallengeEngine.weekKeyFor(today)
            val sessionsByChallenge = domainChallenges.associate { ch ->
                val filtered = when (ch.period) {
                    ChallengePeriod.DAILY -> allCompletedSessions.filter { it.localDate == ch.periodKey }
                    ChallengePeriod.WEEKLY -> allCompletedSessions.filter {
                        val d = runCatching { LocalDate.parse(it.localDate) }.getOrNull()
                        d != null && ChallengeEngine.weekKeyFor(d) == ch.periodKey
                    }
                    ChallengePeriod.CUSTOM -> allCompletedSessions.filter { it.startedAtWallMs >= ch.createdAtMs }
                }
                ch.id to filtered
            }
            val (evaluatedChallenges, newlyCompletedChallenges) = ChallengeEngine.evaluateChallenges(
                challenges = domainChallenges,
                sessionsInPeriodByChallengeId = sessionsByChallenge,
                usageBaseline = usageBaseline,
                nowWallClockMs = nowMs
            )
            challengeDao.upsertProgressList(
                evaluatedChallenges.map {
                    ChallengeProgressEntity(
                        challengeId = it.id,
                        currentProgress = it.currentProgress,
                        status = it.status.name,
                        completedAtMs = it.completedAtMs,
                        rewardClaimed = it.rewardClaimed
                    )
                }
            )
            newlyCompletedChallenges.forEach {
                totalXpDelta += it.rewardXp
                totalSeedsDelta += it.rewardSeeds
            }

            // 6. Save updated daily stats with challenge/streak bonus XP/seeds
            dailyStatsDao.upsertDailyStats(
                updatedTodayStats.copy(
                    xpEarned = (prevDaily?.xpEarned ?: 0) + totalXpDelta,
                    seedsEarned = (prevDaily?.seedsEarned ?: 0) + totalSeedsDelta
                )
            )

            // 7. Update Forest totals
            val newTotalSeeds = prevForest.totalSeeds + totalSeedsDelta
            val newSpendableLeafPoints = prevForest.spendableLeafPoints + totalSeedsDelta
            val newTreesCount = forestDao.getTreesOnce().size
            forestDao.upsertForestProgress(
                prevForest.copy(
                    totalSeeds = newTotalSeeds,
                    spendableLeafPoints = newSpendableLeafPoints,
                    forestLevel = ForestGrowthEngine.forestLevelForSeeds(newTotalSeeds),
                    totalTreesPlanted = newTreesCount
                )
            )

            // 8. Evaluate Achievements
            val totalFocusMinutesAll = allCompletedSessions
                .filter { it.status == SessionStatus.COMPLETED && !it.sessionType.isBreak }
                .sumOf { it.completedMinutes }
            val totalCompletedFocusCount = allCompletedSessions
                .count { it.status == SessionStatus.COMPLETED && !it.sessionType.isBreak }
            val longestSessionMin = allCompletedSessions
                .filter { it.status == SessionStatus.COMPLETED && !it.sessionType.isBreak }
                .maxOfOrNull { it.completedMinutes } ?: 0
            val completedChallengesTotal = evaluatedChallenges.count { it.status == ChallengeStatus.COMPLETED }

            val provisionalXp = prevProfile.lifetimeXp + totalXpDelta
            val provisionalLevel = XpLevelEngine.levelForXp(provisionalXp)
            val existingAchMap = achievementDao.getAchievementsOnce()
                .mapNotNull { entity ->
                    runCatching { AchievementType.valueOf(entity.type) }.getOrNull()?.let { type ->
                        type to if (entity.unlocked) entity.unlockedAtMs else null
                    }
                }.toMap()

            val (_, newlyUnlockedAchievements) = AchievementEngine.evaluate(
                existing = existingAchMap,
                records = PersonalRecords(
                    totalFocusMinutes = totalFocusMinutesAll,
                    totalCompletedSessions = totalCompletedFocusCount,
                    longestSessionMinutes = longestSessionMin,
                    currentStreakDays = streakResult.currentStreakDays,
                    longestStreakDays = maxOf(streakResult.longestStreakDays, prevProfile.longestStreakDays),
                    goalCompletionRatePercent = 0,
                    totalActiveDays = activeDates.size
                ),
                completedChallengesCount = completedChallengesTotal,
                currentLevel = provisionalLevel,
                nowWallClockMs = nowMs
            )

            newlyUnlockedAchievements.forEach { type ->
                achievementDao.upsertAchievement(
                    AchievementEntity(
                        type = type.name,
                        unlocked = true,
                        unlockedAtMs = nowMs
                    )
                )
                totalXpDelta += type.bonusXp
            }

            // 9. Update UserProfile XP & Level
            val finalLifetimeXp = prevProfile.lifetimeXp + totalXpDelta
            val finalLevel = XpLevelEngine.levelForXp(finalLifetimeXp)
            val updatedProfile = prevProfile.copy(
                lifetimeXp = finalLifetimeXp,
                currentLevel = finalLevel,
                currentStreakDays = streakResult.currentStreakDays,
                longestStreakDays = maxOf(streakResult.longestStreakDays, prevProfile.longestStreakDays)
            )
            userProfileDao.upsertProfile(updatedProfile)

            SessionCompletionOutcome(
                session = completedSession.copy(
                    xpAwarded = completedSession.xpAwarded,
                    seedsAwarded = completedSession.seedsAwarded
                ),
                newlyUnlockedAchievements = newlyUnlockedAchievements,
                newlyCompletedChallenges = newlyCompletedChallenges,
                streakBonusXp = streakBonus,
                newLevel = finalLevel,
                leveledUp = finalLevel > prevProfile.currentLevel
            )
        }

        prefs.saveActiveTimerState(null)
        return outcome
    }
}
