package com.shyz.alwayshyper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat

/**
 * Keeps a small black pill ("island") attached near the front camera, above
 * every other app, using a TYPE_APPLICATION_OVERLAY window.
 *
 * - Size/position/theme update live from SharedPreferences while the user
 *   changes them in MainActivity.
 * - The pill is anchored to the device's actual camera cutout (via the
 *   DisplayCutout API) when the device reports one, so it stays next to the
 *   camera regardless of screen rotation. If no cutout is reported (or on
 *   API < 28, before DisplayCutout existed), it falls back to a simple
 *   top-center position.
 * - While our own screen recording (RecordingService) is active, a small red
 *   dot lights up on the side of the pill — like the iOS recording
 *   indicator — instead of tinting the whole pill red.
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var islandContainer: FrameLayout? = null
    private var pillView: View? = null
    private var recordingDot: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var cutoutRect: Rect? = null

    private val recordingColor = android.graphics.Color.parseColor("#FF3B30")

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Prefs.KEY_WIDTH, Prefs.KEY_HEIGHT, Prefs.KEY_RADIUS, Prefs.KEY_TOP_OFFSET -> updateIslandLayout()
            Prefs.KEY_IS_RECORDING -> updateRecordingDot()
            Prefs.KEY_OVERLAY_ON -> {
                if (!Prefs.isOverlayOn(this)) stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()

        if (!Settings.canDrawOverlays(this)) {
            // No permission (revoked while service was running) -> bail out safely.
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addIslandView()

        Prefs.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sticky: if Android kills the process, try to restore the overlay.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Prefs.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        removeIslandView()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Covers rotation: re-evaluate the cutout position.
        updateIslandLayout()
    }

    // ---- overlay view -------------------------------------------------

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun buildPillDrawable(radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.BLACK)
            cornerRadius = dp(radiusDp).toFloat()
            // A subtle gray outline so the pill is still visible when the
            // app behind it is also black — otherwise it would vanish
            // completely against a black background.
            setStroke(dp(1), android.graphics.Color.parseColor("#4D4D4F"))
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    private fun addIslandView() {
        if (islandContainer != null) return

        val heightPx = dp(Prefs.getHeight(this))
        val widthPx = dp(Prefs.getWidth(this))
        val radiusDp = Prefs.getRadius(this)

        val container = FrameLayout(this)

        val pill = View(this)
        pill.background = buildPillDrawable(radiusDp)
        container.addView(
            pill,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        // Small red dot, shown on the left side of the pill while recording.
        val dotSizePx = (heightPx * 0.5f).toInt().coerceAtLeast(1)
        val dotMarginPx = (heightPx * 0.25f).toInt()
        val dot = View(this)
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(recordingColor)
        }
        dot.visibility = View.GONE
        container.addView(
            dot,
            FrameLayout.LayoutParams(dotSizePx, dotSizePx).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = dotMarginPx
            }
        )

        pillView = pill
        recordingDot = dot
        islandContainer = container

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(Prefs.getTopOffset(this@OverlayService))
        }

        try {
            windowManager?.addView(container, params)
            layoutParams = params
            updateRecordingDot()

            // Whenever insets are (re)delivered — including on rotation —
            // grab the camera cutout's bounds and anchor the pill to it.
            // (DisplayCutoutCompat only exposes the plural boundingRects list,
            // not per-side getters like the platform DisplayCutout class does.)
            ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
                val rects = insets.displayCutout?.boundingRects
                cutoutRect = rects?.firstOrNull { !it.isEmpty }
                applyPositioning()
                insets
            }
        } catch (_: Exception) {
            // Permission could have been revoked between the check and the call.
            stopSelf()
        }
    }

    /** Shows/hides the small red recording dot without touching size or position. */
    private fun updateRecordingDot() {
        val dot = recordingDot ?: return
        dot.visibility = if (Prefs.isRecordingActive(this)) View.VISIBLE else View.GONE
    }

    /** Re-centers the pill on the camera cutout if we know where it is, otherwise top-center. */
    private fun applyPositioning() {
        val container = islandContainer ?: return
        val params = layoutParams ?: return
        val rect = cutoutRect
        val topOffsetPx = dp(Prefs.getTopOffset(this))

        if (rect != null) {
            params.gravity = Gravity.TOP or Gravity.START
            params.x = rect.centerX() - (params.width / 2)
            params.y = rect.bottom + topOffsetPx
        } else {
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.x = 0
            params.y = topOffsetPx
        }

        try {
            windowManager?.updateViewLayout(container, params)
        } catch (_: Exception) {
            // View might already be detached; ignore.
        }
    }

    private fun updateIslandLayout() {
        val container = islandContainer ?: return
        val pill = pillView ?: return
        val dot = recordingDot ?: return
        val params = layoutParams ?: return

        val widthPx = dp(Prefs.getWidth(this))
        val heightPx = dp(Prefs.getHeight(this))
        val radiusDp = Prefs.getRadius(this)

        pill.background = buildPillDrawable(radiusDp)
        params.width = widthPx
        params.height = heightPx

        val dotSizePx = (heightPx * 0.5f).toInt().coerceAtLeast(1)
        val dotMarginPx = (heightPx * 0.25f).toInt()
        (dot.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            lp.width = dotSizePx
            lp.height = dotSizePx
            lp.marginStart = dotMarginPx
            dot.layoutParams = lp
        }

        try {
            windowManager?.updateViewLayout(container, params)
        } catch (_: Exception) {
            // View might already be detached; ignore.
        }
        applyPositioning()
    }

    private fun removeIslandView() {
        val container = islandContainer ?: return
        try {
            windowManager?.removeView(container)
        } catch (_: Exception) {
            // Already removed.
        }
        islandContainer = null
        pillView = null
        recordingDot = null
        layoutParams = null
    }

    // ---- foreground notification --------------------------------------

    private fun startInForeground() {
        val channelId = "always_hyper_overlay"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "Always Hyper",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Держит остров активным поверх приложений"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Always Hyper")
            .setContentText("Остров активен поверх приложений")
            .setSmallIcon(android.R.drawable.presence_online)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            // API 34+ requires an explicit foreground service type matching the manifest.
            startForeground(
                1,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1, notification)
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
