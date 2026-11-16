package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.time.ClockProvider
import com.example.data.local.db.FocusForestDatabase
import com.example.data.local.db.entities.ChallengeProgressEntity
import com.example.domain.engine.ChallengeEngine
import com.example.domain.model.ChallengeIconType
import com.example.domain.model.ChallengeItem
import com.example.domain.model.ChallengeMetricType
import com.example.domain.model.ChallengePeriod
import com.example.domain.model.ChallengeStatus
import com.example.domain.model.UsageBaselineStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.util.UUID

class ChallengeRepository(
    private val db: FocusForestDatabase,
    private val clockProvider: ClockProvider
) {
    private val challengeDao = db.challengeDao()
    private val focusSessionDao = db.focusSessionDao()

    val challenges: Flow<List<ChallengeItem>> = combine(
        challengeDao.observeChallenges(),
        challengeDao.observeChallengeProgress()
    ) { entities, progressList ->
        val progressById = progressList.associateBy { it.challengeId }
        entities.map { it.toDomain(progressById[it.id]) }
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
    ) {
        val nowMs = clockProvider.nowWallClockMs()
        val id = "custom_${UUID.randomUUID()}"
        val safeTarget = targetValue.coerceIn(1, 600)
        val rewardXp = when (metricType) {
            ChallengeMetricType.FOCUS_MINUTES -> (safeTarget / 2).coerceIn(25, 150)
            else -> (safeTarget * 20).coerceIn(25, 150)
        }
        val rewardSeeds = (rewardXp / 20).coerceIn(1, 6)

        val item = ChallengeItem(
            id = id,
            title = title.trim().ifBlank { "Personal Focus Goal" },
            description = description.trim().ifBlank { "Reach $safeTarget ${metricType.name.lowercase().replace('_', ' ')}." },
            iconType = ChallengeIconType.SPARK,
            period = ChallengePeriod.CUSTOM,
            metricType = metricType,
            targetValue = safeTarget,
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
    }

    suspend fun refreshProgress(usageBaseline: UsageBaselineStatus?) {
        val nowMs = clockProvider.nowWallClockMs()
        db.withTransaction {
            val allEntities = challengeDao.getAllChallengesOnce()
            val progressMap = challengeDao.getAllProgressOnce().associateBy { it.challengeId }
            val domain = allEntities.map { it.toDomain(progressMap[it.id]) }
            val sessions = focusSessionDao.getAllSessionsOnce().map { it.toDomain() }

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

            val (updated, _) = ChallengeEngine.evaluateChallenges(
                challenges = domain,
                sessionsInPeriodByChallengeId = sessionsByChallenge,
                usageBaseline = usageBaseline,
                nowWallClockMs = nowMs
            )

            challengeDao.upsertProgressList(
                updated.map {
                    ChallengeProgressEntity(
                        challengeId = it.id,
                        currentProgress = it.currentProgress,
                        status = it.status.name,
                        completedAtMs = it.completedAtMs,
                        rewardClaimed = it.rewardClaimed
                    )
                }
            )
        }
    }
}
