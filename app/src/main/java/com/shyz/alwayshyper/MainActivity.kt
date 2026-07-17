package com.shyz.alwayshyper

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shyz.alwayshyper.ui.theme.*

private enum class Tab(val label: String) { APPEARANCE("Вид"), ABOUT("О приложении") }

// Real, on-screen overlay width/height range (dp). Each device/screen is
// different, so this is intentionally generous — the phone mockup below
// re-maps it into a safe preview range so it never overflows the mockup box.
private const val WIDTH_MIN = 40f
private const val WIDTH_MAX = 160f
private const val HEIGHT_MIN = 16f
private const val HEIGHT_MAX = 40f
private const val PREVIEW_PILL_MIN = 26f
private const val PREVIEW_PILL_MAX = 56f
private const val PREVIEW_HEIGHT_MIN = 8f
private const val PREVIEW_HEIGHT_MAX = 14f

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* handled via lifecycle check in Compose */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlwaysHyperTheme {
                AlwaysHyperApp(
                    requestOverlayPermission = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        overlayPermissionLauncher.launch(intent)
                    }
                )
            }
        }
    }
}

@Composable
private fun AlwaysHyperApp(requestOverlayPermission: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.APPEARANCE) }

    var overlayOn by remember { mutableStateOf(Prefs.isOverlayOn(context)) }
    var width by remember { mutableFloatStateOf(Prefs.getWidth(context).toFloat()) }
    var height by remember { mutableFloatStateOf(Prefs.getHeight(context).toFloat()) }
    var radius by remember { mutableFloatStateOf(Prefs.getRadius(context).toFloat()) }
    var topOffset by remember { mutableFloatStateOf(Prefs.getTopOffset(context).toFloat()) }
    var isRecording by remember { mutableStateOf(Prefs.isRecordingActive(context)) }

    // Recording can be stopped from outside this screen (the Quick Settings
    // tile, the notification's "Стоп" button, or the system's own recording
    // chip), so we listen for the change directly instead of only checking
    // on resume.
    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Prefs.KEY_IS_RECORDING) {
                isRecording = Prefs.isRecordingActive(context)
            }
        }
        Prefs.prefs(context).registerOnSharedPreferenceChangeListener(listener)
        onDispose { Prefs.prefs(context).unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun onStartRecording() {
        context.startActivity(
            Intent(context, RequestCaptureActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun onStopRecording() {
        RecordingService.stop(context)
        isRecording = false
    }

    // Re-check the system permission whenever we come back to the foreground
    // (e.g. after the user grants/denies it in Settings).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasPermission = Settings.canDrawOverlays(context)
                if (overlayOn && hasPermission) {
                    OverlayService.start(context)
                } else if (overlayOn && !hasPermission) {
                    // permission was never granted / was revoked: reflect that in the UI
                    overlayOn = false
                    Prefs.setOverlayOn(context, false)
                }
                isRecording = Prefs.isRecordingActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun onToggleOverlay(newValue: Boolean) {
        if (newValue) {
            if (Settings.canDrawOverlays(context)) {
                overlayOn = true
                Prefs.setOverlayOn(context, true)
                OverlayService.start(context)
            } else {
                requestOverlayPermission()
                // Optimistic: will be corrected on ON_RESUME if permission denied.
                overlayOn = true
                Prefs.setOverlayOn(context, true)
            }
        } else {
            overlayOn = false
            Prefs.setOverlayOn(context, false)
            OverlayService.stop(context)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg),
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Header()

                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(320)) togetherWith fadeOut(tween(120)))
                    },
                    label = "tab"
                ) { current ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        when (current) {
                            Tab.APPEARANCE -> AppearanceTab(
                                overlayOn = overlayOn,
                                width = width,
                                height = height,
                                radius = radius,
                                topOffset = topOffset,
                                isRecording = isRecording,
                                onOverlayToggle = ::onToggleOverlay,
                                onWidthChange = {
                                    width = it
                                    Prefs.setWidth(context, it.toInt())
                                },
                                onHeightChange = {
                                    height = it
                                    Prefs.setHeight(context, it.toInt())
                                },
                                onRadiusChange = {
                                    radius = it
                                    Prefs.setRadius(context, it.toInt())
                                },
                                onTopOffsetChange = {
                                    topOffset = it
                                    Prefs.setTopOffset(context, it.toInt())
                                },
                                onStartRecording = ::onStartRecording,
                                onStopRecording = ::onStopRecording
                            )
                            Tab.ABOUT -> AboutTab()
                        }
                        Spacer(modifier = Modifier.height(110.dp))
                    }
                }
            }

            BottomNav(
                tab = tab,
                onTabSelected = { tab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
    ) {
        Text(
            text = "Always Hyper",
            color = TextPrimary,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "by shyz  ·  v1.3 beta",
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}

/** iOS-style section caption: small, gray, uppercase, above a grouped card. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp)
    )
}

/** iOS-style grouped card: flat dark surface, rounded corners, no border/glow. */
@Composable
private fun Section(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
    ) {
        content()
    }
}

/** Thin separator line between rows inside a Section, indented like iOS table dividers. */
@Composable
private fun RowSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(RowDivider)
    )
}

