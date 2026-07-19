package com.shyz.alwayshyper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Detects whether the foreground app is currently drawn truly fullscreen /
 * immersive (e.g. a video player with the status bar hidden) so
 * OverlayService can hide the island along with the system status bar,
 * exactly like the battery percentage and clock do.
 *
 * This requires the user to manually enable this service once, in
 * Settings > Accessibility — Android does not allow apps to detect other
 * apps' fullscreen state any other way.
 *
 * Two things matter for a stable result:
 * 1. The bar for "fullscreen" has to be strict. Modern Android apps draw
 *    edge-to-edge by default, so a window that merely starts at y=0 (top of
 *    screen) is normal, not a signal of immersive mode — nearly every app
 *    would trigger a naive "top <= 0" check. We instead require the window
 *    to cover the ENTIRE screen, top AND bottom, which only genuinely
 *    fullscreen/immersive content (video players, games) does.
 * 2. Events can arrive in rapid bursts for minor UI changes within the same
 *    app. Reacting to every single one causes visible flicker. We debounce:
 *    only the last event within a short window actually updates the state.
 */
class OverlayAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val debouncedCheck = Runnable { updateFullscreenState() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Collapse bursts of events into a single check, ~250ms after things
        // settle down, instead of reacting to every single one.
        handler.removeCallbacks(debouncedCheck)
        handler.postDelayed(debouncedCheck, 250)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(debouncedCheck)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(debouncedCheck)
    }

    private fun updateFullscreenState() {
        val activeWindow = try {
            windows?.firstOrNull { it.isActive }
        } catch (_: Exception) {
            null
        }

        if (activeWindow == null) {
            Prefs.setForegroundFullscreen(this, false)
            return
        }

        val bounds = Rect()
        activeWindow.getBoundsInScreen(bounds)

        val screenHeight = resources.displayMetrics.heightPixels
        // Require the window to cover the screen edge-to-edge on BOTH top
        // and bottom. A normal edge-to-edge app still stops short of the
        // very bottom (leaves room for the gesture/nav bar); only a truly
        // fullscreen video/game covers the whole thing.
        val isFullscreen = bounds.top <= 0 && bounds.bottom >= screenHeight
        Prefs.setForegroundFullscreen(this, isFullscreen)
    }
}
