package com.retrovibe.os.theme

import androidx.compose.ui.graphics.Color
import com.retrovibe.os.model.ThemeId

data class RetroThemeColors(
    val id: ThemeId,
    val name: String,
    val wallpaper: Color,
    val windowBg: Color,
    val windowSurface: Color,
    val titleBarActive: Color,
    val titleBarAccent: Color,
    val titleBarInactive: Color,
    val titleTextActive: Color,
    val titleTextInactive: Color,
    val borderLight: Color,
    val borderDark: Color,
    val borderDeep: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentPink: Color,
    val accentSoft: Color,
    val taskbarBg: Color
)

object RetroThemes {
    val ALL: Map<ThemeId, RetroThemeColors> = mapOf(
        ThemeId.DEAR_PASTEL to RetroThemeColors(
            id = ThemeId.DEAR_PASTEL,
            name = "RetroVibe Pastel Denim",
            wallpaper = Color(0xFF789BC2),
            windowBg = Color(0xFFECE6DA),
            windowSurface = Color(0xFFF8F5EF),
            titleBarActive = Color(0xFF2E4C89),
            titleBarAccent = Color(0xFFD99AA8),
            titleBarInactive = Color(0xFF8F99AD),
            titleTextActive = Color(0xFFFFFFFF),
            titleTextInactive = Color(0xFFE8E4DC),
            borderLight = Color(0xFFFFFFFF),
            borderDark = Color(0xFF8C867A),
            borderDeep = Color(0xFF24232B),
            textPrimary = Color(0xFF24232B),
            textSecondary = Color(0xFF5F5B54),
            accentPink = Color(0xFFE69AB0),
            accentSoft = Color(0xFFF6D6DF),
            taskbarBg = Color(0xFFDED8CB)
        ),
        ThemeId.WIN95_CLASSIC to RetroThemeColors(
            id = ThemeId.WIN95_CLASSIC,
            name = "Classic Win95 Teal",
            wallpaper = Color(0xFF008080),
            windowBg = Color(0xFFC0C0C0),
            windowSurface = Color(0xFFFFFFFF),
            titleBarActive = Color(0xFF000080),
            titleBarAccent = Color(0xFF1084D0),
            titleBarInactive = Color(0xFF808080),
            titleTextActive = Color(0xFFFFFFFF),
            titleTextInactive = Color(0xFFD4D0C8),
            borderLight = Color(0xFFFFFFFF),
            borderDark = Color(0xFF808080),
            borderDeep = Color(0xFF000000),
            textPrimary = Color(0xFF000000),
            textSecondary = Color(0xFF444444),
            accentPink = Color(0xFF000080),
            accentSoft = Color(0xFFC8D6F0),
            taskbarBg = Color(0xFFC0C0C0)
        ),
        ThemeId.Y2K_PINK to RetroThemeColors(
            id = ThemeId.Y2K_PINK,
            name = "Y2K Strawberry Milk",
            wallpaper = Color(0xFFE6B7C8),
            windowBg = Color(0xFFF6ECF0),
            windowSurface = Color(0xFFFFFAFB),
            titleBarActive = Color(0xFFC25782),
            titleBarAccent = Color(0xFFE289AD),
            titleBarInactive = Color(0xFFBFA2AE),
            titleTextActive = Color(0xFFFFFFFF),
            titleTextInactive = Color(0xFFF7EDF1),
            borderLight = Color(0xFFFFFFFF),
            borderDark = Color(0xFFA58492),
            borderDeep = Color(0xFF3A222D),
            textPrimary = Color(0xFF3A222D),
            textSecondary = Color(0xFF735160),
            accentPink = Color(0xFFE47BA4),
            accentSoft = Color(0xFFFBDBE7),
            taskbarBg = Color(0xFFEDDDE4)
        ),
        ThemeId.VAPORWAVE_NIGHT to RetroThemeColors(
            id = ThemeId.VAPORWAVE_NIGHT,
            name = "Vaporwave Midnight",
            wallpaper = Color(0xFF282348),
            windowBg = Color(0xFF39335C),
            windowSurface = Color(0xFF272342),
            titleBarActive = Color(0xFFB34B8A),
            titleBarAccent = Color(0xFF58449C),
            titleBarInactive = Color(0xFF4C466D),
            titleTextActive = Color(0xFFFFFFFF),
            titleTextInactive = Color(0xFFBBB4D6),
            borderLight = Color(0xFF6B6396),
            borderDark = Color(0xFF1C1830),
            borderDeep = Color(0xFF0E0C1A),
            textPrimary = Color(0xFFF2ECFF),
            textSecondary = Color(0xFFB7AECF),
            accentPink = Color(0xFFE77DB6),
            accentSoft = Color(0xFF4E396B),
            taskbarBg = Color(0xFF312C52)
        )
    )

    fun of(id: ThemeId): RetroThemeColors = ALL[id] ?: ALL[ThemeId.DEAR_PASTEL]!!
}

typealias RetroPalette = RetroThemeColors

val RetroThemeColors.textDark: Color get() = textPrimary
val RetroThemeColors.accent: Color get() = accentPink
val RetroThemeColors.desktopBg: Color get() = wallpaper
val RetroThemeColors.titleBarStart: Color get() = titleBarActive

fun paletteFor(id: ThemeId): RetroPalette = RetroThemes.of(id)
