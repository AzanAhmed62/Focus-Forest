package com.azanahmed.focusforest.data.repository

import androidx.room.withTransaction
import com.azanahmed.focusforest.core.time.ClockProvider
import com.azanahmed.focusforest.data.local.db.FocusForestDatabase
import com.azanahmed.focusforest.data.local.db.entities.ForestProgressEntity
import com.azanahmed.focusforest.data.local.db.entities.OwnedItemEntity
import com.azanahmed.focusforest.data.local.db.entities.ShopItemEntity
import com.azanahmed.focusforest.domain.model.AvatarStyle
import com.azanahmed.focusforest.domain.model.ForestState
import com.azanahmed.focusforest.domain.model.ForestThemeSkin
import com.azanahmed.focusforest.domain.model.ShopCatalogItem
import com.azanahmed.focusforest.domain.model.ShopCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

sealed class ShopActionResult {
    data object Success : ShopActionResult()
    data object InsufficientPoints : ShopActionResult()
    data object ItemNotFound : ShopActionResult()
}

class ShopAndForestRepository(
    private val db: FocusForestDatabase,
    private val clockProvider: ClockProvider
) {
    private val forestDao = db.forestDao()
    private val shopDao = db.shopDao()
    private val profileDao = db.userProfileDao()

    val forestState: Flow<ForestState> = combine(
        forestDao.observeForestProgress(),
        forestDao.observeTrees()
    ) { progress, trees ->
        val domainTrees = trees.map { it.toDomain() }
        progress?.toDomain(domainTrees) ?: ForestState(trees = domainTrees)
    }

    val shopItems: Flow<List<ShopCatalogItem>> = combine(
        shopDao.observeCatalog(),
        shopDao.observeOwnedItems()
    ) { catalog, owned ->
        val ownedMap = owned.associateBy { it.itemId }
        catalog.map { entity ->
            val ownedEntity = ownedMap[entity.id]
            entity.toDomain(
                owned = ownedEntity != null,
                equipped = ownedEntity?.isEquipped ?: false
            )
        }
    }

    suspend fun ensureCatalogSeeded() {
        val nowMs = clockProvider.nowWallClockMs()
        val catalog = defaultCatalog()
        db.withTransaction {
            shopDao.upsertCatalogItems(catalog)
            // Default free starter items
            val starters = listOf(
                ForestThemeSkin.CLASSIC_SAGE.id,
                AvatarStyle.SEEDLING.id,
                "env_emerald_clearing"
            )
            starters.forEach { id ->
                shopDao.insertOwnedIfAbsent(
                    OwnedItemEntity(
                        itemId = id,
                        acquiredAtMs = nowMs,
                        isEquipped = true
                    )
                )
            }
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
    }

    suspend fun purchaseOrEquip(itemId: String): ShopActionResult {
        val nowMs = clockProvider.nowWallClockMs()
        return db.withTransaction {
            val catalog = shopDao.getCatalogOnce()
            val item = catalog.find { it.id == itemId } ?: return@withTransaction ShopActionResult.ItemNotFound
            val ownedList = shopDao.getOwnedItemsOnce()
            val alreadyOwned = ownedList.any { it.itemId == itemId }

            if (!alreadyOwned) {
                val forest = forestDao.getForestProgressOnce() ?: ForestProgressEntity(
                    id = 1,
                    totalSeeds = 0,
                    spendableLeafPoints = 0,
                    forestLevel = 1,
                    totalTreesPlanted = 0,
                    equippedEnvironmentId = "env_emerald_clearing",
                    equippedDecorationsCsv = ""
                )
                if (forest.spendableLeafPoints < item.costLeafPoints) {
                    return@withTransaction ShopActionResult.InsufficientPoints
                }
                forestDao.upsertForestProgress(
                    forest.copy(spendableLeafPoints = forest.spendableLeafPoints - item.costLeafPoints)
                )
                shopDao.upsertOwnedItem(
                    OwnedItemEntity(
                        itemId = item.id,
                        acquiredAtMs = nowMs,
                        isEquipped = false
                    )
                )
            }

            equipInternal(item, catalog, shopDao.getOwnedItemsOnce())
            ShopActionResult.Success
        }
    }

    private suspend fun equipInternal(
        item: ShopItemEntity,
        catalog: List<ShopItemEntity>,
        ownedList: List<OwnedItemEntity>
    ) {
        val category = runCatching { ShopCategory.valueOf(item.category) }.getOrDefault(ShopCategory.DECORATION)
        if (category == ShopCategory.DECORATION) {
            // Decorations can be toggled on/off independently
            val current = ownedList.find { it.itemId == item.id }
            val nextEquipped = !(current?.isEquipped ?: false)
            shopDao.upsertOwnedItem(
                OwnedItemEntity(
                    itemId = item.id,
                    acquiredAtMs = current?.acquiredAtMs ?: clockProvider.nowWallClockMs(),
                    isEquipped = nextEquipped
                )
            )
            val allEquippedDecos = shopDao.getOwnedItemsOnce()
                .filter { owned ->
                    owned.isEquipped && catalog.find { it.id == owned.itemId }?.category == ShopCategory.DECORATION.name
                }
                .map { it.itemId }
            val forest = forestDao.getForestProgressOnce()
            if (forest != null) {
                forestDao.upsertForestProgress(
                    forest.copy(equippedDecorationsCsv = allEquippedDecos.joinToString(","))
                )
            }
            return
        }

        // Single-slot categories: unequip siblings in same category
        val sameCategoryIds = catalog.filter { it.category == item.category }.map { it.id }.toSet()
        ownedList.filter { it.itemId in sameCategoryIds }.forEach { sibling ->
            shopDao.upsertOwnedItem(sibling.copy(isEquipped = sibling.itemId == item.id))
        }
        val profile = profileDao.getProfileOnce()
        val forest = forestDao.getForestProgressOnce()
        when (category) {
            ShopCategory.THEME -> if (profile != null) {
                profileDao.upsertProfile(profile.copy(equippedThemeId = item.id))
            }
            ShopCategory.AVATAR -> if (profile != null) {
                profileDao.upsertProfile(profile.copy(equippedAvatarId = item.id))
            }
            ShopCategory.ENVIRONMENT -> if (forest != null) {
                forestDao.upsertForestProgress(forest.copy(equippedEnvironmentId = item.id))
            }
            ShopCategory.DECORATION -> Unit
        }
    }

    private fun defaultCatalog(): List<ShopItemEntity> = listOf(
        // Color Themes
        ShopItemEntity(
            id = ForestThemeSkin.CLASSIC_SAGE.id,
            name = "Classic Forest Sage",
            description = "Deep botanical emerald paired with warm cream paper.",
            category = ShopCategory.THEME.name,
            costLeafPoints = 0,
            previewColorHex = 0xFF1B4332
        ),
        ShopItemEntity(
            id = ForestThemeSkin.MORNING_MIST.id,
            name = "Dawn Mist Meadow",
            description = "Soft eucalyptus greens with sunlit morning highlights.",
            category = ShopCategory.THEME.name,
            costLeafPoints = 4,
            previewColorHex = 0xFF2D6A4F
        ),
        ShopItemEntity(
            id = ForestThemeSkin.AUTUMN_CANOPY.id,
            name = "Autumn Amber Canopy",
            description = "Warm cedar bark, terracotta amber, and mossy olive tones.",
            category = ShopCategory.THEME.name,
            costLeafPoints = 8,
            previewColorHex = 0xFF8C4A27
        ),
        ShopItemEntity(
            id = ForestThemeSkin.MIDNIGHT_PINE.id,
            name = "Midnight Pine Sanctuary",
            description = "Cool evergreen slate with moonlit teal botanical accents.",
            category = ShopCategory.THEME.name,
            costLeafPoints = 12,
            previewColorHex = 0xFF143642
        ),

        // Avatars
        ShopItemEntity(
            id = AvatarStyle.SEEDLING.id,
            name = "Sprouting Seedling",
            description = "Your starter emblem celebrating small daily beginnings.",
            category = ShopCategory.AVATAR.name,
            costLeafPoints = 0,
            previewColorHex = 0xFF52B788
        ),
        ShopItemEntity(
            id = AvatarStyle.PINE_BADGE.id,
            name = "Evergreen Pine",
            description = "Resilient woodland crest for steady focus habits.",
            category = ShopCategory.AVATAR.name,
            costLeafPoints = 3,
            previewColorHex = 0xFF2D6A4F
        ),
        ShopItemEntity(
            id = AvatarStyle.ACORN.id,
            name = "Golden Acorn",
            description = "Symbol of patience and quiet long-term growth.",
            category = ShopCategory.AVATAR.name,
            costLeafPoints = 6,
            previewColorHex = 0xFFC58B2B
        ),
        ShopItemEntity(
            id = AvatarStyle.BOTANIST.id,
            name = "Forest Botanist",
            description = "Pressed fern medallion for thoughtful trackers.",
            category = ShopCategory.AVATAR.name,
            costLeafPoints = 10,
            previewColorHex = 0xFF40916C
        ),
        ShopItemEntity(
            id = AvatarStyle.OWL.id,
            name = "Woodland Owl",
            description = "Calm guardian of evening deep-work sessions.",
            category = ShopCategory.AVATAR.name,
            costLeafPoints = 15,
            previewColorHex = 0xFF5C4D7D
        ),

        // Botanical Environments
        ShopItemEntity(
            id = "env_emerald_clearing",
            name = "Emerald Clearing",
            description = "Sun-dappled forest glade with soft rolling moss.",
            category = ShopCategory.ENVIRONMENT.name,
            costLeafPoints = 0,
            previewColorHex = 0xFF74C69D
        ),
        ShopItemEntity(
            id = "env_misty_alpine",
            name = "Misty Alpine Ridge",
            description = "Crisp mountain silhouettes beneath pale morning mist.",
            category = ShopCategory.ENVIRONMENT.name,
            costLeafPoints = 8,
            previewColorHex = 0xFF52796F
        ),
        ShopItemEntity(
            id = "env_golden_sunset",
            name = "Golden Hour Orchard",
            description = "Warm amber horizon glowing behind quiet woodland hills.",
            category = ShopCategory.ENVIRONMENT.name,
            costLeafPoints = 14,
            previewColorHex = 0xFFD4A373
        ),

        // Forest Decorations
        ShopItemEntity(
            id = "deco_stone_lantern",
            name = "Mossy Stone Lantern",
            description = "Adds a warm glowing lantern beside your grove.",
            category = ShopCategory.DECORATION.name,
            costLeafPoints = 3,
            previewColorHex = 0xFFE9C46A
        ),
        ShopItemEntity(
            id = "deco_stream_brook",
            name = "Woodland Brook",
            description = "A winding crystal stream curving through the clearing.",
            category = ShopCategory.DECORATION.name,
            costLeafPoints = 5,
            previewColorHex = 0xFF6BA3BE
        ),
        ShopItemEntity(
            id = "deco_fireflies",
            name = "Evening Fireflies",
            description = "Soft golden lights drifting gently across your trees.",
            category = ShopCategory.DECORATION.name,
            costLeafPoints = 7,
            previewColorHex = 0xFFF4D35E
        ),
        ShopItemEntity(
            id = "deco_wildflowers",
            name = "Botanical Wildflowers",
            description = "Clusters of chamomile and clover along the forest floor.",
            category = ShopCategory.DECORATION.name,
            costLeafPoints = 4,
            previewColorHex = 0xFFE07A5F
        )
    )
}