/** Small gray caption below a Section, like an iOS list footer. */
@Composable
private fun SectionFooter(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
    )
}

@Composable
private fun AppearanceTab(
    overlayOn: Boolean,
    width: Float,
    height: Float,
    radius: Float,
    topOffset: Float,
    isRecording: Boolean,
    onOverlayToggle: (Boolean) -> Unit,
    onWidthChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit,
    onRadiusChange: (Float) -> Unit,
    onTopOffsetChange: (Float) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    SectionHeader("Предпросмотр")
    Section {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
        ) {
            PhoneMock(
                isIsland = false,
                active = !overlayOn,
                widthDp = width,
                heightDp = height,
                radiusDp = radius,
                topOffsetDp = topOffset,
                label = "Классический",
                onSelect = { onOverlayToggle(false) }
            )
            PhoneMock(
                isIsland = true,
                active = overlayOn,
                widthDp = width,
                heightDp = height,
                radiusDp = radius,
                topOffsetDp = topOffset,
                label = "Always-On",
                onSelect = { onOverlayToggle(true) }
            )
        }
    }

    SectionHeader("Остров")
    Section {
        SettingsRow {
            Column(modifier = Modifier.weight(1f)) {
                Text("Интерактивный остров", color = TextPrimary, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (overlayOn) "Активен поверх всех приложений" else "Отключён",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            IslandToggle(checked = overlayOn, onChange = onOverlayToggle)
        }
        RowSeparator()
        SettingsSliderRow(
            label = "Ширина",
            value = width,
            unit = "px",
            valueRange = WIDTH_MIN..WIDTH_MAX,
            onChange = onWidthChange,
            enabled = overlayOn
        )
        RowSeparator()
        SettingsSliderRow(
            label = "Высота",
            value = height,
            unit = "px",
            valueRange = HEIGHT_MIN..HEIGHT_MAX,
            onChange = onHeightChange,
            enabled = overlayOn
        )
        RowSeparator()
        SettingsSliderRow(
            label = "Скругление углов",
            value = radius,
            unit = "px",
            valueRange = 0f..16f,
            onChange = onRadiusChange,
            enabled = overlayOn
        )
        RowSeparator()
        SettingsSliderRow(
            label = "Позиция по вертикали",
            value = topOffset,
            unit = "px",
            valueRange = 2f..40f,
            onChange = onTopOffsetChange,
            enabled = overlayOn
        )
    }
    SectionFooter(
        if (overlayOn)
            "Остров остаётся видимым постоянно, даже когда экран заблокирован."
        else
            "В классическом режиме показывается только вырез камеры."
    )

    SectionHeader("Запись экрана")
    Section {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (isRecording) onStopRecording() else onStartRecording() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isRecording) "Остановить запись" else "Начать запись экрана",
                color = if (isRecording) DestructiveRed else AccentBlue,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
    SectionFooter(
        (if (isRecording)
            "Идёт запись — на острове горит красный кружок сбоку. "
        else
            "Во время записи на острове загорается красный кружок сбоку, как индикатор на iPhone. ") +
            "Файлы сохраняются в Movies/AlwaysHyper. Быстрый доступ: разверни шторку уведомлений → " +
            "значок карандаша (редактировать плитки) → найди \"Always Hyper\" → перетащи наверх."
    )
}

@Composable
private fun AboutTab() {
    SectionHeader("О приложении")
    Section {
        InfoRow("Версия", "1.3.0-beta")
        RowSeparator()
        InfoRow("Разработчик", "shyz")
        RowSeparator()
        InfoRow("Совместимость", "Android 8.0+")
    }
}

@Composable
private fun SettingsRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    SettingsRow {
        Text(label, color = TextPrimary, fontSize = 16.sp)
        Text(value, color = TextSecondary, fontSize = 15.sp)
    }
}

