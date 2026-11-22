package com.azanahmed.focusforest.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.azanahmed.focusforest.domain.model.ForestState
import com.azanahmed.focusforest.domain.model.ForestTree
import com.azanahmed.focusforest.domain.model.GrowthStage
import com.azanahmed.focusforest.domain.model.TreeSpecies
import com.azanahmed.focusforest.ui.theme.ForestMotion

private const val MAX_VISIBLE_TREES = 14

/**
 * Draws the user's forest as a deterministic botanical scene.
 *
 * The composition is intentionally stable: tree position, depth, lean, and size come from the
 * persisted tree id, never from random numbers or the current frame. Only the stage transition
 * animates, and only when system animations are enabled.
 */
@Composable
fun ForestIllustrationCanvas(
    forestState: ForestState,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    motionEnabled: Boolean = true
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val growthTarget = stageScale(forestState.currentStage)
    val animatedGrowth by animateFloatAsState(
        targetValue = growthTarget,
        animationSpec = tween(durationMillis = ForestMotion.slowMs),
        label = "forest_growth"
    )
    val growth = if (motionEnabled && animationsEnabled) animatedGrowth else growthTarget
    val environmentId = forestState.equippedEnvironmentId
    val decorations = remember(forestState.equippedDecorationIds) {
        forestState.equippedDecorationIds.toSet()
    }
    val visibleTrees = remember(forestState.trees) {
        forestState.trees.take(MAX_VISIBLE_TREES)
    }
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.fillMaxSize().then(semanticsModifier)) {
        drawEnvironmentBase(environmentId = environmentId, isDark = isDark)

        val w = size.width
        val h = size.height
        val unit = minOf(w, h) / 360f

        if ("deco_stream_brook" in decorations) {
            drawBrook(w, h, paletteFor(environmentId, isDark))
        }

        if (visibleTrees.isEmpty()) {
            drawStarterSprout(
                centerX = w * 0.5f,
                groundY = h * 0.81f,
                scale = unit.coerceAtLeast(0.75f) * growth,
                isDark = isDark
            )
        } else {
            visibleTrees.forEachIndexed { index, tree ->
                val stable = tree.id.hashCode() and 0x7fffffff
                val slot = (index + 1f) / (visibleTrees.size + 1f)
                val x = w * (0.08f + slot * 0.84f)
                val depthRow = if (stable % 3 == 0) 0.80f else 0.86f
                val groundY = h * depthRow
                val sizeVariation = 0.86f + (stable % 26) / 100f
                val maturity = 0.90f + (tree.focusMinutes.coerceIn(5, 60) / 60f) * 0.18f
                val lean = ((stable % 9) - 4) * 0.7f * unit
                drawTreeForSpecies(
                    tree = tree,
                    baseX = x,
                    groundY = groundY,
                    scale = unit * sizeVariation * growth * maturity,
                    lean = lean,
                    growth = growth,
                    isDark = isDark
                )
            }
        }

        if ("deco_stone_lantern" in decorations) {
            drawStoneLantern(w, h, unit)
        }
        if ("deco_wildflowers" in decorations) {
            drawWildflowers(w, h, unit)
        }
        if ("deco_fireflies" in decorations) {
            drawFireflies(w, h, unit)
        }

        drawGrassTufts(w, h, unit, isDark)
    }
}

@Composable
fun ForestEnvironmentThumbnail(
    environmentId: String,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Canvas(modifier = modifier.fillMaxSize()) {
        drawEnvironmentBase(environmentId = environmentId, isDark = isDark, withDetails = false)
        val w = size.width
        val h = size.height
        val unit = minOf(w, h) / 180f
        // Two quiet silhouettes make the thumbnail read as a forest place, not a color swatch.
        drawSimplePine(w * 0.30f, h * 0.80f, unit * 0.9f, paletteFor(environmentId, isDark))
        drawSimplePine(w * 0.68f, h * 0.84f, unit * 0.65f, paletteFor(environmentId, isDark))
    }
}

@Composable
fun TreeSpeciesThumbnail(
    species: TreeSpecies,
    stage: GrowthStage,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val growth = stageScale(stage)
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val unit = minOf(w, h) / 90f
        drawTreeForSpecies(
            tree = ForestTree(
                id = "preview_${species.name}",
                species = species,
                plantedDate = "",
                focusMinutes = 25
            ),
            baseX = w * 0.5f,
            groundY = h * 0.88f,
            scale = unit * growth,
            lean = 0f,
            growth = growth,
            isDark = isDark
        )
    }
}

