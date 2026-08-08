package com.juying.app.ui

import androidx.compose.ui.graphics.Color

/**
 * 主题色（参考 AuvFun AbstractC2506pe 的 8 套 accent 色值）。
 * accent 用于浅色模式主色；accentDark 用于深色模式主色（保证对比度）。
 */
data class ThemePalette(
    val id: String,
    val name: String,
    val accent: Color,
    val accentDark: Color
)

val THEME_PALETTES = listOf(
    ThemePalette("blue", "海洋蓝", Color(0xFF3B82F6), Color(0xFF2563EB)),
    ThemePalette("red", "朱砂红", Color(0xFFFF3B5C), Color(0xFFE91E47)),
    ThemePalette("orange", "日落橙", Color(0xFFF97316), Color(0xFFEA580C)),
    ThemePalette("purple", "暮光紫", Color(0xFF8B5CF6), Color(0xFF7C3AED)),
    ThemePalette("green", "薄荷绿", Color(0xFF10B981), Color(0xFF059669)),
    ThemePalette("teal", "青湖色", Color(0xFF14B8A6), Color(0xFF0D9488)),
    ThemePalette("indigo", "靖蓝", Color(0xFF6366F1), Color(0xFF4F46E5)),
    ThemePalette("bubblegum", "樱花粉", Color(0xFFF472B6), Color(0xFFDB2777))
)