@Composable
private fun IslandToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) AccentBlue else ThumbDisabled)
            .clickable { onChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(25.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
    }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    value: Float,
    unit: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = TextPrimary, fontSize = 15.sp)
            Text(
                text = "${value.toInt()}$unit",
                color = if (enabled) AccentBlue else TextTertiary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = AccentBlue,
                inactiveTrackColor = RowDivider,
                disabledThumbColor = Color.White.copy(alpha = 0.5f),
                disabledActiveTrackColor = AccentBlue.copy(alpha = 0.3f),
                disabledInactiveTrackColor = RowDivider
            )
        )
    }
}

@Composable
private fun PhoneMock(
    isIsland: Boolean,
    active: Boolean,
    widthDp: Float,
    heightDp: Float,
    radiusDp: Float,
    topOffsetDp: Float,
    label: String,
    onSelect: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onSelect() }
    ) {
        Box(
            modifier = Modifier
                .size(width = 106.dp, height = 214.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF0B0B0C))
                .border(
                    width = if (active) 2.dp else 1.dp,
                    color = if (active) AccentBlue else RowDivider,
                    shape = RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            if (!isIsland) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black)
                )
            } else {
                val widthFraction = ((widthDp - WIDTH_MIN) / (WIDTH_MAX - WIDTH_MIN)).coerceIn(0f, 1f)
                val heightFraction = ((heightDp - HEIGHT_MIN) / (HEIGHT_MAX - HEIGHT_MIN)).coerceIn(0f, 1f)
                val pillWidth: Dp = (PREVIEW_PILL_MIN + widthFraction * (PREVIEW_PILL_MAX - PREVIEW_PILL_MIN)).dp
                val pillHeight: Dp = (PREVIEW_HEIGHT_MIN + heightFraction * (PREVIEW_HEIGHT_MAX - PREVIEW_HEIGHT_MIN)).dp
                Box(
                    modifier = Modifier
                        .padding(top = topOffsetDp.coerceIn(2f, 34f).dp)
                        .width(pillWidth)
                        .height(pillHeight)
                        .clip(RoundedCornerShape((radiusDp.coerceIn(0f, 16f) * 0.6f).dp))
                        .background(Color.Black)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            color = if (active) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BottomNav(
    tab: Tab,
    onTabSelected: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(RowDivider)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavBg)
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavItem(
                icon = Icons.Filled.Smartphone,
                label = Tab.APPEARANCE.label,
                active = tab == Tab.APPEARANCE,
                onClick = { onTabSelected(Tab.APPEARANCE) }
            )
            NavItem(
                icon = Icons.Filled.Info,
                label = Tab.ABOUT.label,
                active = tab == Tab.ABOUT,
                onClick = { onTabSelected(Tab.ABOUT) }
            )
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) AccentBlue else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            label,
            color = if (active) AccentBlue else TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
