package com.example.data.repository

import com.example.data.local.db.entities.ChallengeEntity
import com.example.data.local.db.entities.ChallengeProgressEntity
import com.example.data.local.db.entities.DailyStatsEntity
import com.example.data.local.db.entities.FocusSessionEntity
import com.example.data.local.db.entities.ForestProgressEntity
import com.example.data.local.db.entities.ForestTreeEntity
import com.example.data.local.db.entities.ShopItemEntity
import com.example.data.local.db.entities.UserProfileEntity
import com.example.domain.engine.ForestGrowthEngine
import com.example.domain.model.ChallengeIconType
import com.example.domain.model.ChallengeItem
import com.example.domain.model.ChallengeMetricType
import com.example.domain.model.ChallengePeriod
import com.example.domain.model.ChallengeStatus
import com.example.domain.model.DailyStats
import com.example.domain.model.FocusSession
import com.example.domain.model.ForestState
import com.example.domain.model.ForestTree
import com.example.domain.model.SessionStatus
import com.example.domain.model.SessionType
import com.example.domain.model.ShopCatalogItem
import com.example.domain.model.ShopCategory
import com.example.domain.model.TreeSpecies
import com.example.domain.model.UserIntention
import com.example.domain.model.UserProfile

internal fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
    id = id,
    displayName = displayName,
    primaryIntention = runCatching { UserIntention.valueOf(primaryIntention) }.getOrDefault(UserIntention.CONCENTRATION),
    dailyFocusGoalMinutes = dailyFocusGoalMinutes,
    lifetimeXp = lifetimeXp,
    currentLevel = currentLevel,
    currentStreakDays = currentStreakDays,
    longestStreakDays = longestStreakDays,
    equippedAvatarId = equippedAvatarId,
    equippedThemeId = equippedThemeId,
    createdAtMs = createdAtMs
)

internal fun FocusSessionEntity.toDomain(): FocusSession = FocusSession(
    id = id,
    sessionType = runCatching { SessionType.valueOf(sessionType) }.getOrDefault(SessionType.POMODORO),
    plannedMinutes = plannedMinutes,
    completedMinutes = completedMinutes,
    status = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.ABORTED),
    startedAtWallMs = startedAtWallMs,
    endedAtWallMs = endedAtWallMs,
    localDate = localDate,
    xpAwarded = xpAwarded,
    seedsAwarded = seedsAwarded,
    intentionLabel = intentionLabel,
    rewardGranted = rewardGranted
)

internal fun FocusSession.toEntity(): FocusSessionEntity = FocusSessionEntity(
    id = id,
    sessionType = sessionType.name,
    plannedMinutes = plannedMinutes,
    completedMinutes = completedMinutes,
    status = status.name,
    startedAtWallMs = startedAtWallMs,
    endedAtWallMs = endedAtWallMs,
    localDate = localDate,
    xpAwarded = xpAwarded,
    seedsAwarded = seedsAwarded,
    intentionLabel = intentionLabel,
    rewardGranted = rewardGranted
)

internal fun DailyStatsEntity.toDomain(): DailyStats = DailyStats(
    localDate = localDate,
    focusMinutes = focusMinutes,
    completedSessions = completedSessions,
    goalMinutes = goalMinutes,
    goalMet = goalMet,
    xpEarned = xpEarned,
    seedsEarned = seedsEarned,
    isRestDay = isRestDay
)

internal fun ChallengeEntity.toDomain(progress: ChallengeProgressEntity?): ChallengeItem = ChallengeItem(
    id = id,
    title = title,
    description = description,
    iconType = runCatching { ChallengeIconType.valueOf(iconType) }.getOrDefault(ChallengeIconType.SPARK),
    period = runCatching { ChallengePeriod.valueOf(period) }.getOrDefault(ChallengePeriod.DAILY),
    metricType = runCatching { ChallengeMetricType.valueOf(metricType) }.getOrDefault(ChallengeMetricType.FOCUS_MINUTES),
    targetValue = targetValue,
    currentProgress = progress?.currentProgress ?: 0,
    rewardXp = rewardXp,
    rewardSeeds = rewardSeeds,
    status = runCatching { ChallengeStatus.valueOf(progress?.status ?: ChallengeStatus.ACTIVE.name) }
        .getOrDefault(ChallengeStatus.ACTIVE),
    periodKey = periodKey,
    createdAtMs = createdAtMs,
    completedAtMs = progress?.completedAtMs,
    rewardClaimed = progress?.rewardClaimed ?: false,
    targetPackageName = targetPackageName
)

internal fun ChallengeItem.toEntity(): ChallengeEntity = ChallengeEntity(
    id = id,
    title = title,
    description = description,
    iconType = iconType.name,
    period = period.name,
    metricType = metricType.name,
    targetValue = targetValue,
    rewardXp = rewardXp,
    rewardSeeds = rewardSeeds,
    periodKey = periodKey,
    createdAtMs = createdAtMs,
    targetPackageName = targetPackageName
)

internal fun ForestTreeEntity.toDomain(): ForestTree = ForestTree(
    id = id,
    species = runCatching { TreeSpecies.valueOf(species) }.getOrDefault(TreeSpecies.PINE),
    plantedDate = plantedDate,
    focusMinutes = focusMinutes
)

internal fun ForestProgressEntity.toDomain(trees: List<ForestTree>): ForestState = ForestState(
    forestLevel = forestLevel,
    totalSeeds = totalSeeds,
    spendableLeafPoints = spendableLeafPoints,
    currentStage = ForestGrowthEngine.stageForSeeds(totalSeeds),
    totalTreesPlanted = totalTreesPlanted,
    trees = trees,
    equippedEnvironmentId = equippedEnvironmentId,
    equippedDecorationIds = equippedDecorationsCsv.split(",").filter { it.isNotBlank() }
)

internal fun ShopItemEntity.toDomain(owned: Boolean, equipped: Boolean): ShopCatalogItem = ShopCatalogItem(
    id = id,
    name = name,
    description = description,
    category = runCatching { ShopCategory.valueOf(category) }.getOrDefault(ShopCategory.DECORATION),
    costLeafPoints = costLeafPoints,
    previewColorHex = previewColorHex,
    owned = owned,
    equipped = equipped
)
