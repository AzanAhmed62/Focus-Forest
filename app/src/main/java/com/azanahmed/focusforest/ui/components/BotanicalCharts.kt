package com.azanahmed.focusforest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.azanahmed.focusforest.domain.engine.CalendarDayCell
import com.azanahmed.focusforest.domain.engine.CalendarDayKind
import com.azanahmed.focusforest.domain.model.DailyStats
import com.azanahmed.focusforest.domain.model.DailyUsageSummary
import com.azanahmed.focusforest.domain.model.SnapshotCompleteness
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
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

    val isDenseWindow = days.size > 10

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            val count = days.size.coerceAtLeast(1)
            val slotWidth = size.width / count
            val barWidth = if (isDenseWindow) {
                (slotWidth * 0.68f).coerceAtLeast(2.dp.toPx()).coerceAtMost(14.dp.toPx())
            } else {
                (slotWidth * 0.46f).coerceAtMost(36.dp.toPx())
            }

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
                drawRoundRect(
                    color = trackColor.copy(alpha = 0.65f),
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                val fraction = (stat.focusMinutes / maxVal).coerceIn(0f, 1f)
                if (fraction > 0f) {
                    val barHeight = (size.height * fraction).coerceAtLeast(4.dp.toPx())
                    drawRoundRect(
                        color = if (stat.goalMet) barColor else barColor.copy(alpha = 0.72f),
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
        }

        if (isDenseWindow && days.isNotEmpty()) {
            val firstLabel = runCatching { LocalDate.parse(days.first().localDate) }.getOrNull()
                ?.format(DateTimeFormatter.ofPattern("MMM d")) ?: ""
            val midLabel = runCatching { LocalDate.parse(days[days.size / 2].localDate) }.getOrNull()
                ?.format(DateTimeFormatter.ofPattern("MMM d")) ?: ""
            val lastLabel = runCatching { LocalDate.parse(days.last().localDate) }.getOrNull()
                ?.format(DateTimeFormatter.ofPattern("MMM d")) ?: ""
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(firstLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(midLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(lastLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
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
}

@Composable
fun UsageSevenDayBarChart(
    days: List<DailyUsageSummary>,
    useMonitoredSubset: Boolean,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.secondary
    val zeroColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
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
                val summary = days[idx]
                when {
                    minutes == null -> {
                        drawRoundRect(
                            color = unavailableColor,
                            topLeft = Offset(x, size.height - 6.dp.toPx()),
                            size = Size(barWidth, 6.dp.toPx()),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                    summary.completeness == SnapshotCompleteness.ZERO_USAGE || minutes == 0 -> {
                        drawRoundRect(
                            color = zeroColor,
                            topLeft = Offset(x, size.height - 6.dp.toPx()),
                            size = Size(barWidth, 6.dp.toPx()),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                    else -> {
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
                        text = when {
                            minutes == null -> "N/A"
                            else -> "${minutes}m"
                        },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreakCalendarCard(
    displayedMonth: YearMonth,
    cells: List<CalendarDayCell>,
    selectedDate: LocalDate?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    val firstDayOfWeek = displayedMonth.atDay(1).dayOfWeek
    val leadingBlanks = (firstDayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val goalMetBg = MaterialTheme.colorScheme.primary
    val activeBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
    val restBg = MaterialTheme.colorScheme.tertiaryContainer
    val missedBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val futureBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)

    BotanicalCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier.testTag("calendar_prev_month")
            ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                text = displayedMonth.format(monthFormatter),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("calendar_month_title")
            )
            IconButton(
                onClick = onNextMonth,
                modifier = Modifier.testTag("calendar_next_month")
            ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { dayLabel ->
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        val paddedSlots = List<CalendarDayCell?>(leadingBlanks) { null } + cells
        val rows = paddedSlots.chunked(7)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { weekRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (col in 0 until 7) {
                        val cell = weekRow.getOrNull(col)
                        if (cell == null) {
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val isSelected = selectedDate == cell.date
                            val bg = when (cell.kind) {
                                CalendarDayKind.GOAL_MET -> goalMetBg
                                CalendarDayKind.FOCUS_ACTIVE -> activeBg
                                CalendarDayKind.REST_DAY -> restBg
                                CalendarDayKind.MISSED -> missedBg
                                CalendarDayKind.FUTURE -> futureBg
                            }
                            val fg = when (cell.kind) {
                                CalendarDayKind.GOAL_MET -> MaterialTheme.colorScheme.onPrimary
                                CalendarDayKind.REST_DAY -> MaterialTheme.colorScheme.onTertiaryContainer
                                CalendarDayKind.FUTURE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = bg,
                                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clickable { onSelectDate(cell.date) }
                                    .testTag("calendar_day_${cell.date}")
                                    .semantics {
                                        contentDescription = "${cell.date}, ${cell.focusMinutes} focus minutes, ${cell.kind.name.lowercase().replace('_', ' ')}"
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = cell.date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = fg,
                                        fontWeight = if (isSelected || cell.kind == CalendarDayKind.GOAL_MET) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CalendarLegendDot(color = goalMetBg, label = "Goal met")
            CalendarLegendDot(color = activeBg, label = "Focus active")
            CalendarLegendDot(color = restBg, label = "Rest day")
            CalendarLegendDot(color = missedBg, label = "No sessions")
        }
    }
}

@Composable
private fun CalendarLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
