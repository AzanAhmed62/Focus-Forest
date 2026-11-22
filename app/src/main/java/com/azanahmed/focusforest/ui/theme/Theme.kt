package com.azanahmed.focusforest.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.azanahmed.focusforest.domain.model.ForestThemeSkin
import com.azanahmed.focusforest.domain.model.ThemePreference

val FocusForestShapes = Shapes(
    small = RoundedCornerShape(ForestRadii.chip),
    medium = RoundedCornerShape(ForestRadii.control),
    large = RoundedCornerShape(ForestRadii.hero)
)

/**
 * Semantic colors that are not part of Material 3's [ColorScheme].
 * They are stable across the four purchased forest skins and switch for dark theme.
 */
@Immutable
data class ForestSemanticColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val leaf: Color,
    val leafContainer: Color,
    val onLeafContainer: Color
)

private val LightForestSemanticColors = ForestSemanticColors(
    success = SuccessMoss,
    successContainer = SuccessMossContainer,
    onSuccessContainer = OnSuccessMossContainer,
    warning = WarningBark,
    warningContainer = WarningBarkContainer,
    onWarningContainer = OnWarningBarkContainer,
    leaf = LeafGold,
    leafContainer = LeafGoldContainer,
    onLeafContainer = OnLeafGoldContainer
)

private val DarkForestSemanticColors = ForestSemanticColors(
    success = SuccessMossDark,
    successContainer = SuccessMossContainerDark,
    onSuccessContainer = OnSuccessMossContainerDark,
    warning = WarningBarkDark,
    warningContainer = WarningBarkContainerDark,
    onWarningContainer = OnWarningBarkContainerDark,
    leaf = LeafGoldDark,
    leafContainer = LeafGoldContainerDark,
    onLeafContainer = OnLeafGoldContainerDark
)

val LocalForestSemanticColors = staticCompositionLocalOf { LightForestSemanticColors }

private fun lightSchemeFor(skin: ForestThemeSkin): ColorScheme {
    val primary = when (skin) {
        ForestThemeSkin.CLASSIC_SAGE -> ForestDeep
        ForestThemeSkin.MORNING_MIST -> MorningMistPrimary
        ForestThemeSkin.AUTUMN_CANOPY -> AutumnPrimary
        ForestThemeSkin.MIDNIGHT_PINE -> MidnightPinePrimary
    }
    val secondary = when (skin) {
        ForestThemeSkin.CLASSIC_SAGE -> ForestEmerald
        ForestThemeSkin.MORNING_MIST -> MorningMistSecondary
        ForestThemeSkin.AUTUMN_CANOPY -> AutumnSecondary
        ForestThemeSkin.MIDNIGHT_PINE -> MidnightPineSecondary
    }
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = SoftMint,
        onPrimaryContainer = ForestDeep,
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = WarmSandSurface,
        onSecondaryContainer = ForestInk,
        tertiary = AmberAcorn,
        onTertiary = Color.White,
        tertiaryContainer = SoftAmberContainer,
        onTertiaryContainer = Color(0xFF4A2E07),
        background = WarmCreamBg,
        onBackground = ForestInk,
        surface = WarmCreamSurface,
        onSurface = ForestInk,
        surfaceVariant = WarmSandSurface,
        onSurfaceVariant = MutedMossText,
        outline = WoodlandOutline,
        error = TerracottaSoft,
        onError = Color.White,
        errorContainer = SoftTerracottaContainer,
        onErrorContainer = Color(0xFF5A1E11)
    )
}

private fun darkSchemeFor(skin: ForestThemeSkin): ColorScheme {
    val primary = when (skin) {
        ForestThemeSkin.CLASSIC_SAGE -> SageLeaf
        ForestThemeSkin.MORNING_MIST -> Color(0xFF83C5BE)
        ForestThemeSkin.AUTUMN_CANOPY -> Color(0xFFE09F67)
        ForestThemeSkin.MIDNIGHT_PINE -> Color(0xFF62B6CB)
    }
    val secondary = when (skin) {
        ForestThemeSkin.CLASSIC_SAGE -> MistSage
        ForestThemeSkin.MORNING_MIST -> Color(0xFFA8DADC)
        ForestThemeSkin.AUTUMN_CANOPY -> Color(0xFFA3B18A)
        ForestThemeSkin.MIDNIGHT_PINE -> Color(0xFFBEE9E8)
    }
    return darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF0A2419),
        primaryContainer = Color(0xFF1F4231),
        onPrimaryContainer = SoftMint,
        secondary = secondary,
        onSecondary = Color(0xFF0D261B),
        secondaryContainer = DeepCanopyElevated,
        onSecondaryContainer = CreamInk,
        tertiary = Color(0xFFE8B968),
        onTertiary = Color(0xFF332005),
        tertiaryContainer = DarkAmberContainer,
        onTertiaryContainer = Color(0xFFFCE4B8),
        background = DeepCanopyBg,
        onBackground = CreamInk,
        surface = DeepCanopySurface,
        onSurface = CreamInk,
        surfaceVariant = DeepCanopyElevated,
        onSurfaceVariant = SoftSageText,
        outline = DarkWoodlandOutline,
        error = Color(0xFFE58B73),
        onError = Color(0xFF441309),
        errorContainer = Color(0xFF5A251B),
        onErrorContainer = Color(0xFFFCDAD2)
    )
}

@Composable
fun FocusForestTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    equippedThemeId: String = ForestThemeSkin.CLASSIC_SAGE.id,
    content: @Composable () -> Unit
) {
    val useDark = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val skin = ForestThemeSkin.entries.find { it.id == equippedThemeId }
        ?: ForestThemeSkin.CLASSIC_SAGE

    val colorScheme = if (useDark) darkSchemeFor(skin) else lightSchemeFor(skin)
    val semanticColors = if (useDark) DarkForestSemanticColors else LightForestSemanticColors

    CompositionLocalProvider(LocalForestSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FocusForestTypography,
            shapes = FocusForestShapes,
            content = content
        )
    }
}
