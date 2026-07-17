package com.shyz.alwayshyper

/**
 * Available accent themes. Colours are stored as plain ARGB ints so this file
 * has no Compose dependency and can be used from OverlayService (to pick the
 * real on-screen island colour) as well as from the Compose UI (see
 * ui/theme/Color.kt for the Compose Color wrappers).
 */
enum class ThemeColor(
    val key: String,
    val label: String,
    val primaryArgb: Int,
    val secondaryArgb: Int
) {
    BLUE("blue", "Синий", 0xFF5B7CFF.toInt(), 0xFF9B6BFF.toInt()),
    RED("red", "Красный", 0xFFFF3B30.toInt(), 0xFFB71C1C.toInt()),
    YELLOW("yellow", "Жёлтый", 0xFFFFD60A.toInt(), 0xFFFF9500.toInt()),
    GREEN("green", "Зелёный", 0xFF34C759.toInt(), 0xFF0A8F3C.toInt()),
    WHITE("white", "Белый", 0xFFF5F6FA.toInt(), 0xFFD0D3DE.toInt()),
    MAROON("maroon", "Бордовый", 0xFF8B1E3F.toInt(), 0xFF5E1230.toInt());

    /** Whether this theme's primary colour is light enough to need dark text/icons on top of it. */
    val isLight: Boolean get() = this == WHITE || this == YELLOW

    companion object {
        fun fromKey(key: String?): ThemeColor = entries.firstOrNull { it.key == key } ?: BLUE
    }
}
