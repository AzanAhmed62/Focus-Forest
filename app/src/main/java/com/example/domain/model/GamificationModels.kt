package com.example.domain.model

enum class AchievementType(
    val title: String,
    val description: String,
    val bonusXp: Int
) {
    FIRST_FOCUS("First Focus", "Complete your very first focus session.", 40),
    THREE_DAY_CONSISTENCY("Three-Day Rhythm", "Build a 3-day focus consistency streak.", 60),
    SEVEN_DAY_CONSISTENCY("Seven-Day Canopy", "Maintain a 7-day consistency streak.", 120),
    THIRTY_DAY_CONSISTENCY("Thirty-Day Woodland", "Sustain a 30-day consistency streak.", 300),
    TEN_SESSIONS("Ten Mindful Sessions", "Complete 10 focus sessions.", 80),
    FIFTY_SESSIONS("Fifty Deep Blocks", "Complete 50 focus sessions.", 220),
    TEN_FOCUS_HOURS("Ten Hours Reclaimed", "Accumulate 600 total focus minutes.", 180),
    FIRST_FIVE_CHALLENGES("Pathfinder", "Complete 5 daily or weekly challenges.", 100),
    LEVEL_FIVE("Keeper of the Grove", "Reach Focus Forest Level 5.", 150)
}

data class AchievementItem(
    val type: AchievementType,
    val unlocked: Boolean,
    val unlockedAtMs: Long?,
    val progressLabel: String
)

enum class TreeSpecies(val displayName: String, val scientificHint: String) {
    PINE("Scots Pine", "Pinus sylvestris"),
    OAK("Ancient Oak", "Quercus robur"),
    BIRCH("Silver Birch", "Betula pendula"),
    WILLOW("River Willow", "Salix alba"),
    FERN("Woodland Fern", "Dryopteris")
}

enum class GrowthStage(val label: String, val minSeeds: Int) {
    SEED("Seed", 0),
    SPROUT("Sprout", 3),
    SAPLING("Sapling", 10),
    YOUNG_TREE("Young Tree", 25),
    MATURE_TREE("Mature Tree", 55),
    ANCIENT_GROVE("Ancient Grove", 100)
}

data class ForestTree(
    val id: String,
    val species: TreeSpecies,
    val plantedDate: String,
    val focusMinutes: Int
)

data class ForestState(
    val forestLevel: Int = 1,
    val totalSeeds: Int = 0,
    val spendableLeafPoints: Int = 0,
    val currentStage: GrowthStage = GrowthStage.SEED,
    val totalTreesPlanted: Int = 0,
    val trees: List<ForestTree> = emptyList(),
    val equippedEnvironmentId: String = "env_emerald_clearing",
    val equippedDecorationIds: List<String> = emptyList()
)

enum class ShopCategory(val label: String) {
    THEME("Color Themes"),
    AVATAR("Avatar Styles"),
    DECORATION("Forest Decorations"),
    ENVIRONMENT("Botanical Environments")
}

data class ShopCatalogItem(
    val id: String,
    val name: String,
    val description: String,
    val category: ShopCategory,
    val costLeafPoints: Int,
    val previewColorHex: Long,
    val owned: Boolean = false,
    val equipped: Boolean = false
)
