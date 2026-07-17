package com.shyz.alwayshyper

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
            val context = LocalContext.current
            var themeColor by remember { mutableStateOf(Prefs.getTheme(context)) }

            AlwaysHyperTheme(themeColor = themeColor) {
                AlwaysHyperApp(
                    themeColor = themeColor,
                    onThemeChange = { newTheme ->
                        themeColor = newTheme
                        Prefs.setTheme(context, newTheme)
                    },
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
private fun AlwaysHyperApp(
    themeColor: ThemeColor,
    onThemeChange: (ThemeColor) -> Unit,
    requestOverlayPermission: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.APPEARANCE) }

    var overlayOn by remember { mutableStateOf(Prefs.isOverlayOn(context)) }
    var width by remember { mutableFloatStateOf(Prefs.getWidth(context).toFloat()) }
    var height by remember { mutableFloatStateOf(Prefs.getHeight(context).toFloat()) }
    var radius by remember { mutableFloatStateOf(Prefs.getRadius(context).toFloat()) }
    var topOffset by remember { mutableFloatStateOf(Prefs.getTopOffset(context).toFloat()) }
    var autoContrast by remember { mutableStateOf(Prefs.isAutoContrastOn(context)) }
    var imagePath by remember { mutableStateOf(Prefs.getImagePath(context)) }

    val coroutineScope = rememberCoroutineScope()

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    try {
                        val outFile = File(context.filesDir, "island_image.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            outFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        outFile.absolutePath
                    } catch (_: Exception) {
                        null
                    }
                }
                if (savedPath != null) {
                    imagePath = savedPath
                    Prefs.setImagePath(context, savedPath)
                }
            }
        }
    }

    fun onClearImage() {
        imagePath = null
        Prefs.setImagePath(context, null)
        File(context.filesDir, "island_image.jpg").delete()
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
            .background(
                Brush.verticalGradient(listOf(BgTop, BgBottom))
            ),
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
                            .padding(horizontal = 20.dp)
                    ) {
                        when (current) {
                            Tab.APPEARANCE -> AppearanceTab(
                                overlayOn = overlayOn,
                                width = width,
                                height = height,
                                radius = radius,
                                topOffset = topOffset,
                                autoContrast = autoContrast,
                                imagePath = imagePath,
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
                                onAutoContrastChange = {
                                    autoContrast = it
                                    Prefs.setAutoContrastOn(context, it)
                                },
                                onPickImage = {
                                    pickImageLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onClearImage = ::onClearImage
                            )
                            Tab.ABOUT -> AboutTab(
                                selectedTheme = themeColor,
                                onThemeSelect = onThemeChange
                            )
                        }
                        Spacer(modifier = Modifier.height(120.dp))
                    }
                }
            }

            BottomNav(
                tab = tab,
                onTabSelected = { tab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun Header() {
    val accent = LocalAppAccent.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(accent.primary, accent.secondary))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.FlashOn,
                    contentDescription = null,
                    tint = accent.onAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Always Hyper",
                color = TextMain,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "by shyz  ·  v1.3 beta",
            color = Muted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun AppCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(
                width = 1.dp,
                color = if (glow) LocalAppAccent.current.primary.copy(alpha = 0.35f) else LineColor,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp)
    ) {
        content()
    }
}

