package com.retrovibe.os.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.model.WindowState
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroThemeColors
import com.retrovibe.os.theme.RetroThemes
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

fun Modifier.win98Bevel(
    raised: Boolean,
    light: Color,
    dark: Color,
    deep: Color,
    strokeDp: Dp = 2.dp
): Modifier = this.drawBehind {
    val s = strokeDp.toPx()
    val tl = if (raised) light else dark
    val br = if (raised) deep else light
    // Top
    drawRect(tl, Offset(0f, 0f), Size(size.width, s))
    // Left
    drawRect(tl, Offset(0f, 0f), Size(s, size.height))
    // Bottom
    drawRect(br, Offset(0f, size.height - s), Size(size.width, s))
    // Right
    drawRect(br, Offset(size.width - s, 0f), Size(s, size.height))
}

fun Modifier.win98Bevel(
    sunken: Boolean,
    bg: Color,
    strokeDp: Dp = 2.dp
): Modifier = this
    .background(bg)
    .win98Bevel(
        raised = !sunken,
        light = Color.White,
        dark = Color(0xFF808080),
        deep = Color(0xFF202020),
        strokeDp = strokeDp
    )

@Composable
fun RetroButton(
    label: String,
    palette: RetroThemeColors,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    RetroButton(
        label = label,
        theme = palette,
        active = active,
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
fun BevelBox(
    theme: RetroThemeColors,
    raised: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(if (raised) theme.windowBg else theme.windowSurface)
            .win98Bevel(raised, theme.borderLight, theme.borderDark, theme.borderDeep)
            .padding(2.dp),
        content = content
    )
}

@Composable
fun RetroButton(
    label: String? = null,
    theme: RetroThemeColors,
    soundEnabled: Boolean = true,
    active: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    onClick: () -> Unit,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var clickFlash by remember { mutableStateOf(false) }
    var clickSeq by remember { mutableIntStateOf(0) }

    LaunchedEffect(clickSeq) {
        if (clickSeq > 0) {
            clickFlash = true
            delay(130)
            clickFlash = false
        }
    }

    val isDown = pressed || clickFlash
    val sunken = isDown || active
    val baseBg = if (active) theme.accentSoft else theme.windowBg
    val bgColor = if (isDown) {
        theme.titleBarActive.copy(alpha = 0.28f).compositeOver(baseBg)
    } else {
        baseBg
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .background(bgColor)
            .win98Bevel(!sunken, theme.borderLight, theme.borderDark, theme.borderDeep)
            .then(
                if (isDown) Modifier.border(1.dp, theme.borderDeep.copy(alpha = 0.65f))
                else Modifier
            )
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) {
                clickSeq++
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
                onClick()
            }
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .offset(x = if (isDown) 1.5.dp else 0.dp, y = if (isDown) 1.5.dp else 0.dp)
    ) {
        if (content != null) {
            content()
        } else if (label != null) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = (textColor ?: theme.textPrimary).copy(alpha = if (enabled) 1f else 0.45f)
            )
        }
    }
}

