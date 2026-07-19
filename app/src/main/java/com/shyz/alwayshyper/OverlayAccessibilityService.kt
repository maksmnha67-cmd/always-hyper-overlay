package com.shyz.alwayshyper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Detects whether the system status bar is currently hidden (fullscreen /
 * immersive mode — e.g. a video player or game) so OverlayService can hide
 * the island right along with it, exactly like the battery percentage and
 * clock do.
 *
 * This requires the user to manually enable this service once, in
 * Settings > Accessibility — Android does not allow apps to detect other
 * apps' fullscreen state any other way.
 *
 * Earlier versions of this check inferred fullscreen from the foreground
 * app's own window bounds, but that's unstable: video players show their
 * own playback-control overlay for a second or two when tapped, which can
 * shift those bounds and made the island flash on its own during normal
 * viewing. Instead, this looks for the status bar's OWN system window
 * (a thin strip pinned to the very top of the screen) — if it's present,
 * the status bar is showing; if it's gone, the app has hidden it. That's
 * unaffected by whatever UI the app itself shows or hides.
 *
 * Two more things keep this flicker-free:
 * 1. Events can arrive in rapid bursts for minor UI changes. Reacting to
 *    every single one causes visible flicker, so each burst is collapsed
 *    into one delayed check.
 * 2. A new value is only trusted (written to Prefs) once it's been seen on
 *    two checks in a row; a one-off blip from a system animation (e.g.
 *    closing the notification shade) never gets applied.
 */
class OverlayAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = Runnable { evaluateAndMaybeApply() }
    private var lastCandidate: Boolean? = null

    // Belt-and-braces fallback: some devices/apps don't fire the events we
    // listen for when entering immersive mode. Re-checking every 400ms
    // regardless catches those cases too, at negligible cost.
    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            evaluateAndMaybeApply()
            handler.postDelayed(this, 400)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            // Listen to everything, not just window-state/windows-changed —
            // on some devices/apps, entering immersive mode doesn't fire
            // either of those specifically, but something always does.
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        handler.post(periodicCheckRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Collapse bursts of events into a single check, ~150ms after things
        // settle down, instead of reacting to every single one.
        handler.removeCallbacks(checkRunnable)
        handler.postDelayed(checkRunnable, 150)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(checkRunnable)
        handler.removeCallbacks(periodicCheckRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        handler.removeCallbacks(periodicCheckRunnable)
    }

    private fun evaluateAndMaybeApply() {
        val candidate = computeIsFullscreen()
        if (candidate == lastCandidate) {
            // Same result two checks in a row -> trust it and apply.
            Prefs.setForegroundFullscreen(this, candidate)
        } else {
            // First time seeing this value — could be a transient blip from
            // a system animation. Remember it and check again shortly
            // before trusting it.
            lastCandidate = candidate
            handler.postDelayed(checkRunnable, 150)
        }
    }

    private fun computeIsFullscreen(): Boolean {
        val windowList = try {
            windows
        } catch (_: Exception) {
            null
        } ?: return false

        val screenWidth = resources.displayMetrics.widthPixels

        val statusBarPresent = windowList.any { window ->
            if (window.type != AccessibilityWindowInfo.TYPE_SYSTEM) return@any false
            val bounds = Rect()
            window.getBoundsInScreen(bounds)
            // The status bar is a thin strip pinned to the very top,
            // spanning almost the full screen width.
            bounds.top <= 0 && bounds.height() in 1..200 && bounds.width() >= screenWidth * 0.9
        }

        return !statusBarPresent
    }
}