private data class ForestPalette(
    val skyTop: Color,
    val skyBottom: Color,
    val orb: Color,
    val ridge: Color,
    val midHill: Color,
    val floor: Color,
    val stream: Color
)

private fun paletteFor(environmentId: String, isDark: Boolean): ForestPalette {
    return if (isDark) {
        when (environmentId) {
            "env_misty_alpine" -> ForestPalette(
                skyTop = Color(0xFF172426),
                skyBottom = Color(0xFF243638),
                orb = Color(0xFFDCE9E7).copy(alpha = 0.72f),
                ridge = Color(0xFF2B4442),
                midHill = Color(0xFF315550),
                floor = Color(0xFF1B332E),
                stream = Color(0xFF4A7280)
            )
            "env_golden_sunset" -> ForestPalette(
                skyTop = Color(0xFF2B1D13),
                skyBottom = Color(0xFF3D2A1B),
                orb = Color(0xFFD79A55).copy(alpha = 0.72f),
                ridge = Color(0xFF3E3A26),
                midHill = Color(0xFF3F4A2E),
                floor = Color(0xFF26331F),
                stream = Color(0xFF5A6F5A)
            )
            else -> ForestPalette(
                skyTop = Color(0xFF0F231A),
                skyBottom = Color(0xFF1B3327),
                orb = Color(0xFFE7D9A0).copy(alpha = 0.72f),
                ridge = Color(0xFF1F4034),
                midHill = Color(0xFF24513F),
                floor = Color(0xFF163326),
                stream = Color(0xFF3E6B7A)
            )
        }
    } else {
        when (environmentId) {
            "env_misty_alpine" -> ForestPalette(
                skyTop = Color(0xFFD8E4E2),
                skyBottom = Color(0xFFF0F4F1),
                orb = Color.White.copy(alpha = 0.78f),
                ridge = Color(0xFF7D9A95),
                midHill = Color(0xFF4F776B),
                floor = Color(0xFF2D5348),
                stream = Color(0xFF8FB8C4)
            )
            "env_golden_sunset" -> ForestPalette(
                skyTop = Color(0xFFF5CFA0),
                skyBottom = Color(0xFFFBE9CE),
                orb = Color(0xFFE8A24A).copy(alpha = 0.72f),
                ridge = Color(0xFF849B74),
                midHill = Color(0xFF588157),
                floor = Color(0xFF3A5A40),
                stream = Color(0xFF8CB8A8)
            )
            else -> ForestPalette(
                skyTop = Color(0xFFCCE3D1),
                skyBottom = Color(0xFFF3F6EC),
                orb = Color(0xFFF8E5A7).copy(alpha = 0.8f),
                ridge = Color(0xFF81B29A),
                midHill = Color(0xFF52B788),
                floor = Color(0xFF2D6A4F),
                stream = Color(0xFF7CC0D8)
            )
        }
    }
}

