package com.azanahmed.focusforest.data.repository

import com.azanahmed.focusforest.data.local.db.entities.ChallengeEntity
import com.azanahmed.focusforest.data.local.db.entities.ChallengeProgressEntity
import com.azanahmed.focusforest.data.local.db.entities.DailyStatsEntity
import com.azanahmed.focusforest.data.local.db.entities.FocusSessionEntity
import com.azanahmed.focusforest.data.local.db.entities.ForestProgressEntity
import com.azanahmed.focusforest.data.local.db.entities.ForestTreeEntity
import com.azanahmed.focusforest.data.local.db.entities.ShopItemEntity
import com.azanahmed.focusforest.data.local.db.entities.UserProfileEntity
import com.azanahmed.focusforest.domain.engine.ForestGrowthEngine
import com.azanahmed.focusforest.domain.model.ChallengeIconType
import com.azanahmed.focusforest.domain.model.ChallengeItem
import com.azanahmed.focusforest.domain.model.ChallengeMetricType
import com.azanahmed.focusforest.domain.model.ChallengePeriod
import com.azanahmed.focusforest.domain.model.ChallengeStatus
import com.azanahmed.focusforest.domain.model.DailyStats
import com.azanahmed.focusforest.domain.model.FocusSession
import com.azanahmed.focusforest.domain.model.ForestState
import com.azanahmed.focusforest.domain.model.ForestTree
import com.azanahmed.focusforest.domain.model.SessionStatus
import com.azanahmed.focusforest.domain.model.SessionType
import com.azanahmed.focusforest.domain.model.ShopCatalogItem
import com.azanahmed.focusforest.domain.model.ShopCategory
import com.azanahmed.focusforest.domain.model.TreeSpecies
import com.azanahmed.focusforest.domain.model.UserIntention
import com.azanahmed.focusforest.domain.model.UserProfile

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
    targetPackageName = targetPackageName,
    isArchived = isArchived
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
    targetPackageName = targetPackageName,
    isArchived = isArchived
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
