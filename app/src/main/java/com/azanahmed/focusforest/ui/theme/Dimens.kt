package com.azanahmed.focusforest.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Shared layout rhythm for Focus Forest.
 *
 * Screens should compose from these values instead of inventing new paddings and radii.
 * The scale is intentionally small: 4 dp for optical adjustments, 8/12/16/20 for structure,
 * and 28/36 for hero moments.
 */
object ForestSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 28.dp
    val xxl = 36.dp

    val screenHorizontal = 18.dp
    val screenVertical = 16.dp
    val cardPadding = 18.dp
    val sectionGap = 16.dp
}

object ForestRadii {
    val chip = 12.dp
    val control = 16.dp
    val card = 22.dp
    val hero = 28.dp
    val pill = 999.dp
}

object ForestSizes {
    val minTouchTarget = 48.dp
    val iconSmall = 18.dp
    val iconMedium = 24.dp
    val iconLarge = 32.dp
    val avatar = 44.dp
    val forestHeroHeight = 260.dp
    val forestPreviewHeight = 160.dp
    val progressTrack = 8.dp
    val goalRing = 76.dp
}

object ForestMotion {
    const val fastMs = 140
    const val mediumMs = 240
    const val slowMs = 420
}
