package com.azanahmed.focusforest.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.azanahmed.focusforest.domain.model.ForestState
import com.azanahmed.focusforest.domain.model.ForestTree
import com.azanahmed.focusforest.domain.model.TreeSpecies
import kotlin.math.sin

@Composable
fun ForestIllustrationCanvas(
    forestState: ForestState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "forest_ambience")
    val swayPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sway_phase"
    )

    val envId = forestState.equippedEnvironmentId
    val decos = forestState.equippedDecorationIds.toSet()

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Sky / Atmosphere based on equipped environment
        val skyColors = when (envId) {
            "env_misty_alpine" -> listOf(Color(0xFFD8E4E2), Color(0xFFF0F4F1))
            "env_golden_sunset" -> listOf(Color(0xFFF5CFA0), Color(0xFFFBE9CE))
            else -> listOf(Color(0xFFCCE3D1), Color(0xFFF3F6EC))
        }
        drawRect(brush = Brush.verticalGradient(skyColors))

        // Sun / Moon orb
        val orbColor = when (envId) {
            "env_golden_sunset" -> Color(0xFFE8A24A).copy(alpha = 0.72f)
            "env_misty_alpine" -> Color(0xFFFFFFFF).copy(alpha = 0.78f)
            else -> Color(0xFFF8E5A7).copy(alpha = 0.8f)
        }
        drawCircle(
            color = orbColor,
            radius = minOf(w, h) * 0.14f,
            center = Offset(w * 0.80f, h * 0.24f)
        )

        // 2. Distant Ridge / Mist
        val ridgeColor = when (envId) {
            "env_misty_alpine" -> Color(0xFF7D9A95)
            "env_golden_sunset" -> Color(0xFF849B74)
            else -> Color(0xFF81B29A)
        }
        val ridgePath = Path().apply {
            moveTo(0f, h * 0.62f)
            cubicTo(w * 0.25f, h * 0.48f, w * 0.55f, h * 0.64f, w, h * 0.52f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(ridgePath, color = ridgeColor.copy(alpha = 0.5f))

        // 3. Midground Rolling Hill
        val midHillColor = when (envId) {
            "env_misty_alpine" -> Color(0xFF4F776B)
            "env_golden_sunset" -> Color(0xFF588157)
            else -> Color(0xFF52B788)
        }
        val midPath = Path().apply {
            moveTo(0f, h * 0.70f)
            cubicTo(w * 0.30f, h * 0.62f, w * 0.72f, h * 0.76f, w, h * 0.66f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(midPath, color = midHillColor.copy(alpha = 0.85f))

        // 4. Foreground Meadow Floor
        val floorColor = when (envId) {
            "env_misty_alpine" -> Color(0xFF2D5348)
            "env_golden_sunset" -> Color(0xFF3A5A40)
            else -> Color(0xFF2D6A4F)
        }
        val floorPath = Path().apply {
            moveTo(0f, h * 0.79f)
            cubicTo(w * 0.35f, h * 0.75f, w * 0.65f, h * 0.84f, w, h * 0.77f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(floorPath, color = floorColor)

        // Optional Decoration: Woodland Brook
        if ("deco_stream_brook" in decos) {
            val streamPath = Path().apply {
                moveTo(w * 0.58f, h * 0.77f)
                cubicTo(w * 0.44f, h * 0.84f, w * 0.68f, h * 0.91f, w * 0.48f, h)
                lineTo(w * 0.62f, h)
                cubicTo(w * 0.76f, h * 0.91f, w * 0.54f, h * 0.84f, w * 0.64f, h * 0.77f)
                close()
            }
            drawPath(streamPath, color = Color(0xFF7CC0D8).copy(alpha = 0.82f))
        }

        // 5. Render Trees (or starter sprout when empty)
        val treesToDraw = forestState.trees.take(14)
        if (treesToDraw.isEmpty()) {
            drawStarterSprout(centerX = w * 0.5f, groundY = h * 0.81f, scale = minOf(w, h) * 0.0032f, sway = sin(swayPhase) * 2f)
        } else {
            treesToDraw.forEachIndexed { idx, tree ->
                val slotFraction = ((idx + 1).toFloat() / (treesToDraw.size + 1).toFloat())
                val x = w * (0.10f + slotFraction * 0.80f)
                val depthRow = if (idx % 2 == 0) 0.78f else 0.85f
                val groundY = h * depthRow
                val sizeScale = if (idx % 2 == 0) 0.85f else 1.05f
                val swayOffset = sin(swayPhase + idx * 0.9f) * 2.5f
                drawTreeForSpecies(
                    tree = tree,
                    baseX = x,
                    groundY = groundY,
                    scale = (minOf(w, h) / 360f) * sizeScale,
                    swayOffset = swayOffset
                )
            }
        }

        // Optional Decoration: Mossy Stone Lantern
        if ("deco_stone_lantern" in decos) {
            val lx = w * 0.16f
            val ly = h * 0.84f
            drawRect(Color(0xFF6B705C), Offset(lx - 8f, ly - 20f), Size(16f, 20f))
            drawRoundRect(Color(0xFFE9C46A), Offset(lx - 7f, ly - 32f), Size(14f, 12f), CornerRadius(3f, 3f))
            val roof = Path().apply {
                moveTo(lx - 14f, ly - 32f)
                lineTo(lx, ly - 44f)
                lineTo(lx + 14f, ly - 32f)
                close()
            }
            drawPath(roof, color = Color(0xFF4A5759))
        }

        // Optional Decoration: Wildflowers
        if ("deco_wildflowers" in decos) {
            val flowerPositions = listOf(0.24f to 0.88f, 0.34f to 0.92f, 0.72f to 0.89f, 0.84f to 0.85f)
            flowerPositions.forEachIndexed { idx, (fx, fy) ->
                val petalColor = if (idx % 2 == 0) Color(0xFFF4A261) else Color(0xFFFFF8E7)
                drawCircle(petalColor, radius = 5f, center = Offset(w * fx, h * fy))
                drawCircle(Color(0xFFE9C46A), radius = 2.2f, center = Offset(w * fx, h * fy))
            }
        }

        // Optional Decoration: Fireflies
        if ("deco_fireflies" in decos) {
            val fireflySpots = listOf(0.22f to 0.56f, 0.42f to 0.48f, 0.65f to 0.60f, 0.82f to 0.52f, 0.52f to 0.68f)
            fireflySpots.forEachIndexed { idx, (fx, fy) ->
                val pulse = 0.45f + 0.55f * ((sin(swayPhase * 1.5f + idx) + 1f) / 2f)
                val center = Offset(w * fx + sin(swayPhase + idx) * 6f, h * fy)
                drawCircle(Color(0xFFF9E47A).copy(alpha = 0.25f * pulse), radius = 11f, center = center)
                drawCircle(Color(0xFFFFF3B0).copy(alpha = 0.9f * pulse), radius = 3.2f, center = center)
            }
        }
    }
}

private fun DrawScope.drawStarterSprout(centerX: Float, groundY: Float, scale: Float, sway: Float) {
    val s = scale.coerceAtLeast(0.7f)
    drawLine(
        color = Color(0xFF1B4332),
        start = Offset(centerX, groundY),
        end = Offset(centerX + sway, groundY - 38f * s),
        strokeWidth = 5f * s,
        cap = StrokeCap.Round
    )
    drawOval(
        color = Color(0xFF74C69D),
        topLeft = Offset(centerX + sway - 22f * s, groundY - 52f * s),
        size = Size(22f * s, 14f * s)
    )
    drawOval(
        color = Color(0xFF95D5B2),
        topLeft = Offset(centerX + sway, groundY - 56f * s),
        size = Size(24f * s, 14f * s)
    )
}

private fun DrawScope.drawTreeForSpecies(
    tree: ForestTree,
    baseX: Float,
    groundY: Float,
    scale: Float,
    swayOffset: Float
) {
    val s = scale.coerceAtLeast(0.65f)
    val trunkWidth = 10f * s
    val trunkHeight = 36f * s
    val trunkColor = if (tree.species == TreeSpecies.BIRCH) Color(0xFFE9E4D8) else Color(0xFF5C4033)

    // Trunk
    drawRoundRect(
        color = trunkColor,
        topLeft = Offset(baseX - trunkWidth / 2f, groundY - trunkHeight),
        size = Size(trunkWidth, trunkHeight),
        cornerRadius = CornerRadius(4f, 4f)
    )

    val topX = baseX + swayOffset
    when (tree.species) {
        TreeSpecies.PINE -> {
            val tiers = listOf(
                Triple(34f * s, groundY - trunkHeight * 0.75f, Color(0xFF1B4332)),
                Triple(28f * s, groundY - trunkHeight * 1.35f, Color(0xFF2D6A4F)),
                Triple(20f * s, groundY - trunkHeight * 1.90f, Color(0xFF40916C))
            )
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
            drawCircle(Color(0xFF2D6A4F), radius = 28f * s, center = Offset(topX, cy))
            drawCircle(Color(0xFF40916C), radius = 21f * s, center = Offset(topX - 15f * s, cy + 6f * s))
            drawCircle(Color(0xFF52B788), radius = 19f * s, center = Offset(topX + 16f * s, cy + 5f * s))
        }
        TreeSpecies.BIRCH -> {
            val cy = groundY - trunkHeight - 22f * s
            drawOval(
                color = Color(0xFF74C69D),
                topLeft = Offset(topX - 20f * s, cy - 28f * s),
                size = Size(40f * s, 54f * s)
            )
            drawOval(
                color = Color(0xFF95D5B2),
                topLeft = Offset(topX - 12f * s, cy - 22f * s),
                size = Size(24f * s, 38f * s)
            )
        }
        TreeSpecies.WILLOW -> {
            val cy = groundY - trunkHeight - 14f * s
            drawCircle(Color(0xFF6B9080), radius = 26f * s, center = Offset(topX, cy))
            for (i in -2..2) {
                drawLine(
                    color = Color(0xFFA4C3B2),
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
                    color = Color(0xFF52B788),
                    topLeft = Offset(baseX + angleIdx * 8f * s - 6f * s, groundY - 26f * s),
                    size = Size(12f * s, 26f * s)
                )
            }
        }
    }
}
