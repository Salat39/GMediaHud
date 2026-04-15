package com.salat.gmediahud.ui.theme

import androidx.compose.ui.graphics.Color
import com.salat.gmediahud.ui.theme.ColorPalette

internal val LightAppColors = AppColors(
    isDark = false,

    surfaceLayerAccentPale = ColorPalette.BrandBlue400.copy(alpha = 0.3f),
    surfaceBackground = Color(0xFFf7f8fa),
    surfaceLayer1 = Color(0xFF314e70),
    surfaceMenu = Color(0xFF4a76a8),
    surfaceMenuDivider = Color(0xFF4a76a8),
    surfaceSettings = Color(0xFFeceff3),
    surfaceSettingsLayer1 = Color(0xFFeceff3),
    cardItemBackground = Color(0xFF212121),
    contentAccent = Color(0xFF0099FA),
    contentLightAccent = Color(0xFF0099FA),
    deleteButton = Color(0xFFF44336),
    contentPrimary = Color(0xFF314e70),
    contentWarning = ColorPalette.Yellow700,
    addSplitTop = Color(0xFF1565C0),
    addSplitBottom = Color(0xFF00695C),
    menuIcon = ColorPalette.BaseWhite,
    sliderPassive = Color(0xFF546E7A),
    autoStart = Color(0xFF546E7A),
)

internal val DarkAppColors = AppColors(
    isDark = true,

    surfaceLayerAccentPale = ColorPalette.BrandBlue800.copy(alpha = 0.3f),
    surfaceBackground = Color(0xFF1f1f1f),
    surfaceLayer1 = Color(0xFF121212),
    surfaceMenu = Color(0xFF2D2D2D), // 0xFF2A2A2A
    surfaceMenuDivider = Color(0xFF232323),
    surfaceSettings = Color(0xFF1A1A1A), // 0xFF1C1C1C
    surfaceSettingsLayer1 = Color(0xFF262626), // 0xFF262626
    cardItemBackground = Color(0xFF1e1e1e),
    contentAccent = Color(0xFF1975d0),
    contentLightAccent = Color(0xFF1975d0),
    deleteButton = Color(0xFFF44336),
    contentPrimary = Color(0xFFe5e5e5),
    contentWarning = ColorPalette.Yellow500,
    addSplitTop = Color(0xFF2E7D32), // 0xFF388E3C
    addSplitBottom = Color(0xFF283593), // 0xFF512DA8
    menuIcon = ColorPalette.BaseWhite,
    sliderPassive = Color(0xFF626262),
    autoStart = Color(0xFF01579B),
)

/* internal val DarkAppColors = AppColors(
    isDark = true,

    surfaceLayerAccentPale = ColorPalette.BrandBlue800.copy(alpha = 0.3f),
    surfaceBackground = Color(0xFF263238),
    surfaceLayer1 = Color(0xFF37474F),
    surfaceLayer2 = Color(0xFF455A64),
    surfaceLayer3 = Color(0xFF546E7A),
    surfaceLayer4 = Color(0xFF2C3A41),
    surfaceLayer5 = Color(0xFF314048),
    contentAccent = Color(0xFF1975d0),
    contentPrimary = ColorPalette.BaseWhite,
    contentWarning = ColorPalette.Yellow500,
    addSplitTop = Color(0xFF388E3C),
    addSplitBottom = Color(0xFF512DA8),
    menuIcon = ColorPalette.BaseWhite,
) */
