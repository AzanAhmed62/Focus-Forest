package com.azanahmed.focusforest.domain.engine

import com.azanahmed.focusforest.domain.model.GrowthStage

object ForestGrowthEngine {

    fun stageForSeeds(totalSeeds: Int): GrowthStage {
        return GrowthStage.entries
            .lastOrNull { totalSeeds >= it.minSeeds }
            ?: GrowthStage.SEED
    }

    fun forestLevelForSeeds(totalSeeds: Int): Int {
        return 1 + (totalSeeds.coerceAtLeast(0) / 12)
    }

    fun seedsToNextStage(totalSeeds: Int): Int? {
        val next = GrowthStage.entries.firstOrNull { it.minSeeds > totalSeeds }
        return next?.let { it.minSeeds - totalSeeds }
    }
}
