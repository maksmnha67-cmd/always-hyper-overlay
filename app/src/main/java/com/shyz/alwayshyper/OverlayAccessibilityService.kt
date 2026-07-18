package com.shyz.alwayshyper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent

/**
 * Detects whether the foreground app is currently drawn fullscreen/immersive
 * (e.g. a video player with the status bar hidden) so OverlayService can hide
 * the island along with the system status bar, exactly like the battery
 * percentage and clock do.
 *
 * This requires the user to manually enable this service once, in
 * Settings > Accessibility — Android does not allow apps to detect other
 * apps' fullscreen state any other way.
 */
class OverlayAccessibilityService : AccessibilityService() {

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
        updateFullscreenState()
    }

    override fun onInterrupt() {
        // Nothing to clean up.
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
        // A normal app window starts below the status bar. A fullscreen /
        // immersive one (video player, game, etc.) is drawn starting at the
        // very top of the screen, covering where the status bar would be.
        val isFullscreen = bounds.top <= 0
        Prefs.setForegroundFullscreen(this, isFullscreen)
    }
}
