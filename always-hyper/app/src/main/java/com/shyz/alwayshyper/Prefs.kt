package com.shyz.alwayshyper

import android.content.Context
import android.content.SharedPreferences

/**
 * Single source of truth for the overlay's state.
 * MainActivity writes to it; OverlayService listens for changes
 * so the on-screen island updates live while you drag the sliders.
 */
object Prefs {
    private const val FILE = "always_hyper_prefs"

    const val KEY_OVERLAY_ON = "overlay_on"
    const val KEY_WIDTH = "width"
    const val KEY_HEIGHT = "height"
    const val KEY_RADIUS = "radius"
    const val KEY_TOP_OFFSET = "top_offset"
    const val KEY_THEME = "theme"
    const val KEY_AUTO_CONTRAST = "auto_contrast"
    const val KEY_IMAGE_PATH = "island_image_path"

    const val DEFAULT_WIDTH = 90
    const val DEFAULT_HEIGHT = 28
    const val DEFAULT_RADIUS = 14
    const val DEFAULT_TOP_OFFSET = 10
    val DEFAULT_THEME = ThemeColor.BLUE

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isOverlayOn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY_ON, true)

    fun setOverlayOn(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_OVERLAY_ON, value).apply()
    }

    fun getWidth(context: Context): Int =
        prefs(context).getInt(KEY_WIDTH, DEFAULT_WIDTH)

    fun setWidth(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_WIDTH, value).apply()
    }

    fun getHeight(context: Context): Int =
        prefs(context).getInt(KEY_HEIGHT, DEFAULT_HEIGHT)

    fun setHeight(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_HEIGHT, value).apply()
    }

    fun getRadius(context: Context): Int =
        prefs(context).getInt(KEY_RADIUS, DEFAULT_RADIUS)

    fun setRadius(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_RADIUS, value).apply()
    }

    fun getTopOffset(context: Context): Int =
        prefs(context).getInt(KEY_TOP_OFFSET, DEFAULT_TOP_OFFSET)

    fun setTopOffset(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_TOP_OFFSET, value).apply()
    }

    fun getTheme(context: Context): ThemeColor =
        ThemeColor.fromKey(prefs(context).getString(KEY_THEME, DEFAULT_THEME.key))

    fun setTheme(context: Context, value: ThemeColor) {
        prefs(context).edit().putString(KEY_THEME, value.key).apply()
    }

    /**
     * Off by default: the island is always plain black (matches the actual
     * camera cutout colour on every phone). When turned on, the island
     * switches to the selected theme colour whenever the system is in dark
     * theme, so it doesn't disappear against dark app backgrounds.
     */
    fun isAutoContrastOn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CONTRAST, false)

    fun setAutoContrastOn(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CONTRAST, value).apply()
    }

    fun getImagePath(context: Context): String? =
        prefs(context).getString(KEY_IMAGE_PATH, null)

    fun setImagePath(context: Context, path: String?) {
        prefs(context).edit().putString(KEY_IMAGE_PATH, path).apply()
    }
}
