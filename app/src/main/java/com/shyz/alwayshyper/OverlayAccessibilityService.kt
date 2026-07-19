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
 * Three things matter for a stable, flicker-free result:
 * 1. The bar for "fullscreen" has to be strict. Modern Android apps draw
 *    edge-to-edge by default, so a window that merely starts at y=0 (top of
 *    screen) is normal, not a signal of immersive mode. We require the
 *    window to cover the ENTIRE screen, top AND bottom, which only
 *    genuinely fullscreen/immersive content (video players, games) does.
 * 2. Events can arrive in rapid bursts for minor UI changes. Reacting to
 *    every single one causes visible flicker, so each burst is collapsed
 *    into one delayed check.
 * 3. Short system animations (e.g. closing the notification shade) can
 *    briefly report an inconsistent, in-between window state — a single
 *    delayed check can land exactly in that window and misfire for a
 *    fraction of a second. To filter that out, a new value is only trusted
 *    (written to Prefs) once it's been seen on two checks in a row; a
 *    one-off blip that disappears on the next check never gets applied.
 */
class OverlayAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = Runnable { evaluateAndMaybeApply() }
    private var lastCandidate: Boolean? = null

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
        // Collapse bursts of events into a single check, ~300ms after things
        // settle down, instead of reacting to every single one.
        handler.removeCallbacks(checkRunnable)
        handler.postDelayed(checkRunnable, 300)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(checkRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
    }

    private fun evaluateAndMaybeApply() {
        val candidate = computeIsFullscreen()
        if (candidate == lastCandidate) {
            // Same result two checks in a row -> trust it and apply.
            Prefs.setForegroundFullscreen(this, candidate)
        } else {
            // First time seeing this value — could be a transient blip from
            // a system animation (e.g. closing the notification shade).
            // Remember it and check again shortly before trusting it.
            lastCandidate = candidate
            handler.postDelayed(checkRunnable, 300)
        }
    }

    private fun computeIsFullscreen(): Boolean {
        val activeWindow = try {
            windows?.firstOrNull { it.isActive }
        } catch (_: Exception) {
            null
        } ?: return false

        val bounds = Rect()
        activeWindow.getBoundsInScreen(bounds)

        val screenHeight = resources.displayMetrics.heightPixels
        // Require the window to cover the screen edge-to-edge on BOTH top
        // and bottom. A normal edge-to-edge app still stops short of the
        // very bottom (leaves room for the gesture/nav bar); only a truly
        // fullscreen video/game covers the whole thing.
        return bounds.top <= 0 && bounds.bottom >= screenHeight
    }
}