@Composable
fun RetroWindow(
    vm: OSViewModel,
    win: WindowState,
    boundsWidthDp: Float,
    boundsHeightDp: Float,
    icon: String? = null,
    accentTitleBar: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!win.isOpen || win.isMinimized) return
    val theme = RetroThemes.of(vm.themeId)
    val isActive = vm.activeWindowId == win.id
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current

    val wDp = if (win.isMaximized) boundsWidthDp - 4f else win.width.coerceAtMost(boundsWidthDp - 8f)
    val hDp = if (win.isMaximized) boundsHeightDp - 4f else win.height.coerceAtMost(boundsHeightDp - 8f)
    val leftDp = if (win.isMaximized) 2f else win.x.coerceIn(2f, (boundsWidthDp - wDp - 2f).coerceAtLeast(2f))
    val topDp = if (win.isMaximized) 2f else win.y.coerceIn(2f, (boundsHeightDp - hDp - 2f).coerceAtLeast(2f))

    val titleBrush = Brush.horizontalGradient(
        colors = when {
            !isActive -> listOf(theme.titleBarInactive, Color(0xFFB5B8C4))
            accentTitleBar -> listOf(theme.titleBarAccent, theme.accentPink)
            else -> listOf(theme.titleBarActive, theme.titleBarAccent)
        }
    )

    Box(
        modifier = Modifier
            .offset { IntOffset(with(density) { leftDp.dp.roundToPx() }, with(density) { topDp.dp.roundToPx() }) }
            .size(wDp.dp, hDp.dp)
            .zIndex(win.zIndex.toFloat())
            .background(theme.windowBg)
            .win98Bevel(true, theme.borderLight, theme.borderDark, theme.borderDeep)
            .padding(2.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
                if (vm.activeWindowId != win.id) vm.focusWindow(win.id)
            }
            .pointerInput(win.id) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.any { it.pressed } && vm.activeWindowId != win.id) {
                            vm.focusWindow(win.id)
                        }
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Win98 2-tone gradient Title Bar (Draggable)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(29.dp)
                    .background(titleBrush)
                    .pointerInput(win.id, win.isMaximized) {
                        if (!win.isMaximized) {
                            detectDragGestures(
                                onDragStart = { vm.focusWindow(win.id) }
                            ) { change, dragAmount ->
                                change.consume()
                                val dxDp = dragAmount.x / density.density
                                val dyDp = dragAmount.y / density.density
                                val cur = vm.windows[win.id] ?: return@detectDragGestures
                                vm.moveWindow(
                                    win.id,
                                    (cur.x + dxDp).coerceIn(-40f, boundsWidthDp - 80f),
                                    (cur.y + dyDp).coerceIn(0f, boundsHeightDp - 56f)
                                )
                            }
                        }
                    }
                    .padding(horizontal = 4.dp)
            ) {
                if (icon != null) {
                    PixelIcon(icon, size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = win.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) theme.titleTextActive else theme.titleTextInactive,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    WinTitleBtn("_", theme, vm.soundEnabled) { vm.minimizeWindow(win.id) }
                    WinTitleBtn(if (win.isMaximized) "❐" else "□", theme, vm.soundEnabled) { vm.toggleMaximizeWindow(win.id) }
                    WinTitleBtn("×", theme, vm.soundEnabled) { vm.closeWindow(win.id) }
                }
            }

            // Content Body
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content()
            }
        }

        // Bottom-Right Classic Win98 Resize Grip
        if (!win.isMaximized) {
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .pointerInput(win.id) {
                        detectDragGestures(
                            onDragStart = { vm.focusWindow(win.id) }
                        ) { change, dragAmount ->
                            change.consume()
                            val dxDp = dragAmount.x / density.density
                            val dyDp = dragAmount.y / density.density
                            val cur = vm.windows[win.id] ?: return@detectDragGestures
                            vm.resizeWindow(
                                win.id,
                                (cur.width + dxDp).coerceIn(185f, boundsWidthDp - 4f),
                                (cur.height + dyDp).coerceIn(130f, boundsHeightDp - 4f)
                            )
                        }
                    }
            ) {
                Canvas(modifier = Modifier.size(12.dp).padding(1.dp)) {
                    val d = theme.borderDark
                    val l = theme.borderLight
                    val pts = listOf(9f to 3f, 6f to 6f, 3f to 9f, 9f to 6f, 6f to 9f, 9f to 9f)
                    val s = size.width / 12f
                    pts.forEach { (px, py) ->
                        drawRect(d, Offset(px * s, py * s), Size(1.5f * s, 1.5f * s))
                        drawRect(l, Offset((px + 1) * s, (py + 1) * s), Size(s, s))
                    }
                }
            }
        }
    }
}

@Composable
private fun WinTitleBtn(label: String, theme: RetroThemeColors, soundEnabled: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var clickFlash by remember { mutableStateOf(false) }
    var clickSeq by remember { mutableIntStateOf(0) }

    LaunchedEffect(clickSeq) {
        if (clickSeq > 0) {
            clickFlash = true
            delay(110)
            clickFlash = false
        }
    }

    val isDown = pressed || clickFlash
    val bgColor = if (isDown) {
        theme.titleBarActive.copy(alpha = 0.30f).compositeOver(theme.windowBg)
    } else {
        theme.windowBg
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(23.dp, 21.dp)
            .background(bgColor)
            .win98Bevel(!isDown, theme.borderLight, theme.borderDark, theme.borderDeep, 1.5.dp)
            .clickable(interactionSource = interaction, indication = null) {
                clickSeq++
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
                onClick()
            }
            .offset(x = if (isDown) 1.dp else 0.dp, y = if (isDown) 1.dp else 0.dp)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = theme.textPrimary
        )
    }
}
