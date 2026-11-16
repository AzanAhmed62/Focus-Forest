package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.DailyStats
import com.example.domain.model.DailyUsageSummary
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun FocusTrendBarChart(
    days: List<DailyStats>,
    dailyGoalMinutes: Int,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val maxVal = maxOf(
        dailyGoalMinutes,
        days.maxOfOrNull { it.focusMinutes } ?: 0,
        30
    ).toFloat()

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            val count = days.size.coerceAtLeast(1)
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.46f).coerceAtMost(36.dp.toPx())

            // Goal dashed threshold line
            val goalY = size.height * (1f - (dailyGoalMinutes / maxVal).coerceIn(0f, 1f))
            drawLine(
                color = goalColor.copy(alpha = 0.7f),
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )

            days.forEachIndexed { idx, stat ->
                val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                // Background track
                drawRoundRect(
                    color = trackColor.copy(alpha = 0.65f),
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(10f, 10f)
                )
                val fraction = (stat.focusMinutes / maxVal).coerceIn(0f, 1f)
                if (fraction > 0f) {
                    val barHeight = (size.height * fraction).coerceAtLeast(6.dp.toPx())
                    drawRoundRect(
                        color = if (stat.goalMet) barColor else barColor.copy(alpha = 0.72f),
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEach { stat ->
                val label = runCatching {
                    LocalDate.parse(stat.localDate).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                }.getOrDefault("•")
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${stat.focusMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun UsageSevenDayBarChart(
    days: List<DailyUsageSummary>,
    useMonitoredSubset: Boolean,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.secondary
    val unavailableColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val values = days.map {
        if (!it.hasUsableData) null
        else if (useMonitoredSubset) it.monitoredAppsMinutes else it.totalObservedMinutes
    }
    val maxVal = maxOf(values.filterNotNull().maxOrNull() ?: 60, 60).toFloat()

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            val count = days.size.coerceAtLeast(1)
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.46f).coerceAtMost(36.dp.toPx())

            values.forEachIndexed { idx, minutes ->
                val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                if (minutes == null) {
                    // Unavailable / incomplete data marker instead of pretending zero
                    drawRoundRect(
                        color = unavailableColor,
                        topLeft = Offset(x, size.height - 6.dp.toPx()),
                        size = Size(barWidth, 6.dp.toPx()),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                } else {
                    val fraction = (minutes / maxVal).coerceIn(0f, 1f)
                    val barHeight = (size.height * fraction).coerceAtLeast(8.dp.toPx())
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEachIndexed { idx, day ->
                val minutes = values[idx]
                val label = runCatching {
                    LocalDate.parse(day.localDate).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                }.getOrDefault("•")
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = minutes?.let { "${it}m" } ?: "N/A",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