private fun DrawScope.drawEnvironmentBase(
    environmentId: String,
    isDark: Boolean,
    withDetails: Boolean = true
) {
    val w = size.width
    val h = size.height
    val palette = paletteFor(environmentId, isDark)

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.skyTop, palette.skyBottom),
            startY = 0f,
            endY = h
        )
    )

    drawCircle(
        color = palette.orb,
        radius = minOf(w, h) * 0.14f,
        center = Offset(w * 0.80f, h * 0.24f)
    )

    val ridgePath = Path().apply {
        moveTo(0f, h * 0.62f)
        cubicTo(w * 0.25f, h * 0.48f, w * 0.55f, h * 0.64f, w, h * 0.52f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(ridgePath, color = palette.ridge.copy(alpha = 0.55f))

    val midPath = Path().apply {
        moveTo(0f, h * 0.70f)
        cubicTo(w * 0.30f, h * 0.62f, w * 0.72f, h * 0.76f, w, h * 0.66f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(midPath, color = palette.midHill.copy(alpha = 0.88f))

    val floorPath = Path().apply {
        moveTo(0f, h * 0.79f)
        cubicTo(w * 0.35f, h * 0.75f, w * 0.65f, h * 0.84f, w, h * 0.77f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(floorPath, color = palette.floor)

    if (withDetails) {
        // A soft path of light on the meadow floor adds depth without another card or gradient.
        drawOval(
            color = palette.skyBottom.copy(alpha = if (isDark) 0.08f else 0.22f),
            topLeft = Offset(w * 0.30f, h * 0.83f),
            size = Size(w * 0.40f, h * 0.07f)
        )
    }
}

private fun DrawScope.drawBrook(w: Float, h: Float, palette: ForestPalette) {
    val streamPath = Path().apply {
        moveTo(w * 0.58f, h * 0.77f)
        cubicTo(w * 0.44f, h * 0.84f, w * 0.68f, h * 0.91f, w * 0.48f, h)
        lineTo(w * 0.62f, h)
        cubicTo(w * 0.76f, h * 0.91f, w * 0.54f, h * 0.84f, w * 0.64f, h * 0.77f)
        close()
    }
    drawPath(streamPath, color = palette.stream.copy(alpha = 0.82f))
    drawLine(
        color = Color.White.copy(alpha = 0.24f),
        start = Offset(w * 0.56f, h * 0.83f),
        end = Offset(w * 0.52f, h * 0.96f),
        strokeWidth = 2.5f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawStarterSprout(
    centerX: Float,
    groundY: Float,
    scale: Float,
    isDark: Boolean
) {
    val s = scale.coerceAtLeast(0.7f)
    val stem = if (isDark) Color(0xFF8FD3AC) else Color(0xFF1B4332)
    val leaf = if (isDark) Color(0xFF74C69D) else Color(0xFF74C69D)
    val leafLight = if (isDark) Color(0xFFA7E3C0) else Color(0xFF95D5B2)
    drawOval(
        color = Color.Black.copy(alpha = 0.10f),
        topLeft = Offset(centerX - 18f * s, groundY - 3f * s),
        size = Size(36f * s, 9f * s)
    )
    drawLine(
        color = stem,
        start = Offset(centerX, groundY),
        end = Offset(centerX, groundY - 38f * s),
        strokeWidth = 5f * s,
        cap = StrokeCap.Round
    )
    drawOval(
        color = leaf,
        topLeft = Offset(centerX - 22f * s, groundY - 52f * s),
        size = Size(22f * s, 14f * s)
    )
    drawOval(
        color = leafLight,
        topLeft = Offset(centerX, groundY - 56f * s),
        size = Size(24f * s, 14f * s)
    )
}

private fun DrawScope.drawTreeForSpecies(
    tree: ForestTree,
    baseX: Float,
    groundY: Float,
    scale: Float,
    lean: Float,
    growth: Float,
    isDark: Boolean
) {
    val s = scale.coerceAtLeast(0.55f)
    val trunkColor = when {
        tree.species == TreeSpecies.BIRCH -> if (isDark) Color(0xFFD8D2C4) else Color(0xFFE9E4D8)
        isDark -> Color(0xFF3B2A22)
        else -> Color(0xFF5C4033)
    }
    val trunkWidth = 9f * s
    val trunkHeight = 34f * s

    drawOval(
        color = Color.Black.copy(alpha = if (isDark) 0.18f else 0.10f),
        topLeft = Offset(baseX - 22f * s, groundY - 4f * s),
        size = Size(44f * s, 10f * s)
    )

    if (growth < 0.78f) {
        drawYoungSapling(baseX, groundY, s, lean, isDark)
        return
    }

    drawRoundRect(
        color = trunkColor,
        topLeft = Offset(baseX - trunkWidth / 2f, groundY - trunkHeight),
        size = Size(trunkWidth, trunkHeight),
        cornerRadius = CornerRadius(4f, 4f)
    )

    val topX = baseX + lean
    val foliage = foliageColors(tree.species, isDark)

    when (tree.species) {
        TreeSpecies.PINE -> {
            val tiers = mutableListOf(
                Triple(34f * s, groundY - trunkHeight * 0.75f, foliage[0]),
                Triple(28f * s, groundY - trunkHeight * 1.35f, foliage[1]),
                Triple(20f * s, groundY - trunkHeight * 1.90f, foliage[2])
            )
            if (growth > 1.08f) {
                tiers += Triple(14f * s, groundY - trunkHeight * 2.35f, foliage[2])
            }
            tiers.forEach { (halfW, bottomY, color) ->
                val path = Path().apply {
                    moveTo(topX, bottomY - 34f * s)
                    lineTo(topX - halfW, bottomY)
                    lineTo(topX + halfW, bottomY)
                    close()
                }
                drawPath(path, color = color)
            }
        }
        TreeSpecies.OAK -> {
            val cy = groundY - trunkHeight - 16f * s
            drawCircle(foliage[0], radius = 28f * s, center = Offset(topX, cy))
            drawCircle(foliage[1], radius = 21f * s, center = Offset(topX - 15f * s, cy + 6f * s))
            drawCircle(foliage[2], radius = 19f * s, center = Offset(topX + 16f * s, cy + 5f * s))
            if (growth > 1.05f) {
                drawCircle(foliage[1], radius = 15f * s, center = Offset(topX - 4f * s, cy - 18f * s))
                drawCircle(foliage[2], radius = 13f * s, center = Offset(topX + 12f * s, cy - 12f * s))
            }
        }
        TreeSpecies.BIRCH -> {
            val cy = groundY - trunkHeight - 22f * s
            drawOval(
                color = foliage[0],
                topLeft = Offset(topX - 20f * s, cy - 28f * s),
                size = Size(40f * s, 54f * s)
            )
            drawOval(
                color = foliage[1],
                topLeft = Offset(topX - 12f * s, cy - 22f * s),
                size = Size(24f * s, 38f * s)
            )
            if (growth > 1.05f) {
                drawOval(
                    color = foliage[2],
                    topLeft = Offset(topX + 4f * s, cy - 32f * s),
                    size = Size(18f * s, 28f * s)
                )
            }
        }
        TreeSpecies.WILLOW -> {
            val cy = groundY - trunkHeight - 14f * s
            drawCircle(foliage[0], radius = 26f * s, center = Offset(topX, cy))
            for (i in -2..2) {
                drawLine(
                    color = foliage[1],
                    start = Offset(topX + i * 8f * s, cy),
                    end = Offset(topX + i * 9f * s, groundY - 12f * s),
                    strokeWidth = 3f * s,
                    cap = StrokeCap.Round
                )
            }
        }
        TreeSpecies.FERN -> {
            for (angleIdx in -2..2) {
                drawOval(
                    color = foliage[if (angleIdx % 2 == 0) 0 else 1],
                    topLeft = Offset(
                        baseX + angleIdx * 8f * s - 6f * s,
                        groundY - 26f * s
                    ),
                    size = Size(12f * s, 26f * s)
                )
            }
        }
    }
}

private fun DrawScope.drawYoungSapling(
    baseX: Float,
    groundY: Float,
    s: Float,
    lean: Float,
    isDark: Boolean
) {
    val stem = if (isDark) Color(0xFF8FD3AC) else Color(0xFF2D6A4F)
    val leaf = if (isDark) Color(0xFF74C69D) else Color(0xFF52B788)
    val leafLight = if (isDark) Color(0xFFA7E3C0) else Color(0xFF95D5B2)
    drawLine(
        color = stem,
        start = Offset(baseX, groundY),
        end = Offset(baseX + lean, groundY - 24f * s),
        strokeWidth = 3.5f * s,
        cap = StrokeCap.Round
    )
    drawOval(
        color = leaf,
        topLeft = Offset(baseX + lean - 14f * s, groundY - 34f * s),
        size = Size(14f * s, 9f * s)
    )
    drawOval(
        color = leafLight,
        topLeft = Offset(baseX + lean, groundY - 38f * s),
        size = Size(15f * s, 9f * s)
    )
}

private fun foliageColors(species: TreeSpecies, isDark: Boolean): List<Color> {
    return if (isDark) {
        when (species) {
            TreeSpecies.PINE -> listOf(Color(0xFF2D6A4F), Color(0xFF40916C), Color(0xFF52B788))
            TreeSpecies.OAK -> listOf(Color(0xFF2D6A4F), Color(0xFF40916C), Color(0xFF52B788))
            TreeSpecies.BIRCH -> listOf(Color(0xFF74C69D), Color(0xFF95D5B2), Color(0xFFA7E3C0))
            TreeSpecies.WILLOW -> listOf(Color(0xFF5F8D7A), Color(0xFF8FB8A4))
            TreeSpecies.FERN -> listOf(Color(0xFF52B788), Color(0xFF74C69D))
        }
    } else {
        when (species) {
            TreeSpecies.PINE -> listOf(Color(0xFF1B4332), Color(0xFF2D6A4F), Color(0xFF40916C))
            TreeSpecies.OAK -> listOf(Color(0xFF2D6A4F), Color(0xFF40916C), Color(0xFF52B788))
            TreeSpecies.BIRCH -> listOf(Color(0xFF74C69D), Color(0xFF95D5B2), Color(0xFFA7E3C0))
            TreeSpecies.WILLOW -> listOf(Color(0xFF6B9080), Color(0xFFA4C3B2))
            TreeSpecies.FERN -> listOf(Color(0xFF52B788), Color(0xFF74C69D))
        }
    }
}

private fun DrawScope.drawStoneLantern(w: Float, h: Float, unit: Float) {
    val lx = w * 0.16f
    val ly = h * 0.84f
    val s = unit.coerceAtLeast(0.75f)
    drawRect(Color(0xFF6B705C), Offset(lx - 8f * s, ly - 20f * s), Size(16f * s, 20f * s))
    drawRoundRect(
        Color(0xFFE9C46A),
        Offset(lx - 7f * s, ly - 32f * s),
        Size(14f * s, 12f * s),
        CornerRadius(3f * s, 3f * s)
    )
    val roof = Path().apply {
        moveTo(lx - 14f * s, ly - 32f * s)
        lineTo(lx, ly - 44f * s)
        lineTo(lx + 14f * s, ly - 32f * s)
        close()
    }
    drawPath(roof, color = Color(0xFF4A5759))
    drawCircle(Color(0xFFF9E47A).copy(alpha = 0.16f), radius = 12f * s, center = Offset(lx, ly - 26f * s))
}

private fun DrawScope.drawWildflowers(w: Float, h: Float, unit: Float) {
    val s = unit.coerceAtLeast(0.7f)
    val flowerPositions = listOf(0.24f to 0.88f, 0.34f to 0.92f, 0.72f to 0.89f, 0.84f to 0.85f)
    flowerPositions.forEachIndexed { idx, (fx, fy) ->
        val petalColor = if (idx % 2 == 0) Color(0xFFF4A261) else Color(0xFFFFF8E7)
        drawCircle(petalColor, radius = 5f * s, center = Offset(w * fx, h * fy))
        drawCircle(Color(0xFFE9C46A), radius = 2.2f * s, center = Offset(w * fx, h * fy))
    }
}

private fun DrawScope.drawFireflies(w: Float, h: Float, unit: Float) {
    val s = unit.coerceAtLeast(0.7f)
    val fireflySpots = listOf(0.22f to 0.56f, 0.42f to 0.48f, 0.65f to 0.60f, 0.82f to 0.52f, 0.52f to 0.68f)
    fireflySpots.forEachIndexed { idx, (fx, fy) ->
        // Stable glow: no frame loop, no random recomposition.
        val pulse = 0.55f + 0.30f * (((idx * 37) % 10) / 10f)
        val center = Offset(w * fx, h * fy)
        drawCircle(Color(0xFFF9E47A).copy(alpha = 0.20f * pulse), radius = 11f * s, center = center)
        drawCircle(Color(0xFFFFF3B0).copy(alpha = 0.85f * pulse), radius = 3.2f * s, center = center)
    }
}

private fun DrawScope.drawGrassTufts(w: Float, h: Float, unit: Float, isDark: Boolean) {
    val s = unit.coerceAtLeast(0.7f)
    val grassColor = if (isDark) Color(0xFF3D7A5D).copy(alpha = 0.55f) else Color(0xFF1B4332).copy(alpha = 0.35f)
    for (i in 0 until 12) {
        val x = w * (0.05f + i * 0.08f)
        val baseY = h * (0.90f + (i % 3) * 0.018f)
        val blade = 5f * s + (i % 4) * 2f * s
        drawLine(
            color = grassColor,
            start = Offset(x, baseY),
            end = Offset(x + (if (i % 2 == 0) -3f else 3f) * s, baseY - blade),
            strokeWidth = 1.8f * s,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawSimplePine(baseX: Float, groundY: Float, scale: Float, palette: ForestPalette) {
    val s = scale.coerceAtLeast(0.5f)
    drawRoundRect(
        color = Color(0xFF3B2A22).copy(alpha = 0.75f),
        topLeft = Offset(baseX - 2f * s, groundY - 16f * s),
        size = Size(4f * s, 16f * s),
        cornerRadius = CornerRadius(2f * s, 2f * s)
    )
    val path = Path().apply {
        moveTo(baseX, groundY - 46f * s)
        lineTo(baseX - 16f * s, groundY - 12f * s)
        lineTo(baseX + 16f * s, groundY - 12f * s)
        close()
    }
    drawPath(path, color = palette.midHill.copy(alpha = 0.92f))
}

private fun stageScale(stage: GrowthStage): Float = when (stage) {
    GrowthStage.SEED -> 0.55f
    GrowthStage.SPROUT -> 0.70f
    GrowthStage.SAPLING -> 0.84f
    GrowthStage.YOUNG_TREE -> 0.96f
    GrowthStage.MATURE_TREE -> 1.10f
    GrowthStage.ANCIENT_GROVE -> 1.22f
}
