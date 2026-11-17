package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.time.ClockProvider
import com.example.data.local.db.FocusForestDatabase
import com.example.data.local.db.entities.ChallengeProgressEntity
import com.example.domain.engine.ChallengeEngine
import com.example.domain.engine.StreakCalculator
import com.example.domain.model.ChallengeIconType
import com.example.domain.model.ChallengeItem
import com.example.domain.model.ChallengeMetricType
import com.example.domain.model.ChallengePeriod
import com.example.domain.model.ChallengeStatus
import com.example.domain.model.PersonalRecords
import com.example.domain.model.SessionStatus
import com.example.domain.model.UsageBaselineStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.util.UUID

sealed class ChallengeMutationResult {
    data object Success : ChallengeMutationResult()
    data class ValidationError(val message: String) : ChallengeMutationResult()
    data class Rejected(val message: String) : ChallengeMutationResult()
}

class ChallengeRepository(
    private val db: FocusForestDatabase,
    private val clockProvider: ClockProvider,
    private val rewardAuthority: RewardAuthority = RewardAuthority(db, clockProvider)
) {
    private val challengeDao = db.challengeDao()
    private val focusSessionDao = db.focusSessionDao()
    private val dailyStatsDao = db.dailyStatsDao()
    private val userProfileDao = db.userProfileDao()

    val challenges: Flow<List<ChallengeItem>> = combine(
        challengeDao.observeChallenges(),
        challengeDao.observeChallengeProgress()
    ) { entities, progressList ->
        val progressById = progressList.associateBy { it.challengeId }
        entities
            .filter { !it.isArchived }
            .map { it.toDomain(progressById[it.id]) }
    }

    suspend fun ensureChallengesGenerated(baselineReady: Boolean) {
        val today = clockProvider.todayLocalDate()
        val todayKey = today.toString()
        val weekKey = ChallengeEngine.weekKeyFor(today)
        val nowMs = clockProvider.nowWallClockMs()

        db.withTransaction {
            val existingDaily = challengeDao.getChallengesForPeriod(todayKey)
            if (existingDaily.isEmpty()) {
                val daily = ChallengeEngine.generateDailyChallenges(today, nowMs, baselineReady)
                challengeDao.insertChallengesIfAbsent(daily.map { it.toEntity() })
                challengeDao.insertProgressIfAbsent(
                    daily.map {
                        ChallengeProgressEntity(
                            challengeId = it.id,
                            currentProgress = 0,
                            status = ChallengeStatus.ACTIVE.name,
                            completedAtMs = null,
                            rewardClaimed = false
                        )
                    }
                )
            }

            val existingWeekly = challengeDao.getChallengesForPeriod(weekKey)
            if (existingWeekly.isEmpty()) {
                val weekly = ChallengeEngine.generateWeeklyChallenges(today, nowMs, baselineReady)
                challengeDao.insertChallengesIfAbsent(weekly.map { it.toEntity() })
                challengeDao.insertProgressIfAbsent(
                    weekly.map {
                        ChallengeProgressEntity(
                            challengeId = it.id,
                            currentProgress = 0,
                            status = ChallengeStatus.ACTIVE.name,
                            completedAtMs = null,
                            rewardClaimed = false
                        )
                    }
                )
            }
        }
    }

    suspend fun createCustomPersonalChallenge(
        title: String,
        description: String,
        metricType: ChallengeMetricType,
        targetValue: Int
    ): ChallengeMutationResult {
        val validation = ChallengeEngine.validateCustomChallenge(title, metricType, targetValue)
        if (!validation.isValid) {
            return ChallengeMutationResult.ValidationError(validation.errorMessage ?: "Invalid challenge parameters.")
        }

        val nowMs = clockProvider.nowWallClockMs()
        val id = "custom_${UUID.randomUUID()}"
        val rewardXp = computeRewardXp(metricType, targetValue)
        val rewardSeeds = (rewardXp / 20).coerceIn(1, 8)
        val unitText = ChallengeEngine.targetBoundsFor(metricType).third

        val item = ChallengeItem(
            id = id,
            title = title.trim(),
            description = description.trim().ifBlank { "Reach $targetValue $unitText." },
            iconType = ChallengeIconType.SPARK,
            period = ChallengePeriod.CUSTOM,
            metricType = metricType,
            targetValue = targetValue,
            currentProgress = 0,
            rewardXp = rewardXp,
            rewardSeeds = rewardSeeds,
            status = ChallengeStatus.ACTIVE,
            periodKey = "custom",
            createdAtMs = nowMs,
            completedAtMs = null,
            rewardClaimed = false
        )
        db.withTransaction {
            challengeDao.upsertChallenge(item.toEntity())
            challengeDao.upsertProgress(
                ChallengeProgressEntity(
                    challengeId = item.id,
                    currentProgress = 0,
                    status = ChallengeStatus.ACTIVE.name,
                    completedAtMs = null,
                    rewardClaimed = false
                )
            )
        }
        return ChallengeMutationResult.Success
    }

    suspend fun editCustomPersonalChallenge(
        challengeId: String,
        newTitle: String,
        newDescription: String,
        newTargetValue: Int
    ): ChallengeMutationResult {
        return db.withTransaction {
            val entity = challengeDao.getChallengeById(challengeId)
                ?: return@withTransaction ChallengeMutationResult.Rejected("Challenge not found.")
            if (entity.period != ChallengePeriod.CUSTOM.name) {
                return@withTransaction ChallengeMutationResult.Rejected("Only custom personal challenges can be edited.")
            }
            val progress = challengeDao.getProgressById(challengeId)
            val domain = entity.toDomain(progress)
            if (domain.rewardClaimed || domain.status != ChallengeStatus.ACTIVE) {
                return@withTransaction ChallengeMutationResult.Rejected("Completed or rewarded challenges cannot be modified.")
            }

            val validation = ChallengeEngine.validateCustomChallenge(newTitle, domain.metricType, newTargetValue)
            if (!validation.isValid) {
                return@withTransaction ChallengeMutationResult.ValidationError(validation.errorMessage ?: "Invalid target or title.")
            }
            if (newTargetValue < domain.currentProgress) {
                return@withTransaction ChallengeMutationResult.ValidationError(
                    "Target cannot be lower than current progress (${domain.currentProgress})."
                )
            }

            val newXp = computeRewardXp(domain.metricType, newTargetValue)
            val newSeeds = (newXp / 20).coerceIn(1, 8)
            challengeDao.upsertChallenge(
                entity.copy(
                    title = newTitle.trim(),
                    description = newDescription.trim().ifBlank { domain.description },
                    targetValue = newTargetValue,
                    rewardXp = newXp,
                    rewardSeeds = newSeeds
                )
            )
            ChallengeMutationResult.Success
        }
    }

    suspend fun deleteOrArchiveCustomChallenge(challengeId: String): ChallengeMutationResult {
        return db.withTransaction {
            val entity = challengeDao.getChallengeById(challengeId)
                ?: return@withTransaction ChallengeMutationResult.Rejected("Challenge not found.")
            if (entity.period != ChallengePeriod.CUSTOM.name) {
                return@withTransaction ChallengeMutationResult.Rejected("System challenges cannot be deleted.")
            }
            val progress = challengeDao.getProgressById(challengeId)
            if (progress?.rewardClaimed == true) {
                // Keep ledger integrity and archive rather than deleting claimed reward history
                challengeDao.archiveChallenge(challengeId)
            } else {
                challengeDao.deleteProgressById(challengeId)
                challengeDao.deleteChallengeById(challengeId)
            }
            ChallengeMutationResult.Success
        }
    }

    suspend fun refreshProgress(usageBaseline: UsageBaselineStatus?): List<ChallengeItem> {
        val nowMs = clockProvider.nowWallClockMs()
        val today = clockProvider.todayLocalDate()
        return db.withTransaction {
            val allEntities = challengeDao.getAllChallengesOnce().filter { !it.isArchived }
            if (allEntities.isEmpty()) return@withTransaction emptyList()

            val progressMap = challengeDao.getAllProgressOnce().associateBy { it.challengeId }
            val domain = allEntities.map { it.toDomain(progressMap[it.id]) }
            val sessions = focusSessionDao.getAllSessionsOnce().map { it.toDomain() }
            val dailyStats = dailyStatsDao.getAllDailyStatsOnce().map { it.toDomain() }
            val profile = userProfileDao.getProfileOnce()

            val sessionsByChallenge = domain.associate { ch ->
                val matching = when (ch.period) {
                    ChallengePeriod.DAILY -> sessions.filter { it.localDate == ch.periodKey }
                    ChallengePeriod.WEEKLY -> sessions.filter {
                        val d = runCatching { LocalDate.parse(it.localDate) }.getOrNull()
                        d != null && ChallengeEngine.weekKeyFor(d) == ch.periodKey
                    }
                    ChallengePeriod.CUSTOM -> sessions.filter { it.startedAtWallMs >= ch.createdAtMs }
                }
                ch.id to matching
            }

            val evalOutput = ChallengeEngine.evaluate(
                challenges = domain,
                sessionsInPeriodByChallengeId = sessionsByChallenge,
                usageBaseline = usageBaseline,
                todayDate = today,
                nowWallClockMs = nowMs
            )

            val recordsSnapshot = buildRecordsSnapshot(sessions, dailyStats, profile?.longestStreakDays ?: 0, today)
            val result = rewardAuthority.applyChallengeEvaluationAndSyncTotalsInTransaction(
                evalOutput = evalOutput,
                records = recordsSnapshot,
                nowMs = nowMs,
                defaultGoalMinutes = profile?.dailyFocusGoalMinutes ?: 50
            )
            result.newlyCreditedChallenges
        }
    }

    private fun buildRecordsSnapshot(
        sessions: List<com.example.domain.model.FocusSession>,
        dailyStats: List<com.example.domain.model.DailyStats>,
        prevLongestStreak: Int,
        today: LocalDate
    ): PersonalRecords {
        val completedFocus = sessions.filter { it.status == SessionStatus.COMPLETED && !it.sessionType.isBreak }
        val activeDates = dailyStats.filter { it.completedSessions > 0 }
            .mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }
            .toSet()
        val restDates = dailyStats.filter { it.isRestDay }
            .mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }
            .toSet()
        val streaks = StreakCalculator.calculateStreaks(activeDates, restDates, today)
        val activeDays = dailyStats.count { it.completedSessions > 0 }
        val goalMetDays = dailyStats.count { it.goalMet }
        return PersonalRecords(
            totalFocusMinutes = completedFocus.sumOf { it.completedMinutes },
            totalCompletedSessions = completedFocus.size,
            longestSessionMinutes = completedFocus.maxOfOrNull { it.completedMinutes } ?: 0,
            currentStreakDays = streaks.currentStreakDays,
            longestStreakDays = maxOf(streaks.longestStreakDays, prevLongestStreak),
            goalCompletionRatePercent = if (activeDays > 0) ((goalMetDays * 100f) / activeDays).toInt() else 0,
            totalActiveDays = activeDays
        )
    }

    private fun computeRewardXp(metricType: ChallengeMetricType, targetValue: Int): Int = when (metricType) {
        ChallengeMetricType.FOCUS_MINUTES,
        ChallengeMetricType.WEEKLY_FOCUS_GOAL -> (targetValue / 2).coerceIn(25, 160)
        ChallengeMetricType.DAILY_USAGE_BUDGET -> (targetValue * 30).coerceIn(30, 150)
        else -> (targetValue * 20).coerceIn(25, 160)
    }
}