@Composable
private fun AppearanceTab(
    overlayOn: Boolean,
    width: Float,
    height: Float,
    radius: Float,
    topOffset: Float,
    autoContrast: Boolean,
    imagePath: String?,
    onOverlayToggle: (Boolean) -> Unit,
    onWidthChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit,
    onRadiusChange: (Float) -> Unit,
    onTopOffsetChange: (Float) -> Unit,
    onAutoContrastChange: (Boolean) -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit
) {
    Spacer(modifier = Modifier.height(20.dp))

    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
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

    Spacer(modifier = Modifier.height(20.dp))

    AppCard(glow = overlayOn) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Интерактивный остров", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (overlayOn) "Активен поверх всех приложений" else "Отключён",
                    color = if (overlayOn) Color(0xFF8FA3FF) else Muted,
                    fontSize = 12.sp
                )
            }
            IslandToggle(checked = overlayOn, onChange = onOverlayToggle)
        }

        Spacer(modifier = Modifier.height(20.dp))

        LabeledSlider(
            label = "Ширина",
            value = width,
            unit = "px",
            valueRange = WIDTH_MIN..WIDTH_MAX,
            onChange = onWidthChange,
            enabled = overlayOn
        )
        Spacer(modifier = Modifier.height(20.dp))
        LabeledSlider(
            label = "Высота",
            value = height,
            unit = "px",
            valueRange = HEIGHT_MIN..HEIGHT_MAX,
            onChange = onHeightChange,
            enabled = overlayOn
        )
        Spacer(modifier = Modifier.height(20.dp))
        LabeledSlider(
            label = "Скругление углов",
            value = radius,
            unit = "px",
            valueRange = 0f..16f,
            onChange = onRadiusChange,
            enabled = overlayOn
        )
        Spacer(modifier = Modifier.height(20.dp))
        LabeledSlider(
            label = "Позиция по вертикали",
            value = topOffset,
            unit = "px",
            valueRange = 2f..40f,
            onChange = onTopOffsetChange,
            enabled = overlayOn
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (overlayOn)
                "Остров остаётся видимым постоянно, даже когда экран заблокирован."
            else
                "В классическом режиме показывается только вырез камеры.",
            color = MutedDim,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Авто-контраст", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "По умолчанию остров чёрный. Включи, чтобы на тёмном фоне он " +
                        "становился ярче (цвет темы), а на светлом оставался чёрным.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            IslandToggle(checked = autoContrast, onChange = onAutoContrastChange)
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    AppCard {
        Text("Фото для острова", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "Вместо цвета остров может показывать выбранное фото",
            color = Muted,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (imagePath != null) {
                val bitmap = remember(imagePath) {
                    android.graphics.BitmapFactory.decodeFile(imagePath)?.asImageBitmap()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }

            Column {
                Text(
                    text = if (imagePath != null) "Заменить фото" else "Выбрать из галереи",
                    color = LocalAppAccent.current.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onPickImage() }
                )
                if (imagePath != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Убрать фото",
                        color = Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { onClearImage() }
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutTab(
    selectedTheme: ThemeColor,
    onThemeSelect: (ThemeColor) -> Unit
) {
    Spacer(modifier = Modifier.height(20.dp))
    AppCard {
        AboutRow("Версия", "1.3.0-beta")
        Spacer(modifier = Modifier.height(14.dp))
        AboutRow("Разработчик", "shyz")
        Spacer(modifier = Modifier.height(14.dp))
        AboutRow("Совместимость", "Android 8.0+")
    }

    Spacer(modifier = Modifier.height(20.dp))
    AppCard {
        Text("Цвет темы", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "Меняет акцентный цвет во всём приложении",
            color = Muted,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        ThemePicker(selected = selectedTheme, onSelect = onThemeSelect)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "На тёмном фоне остров автоматически становится ярче — вместо чёрного " +
                "используется выбранный здесь цвет, чтобы его было видно.",
            color = MutedDim,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun ThemePicker(selected: ThemeColor, onSelect: (ThemeColor) -> Unit) {
    val rows = ThemeColor.entries.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { theme ->
                    ThemeSwatch(
                        theme = theme,
                        isSelected = theme == selected,
                        onClick = { onSelect(theme) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(theme: ThemeColor, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.linearGradient(listOf(theme.toComposeColor(), theme.toComposeSecondary()))
                )
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) Color.White else LineColor,
                    shape = RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (theme.isLight) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(theme.label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Muted, fontSize = 14.sp)
        Text(value, color = TextMain, fontSize = 14.sp)
    }
}

@Composable
private fun IslandToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalAppAccent.current
    val trackBrush = if (checked) Brush.linearGradient(listOf(accent.primary, accent.secondary))
    else Brush.linearGradient(listOf(LineColor, LineColor))

    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(RoundedCornerShape(50))
            .background(trackBrush)
            .clickable { onChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    unit: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    enabled: Boolean
) {
    val accent = LocalAppAccent.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(
                text = "${value.toInt()}$unit",
                color = if (enabled) accent.primary else ThumbDisabled,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accent.primary,
                inactiveTrackColor = LineColor,
                disabledThumbColor = Color.White.copy(alpha = 0.5f),
                disabledActiveTrackColor = accent.primary.copy(alpha = 0.3f),
                disabledInactiveTrackColor = LineColor
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
        val accent = LocalAppAccent.current
        Box(
            modifier = Modifier
                .size(width = 106.dp, height = 214.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A2240), Color(0xFF0F1428))))
                .border(
                    width = 2.dp,
                    color = if (active) accent.primary else LineColor,
                    shape = RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            // soft glow behind the notch/island
            Canvas(modifier = Modifier.matchParentSize()) {
                val brush = ShaderBrush(
                    RadialGradientShader(
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width.coerceAtLeast(1f),
                        colors = listOf(accent.primary.copy(alpha = 0.20f), Color.Transparent)
                    )
                )
                drawRect(brush = brush)
            }

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
            color = if (active) TextMain else Muted,
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
    Box(
        modifier = modifier.clip(RoundedCornerShape(20.dp))
    ) {
        // Real frosted-glass effect: soft blurred colour glow sitting under the
        // translucent bar. Compose can't sample pixels from *behind* a floating
        // element without extra libraries, so this fakes the classic "light
        // passing through frosted glass" look using an actual RenderEffect blur
        // (API 31+). On older Android it just falls back to plain translucency.
        GlassGlow(modifier = Modifier.matchParentSize())

        Row(
            modifier = Modifier
                .background(NavBg)
                .border(1.dp, LineColor.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                .padding(6.dp)
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
private fun GlassGlow(modifier: Modifier = Modifier) {
    val accent = LocalAppAccent.current
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.graphicsLayer { renderEffect = blurRenderEffect(30f) }
    } else {
        Modifier
    }
    Canvas(modifier = modifier.then(blurModifier)) {
        drawCircle(
            color = accent.primary.copy(alpha = 0.45f),
            radius = size.height * 1.4f,
            center = Offset(size.width * 0.18f, size.height * 0.15f)
        )
        drawCircle(
            color = accent.secondary.copy(alpha = 0.40f),
            radius = size.height * 1.4f,
            center = Offset(size.width * 0.85f, size.height * 0.9f)
        )
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
private fun blurRenderEffect(radius: Float): androidx.compose.ui.graphics.RenderEffect =
    android.graphics.RenderEffect
        .createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
        .asComposeRenderEffect()

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val accent = LocalAppAccent.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (active) Brush.linearGradient(listOf(accent.primary, accent.secondary))
                else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) accent.onAccent else Muted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            color = if (active) accent.onAccent else Muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
