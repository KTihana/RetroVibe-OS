package com.retrovibe.os.ui

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.model.ScreensaverId
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.store.todayIso
import com.retrovibe.os.theme.RetroThemes
import com.retrovibe.os.ui.windows.*
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private data class DesktopShortcut(
    val id: String,
    val label: String,
    val icon: String,
    val windowId: String? = null,
    val noteId: String? = null,
    val badgeCount: Int = 0
)

private data class StartMenuEntry(
    val label: String,
    val icon: String,
    val desktopIconId: String? = null,
    val action: () -> Unit
)

@Composable
fun DesktopScreen(vm: OSViewModel) {
    val theme = RetroThemes.of(vm.themeId)
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current

    var booted by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(false) }
    var poweredOff by remember { mutableStateOf(false) }
    var showShutdownDialog by remember { mutableStateOf(false) }
    var startMenuOpen by remember { mutableStateOf(false) }
    var activeSubmenu by remember { mutableStateOf<String?>(null) } // programs | documents | games | settings
    var selectedIconId by remember { mutableStateOf<String?>(null) }
    var contextMenuIconId by remember { mutableStateOf<String?>(null) }
    var tabMenuWindowId by remember { mutableStateOf<String?>(null) }
    var showDesktopMenu by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var clockStr by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var idleSeconds by remember { mutableIntStateOf(0) }
    var loginNameInput by remember { mutableStateOf(vm.username) }

    fun showToast(msg: String) {
        toastMsg = msg
    }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            delay(2000)
            toastMsg = null
        }
    }

    // Live clock + Screensaver idle timer
    LaunchedEffect(vm.screensaverWaitSec, vm.screensaverId, vm.screensaverActive) {
        while (true) {
            delay(1000)
            clockStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            if (booted && !poweredOff && vm.screensaverWaitSec > 0 && vm.screensaverId != ScreensaverId.NONE && !vm.screensaverActive) {
                idleSeconds++
                if (idleSeconds >= vm.screensaverWaitSec) {
                    vm.screensaverActive = true
                    idleSeconds = 0
                }
            }
        }
    }

    // Boot screen
    if (!booted) {
        BootScreen(onFinish = {
            booted = true
            loggedIn = !vm.loginPromptEnabled
            RetroSoundEngine.play(RetroSound.STARTUP, vm.soundEnabled)
        })
        return
    }

    // Power-off screen: show classic Win98 message briefly, then exit the app!
    if (poweredOff) {
        val activity = LocalContext.current as? Activity
        LaunchedEffect(Unit) {
            delay(2200)
            activity?.finishAffinity()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { activity?.finishAffinity() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "It is now safe to turn off\nyour RetroVibe OS phone.",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFFFF8800),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "(Closing RetroVibe OS...)",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFAA5500)
                )
            }
        }
        return
    }

    // Optional Win98 Login Screen
    if (!loggedIn) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.wallpaper),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(290.dp)
                    .win98Bevel(sunken = false, bg = theme.windowBg)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(theme.titleBarActive, theme.titleBarAccent)))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text("Welcome to RetroVibe OS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon("winFlag", 32.dp)
                    Text("Type a user name to log on to RetroVibe OS.", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = theme.textPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("User:", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textPrimary)
                    Box(modifier = Modifier.weight(1f).win98Bevel(sunken = true, bg = Color.White).padding(5.dp)) {
                        BasicTextField(
                            value = loginNameInput,
                            onValueChange = { loginNameInput = it },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    RetroButton(label = "OK", palette = theme, active = true, onClick = {
                        vm.username = loginNameInput.ifBlank { "RetroUser" }
                        vm.persist()
                        loggedIn = true
                        RetroSoundEngine.play(RetroSound.STARTUP, vm.soundEnabled)
                    })
                }
            }
        }
        return
    }

    val pendingTodos = vm.todos.count { todayIso() !in it.completedDates }
    val pendingReminders = vm.reminders.count { !it.completed }

    val baseShortcuts = listOf(
        DesktopShortcut("icon-explorer", "My Computer", "computer", "explorer"),
        DesktopShortcut("icon-gallery", "My Pictures", "gallery", "gallery"),
        DesktopShortcut("icon-camera", "Camera", "camera", "camera"),
        DesktopShortcut("icon-phone", "Phone", "phone", "phone"),
        DesktopShortcut("icon-clock", "Clock", "clock", "clock"),
        DesktopShortcut("icon-reader", "E-Reader", "book", "reader"),
        DesktopShortcut("icon-control", "Control Panel", "control", "control"),
        DesktopShortcut("icon-music", "Music", "music", "music"),
        DesktopShortcut("icon-calendar", "Calendar", "calendar", "calendar"),
        DesktopShortcut("icon-reminders", "Reminders", "reminder", "reminders", badgeCount = pendingReminders),
        DesktopShortcut("icon-habits", "To do list", "habit", "habits", badgeCount = pendingTodos),
        DesktopShortcut("icon-paint", "MS Paint", "paint", "paint"),
        DesktopShortcut("icon-browser", "Internet Voyager", "ie", "browser"),
        DesktopShortcut("icon-mail", "E-mail", "mail", "mail"),
        DesktopShortcut("icon-recycle", if (vm.trash.isEmpty()) "Recycle Bin" else "Recycle Bin", "recycle", "recycle", badgeCount = vm.trash.size),
        DesktopShortcut("icon-sweeper", "HeartSweeper", "sweeper", "sweeper"),
        DesktopShortcut("icon-calc", "Calculator", "calc", "calc"),
        DesktopShortcut("icon-recorder", "Sound Recorder", "recorder", "recorder"),
        DesktopShortcut("icon-taskmgr", "Task Manager", "taskmgr", "taskmgr"),
        DesktopShortcut("icon-settings", "Display", "settings", "settings"),
        DesktopShortcut("icon-status", "Updates", "status", "status")
    ).filter { it.id !in vm.hiddenDesktopIcons }

    val pinnedNoteShortcuts = vm.notes.filter { it.pinnedToDesktop }.map { n ->
        DesktopShortcut("icon-note-${n.id}", n.title, "note", noteId = n.id)
    }
    val allShortcuts = baseShortcuts + pinnedNoteShortcuts

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.wallpaper)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.changes.any { it.pressed }) idleSeconds = 0
                    }
                }
            }
    ) {
        // Optional custom wallpaper
        if (vm.customWallpaperUri != null) {
            AsyncImage(
                model = vm.customWallpaperUri,
                contentDescription = "Wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .imePadding()
        ) {
            // Desktop Workspace Area
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                focusManager.clearFocus()
                                startMenuOpen = false
                                activeSubmenu = null
                                selectedIconId = null
                                contextMenuIconId = null
                                tabMenuWindowId = null
                                showDesktopMenu = false
                            },
                            onLongPress = {
                                startMenuOpen = false
                                contextMenuIconId = null
                                tabMenuWindowId = null
                                showDesktopMenu = true
                            }
                        )
                    }
            ) {
                val boundsW = maxWidth.value
                val boundsH = maxHeight.value
                // Keep icon grid height stable when soft keyboard opens so icons never jump around!
                var stableBoundsH by remember { mutableFloatStateOf(boundsH) }
                if (boundsH > stableBoundsH) stableBoundsH = boundsH
                // Tight, symmetrical left & right margins (~6dp) with columns evenly distributed across screen
                val sideMargin = 6f
                val maxCols = ((boundsW - sideMargin * 2f) / 76f).toInt().coerceAtLeast(1)
                val cellW = (boundsW - sideMargin * 2f) / maxCols
                val cellH = 82f
                val maxRows = ((stableBoundsH - 16f) / cellH).toInt().coerceAtLeast(1)
                val colPadX = ((cellW - 74f) / 2f).coerceAtLeast(0f)

                // Compute zero-overlap snap-to-grid positions
                val occupied = mutableSetOf<Pair<Int, Int>>()
                val resolvedPositions = mutableMapOf<String, Pair<Float, Float>>()

                fun nearestFreeSlot(prefCol: Int, prefRow: Int): Pair<Int, Int> {
                    var best: Pair<Int, Int>? = null
                    var bestDist = Float.MAX_VALUE
                    for (c in 0 until maxCols) {
                        for (r in 0 until maxRows) {
                            if ((c to r) !in occupied) {
                                val d = hypot((c - prefCol).toFloat(), (r - prefRow).toFloat())
                                if (d < bestDist) {
                                    bestDist = d
                                    best = c to r
                                }
                            }
                        }
                    }
                    return best ?: (0 to 0)
                }

                allShortcuts.forEachIndexed { idx, sc ->
                    val saved = vm.iconPositions[sc.id]
                    val (col, row) = if (saved != null) {
                        val c = ((saved.x - sideMargin) / cellW).roundToInt().coerceIn(0, maxCols - 1)
                        val r = ((saved.y - 8f) / cellH).roundToInt().coerceIn(0, maxRows - 1)
                        if ((c to r) !in occupied) c to r else nearestFreeSlot(c, r)
                    } else {
                        val defC = idx / maxRows
                        val defR = idx % maxRows
                        if ((defC to defR) !in occupied && defC < maxCols) defC to defR else nearestFreeSlot(defC, defR)
                    }
                    occupied.add(col to row)
                    resolvedPositions[sc.id] = (sideMargin + col * cellW + colPadX) to (8f + row * cellH)
                }

                val recyclePos = resolvedPositions["icon-recycle"]

                // Desktop Icons
                allShortcuts.forEach { sc ->
                    val (ix, iy) = resolvedPositions[sc.id] ?: (6f to 8f)
                    var dragX by remember(sc.id, ix) { mutableFloatStateOf(ix) }
                    var dragY by remember(sc.id, iy) { mutableFloatStateOf(iy) }
                    var isDragging by remember(sc.id) { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(with(density) { dragX.dp.roundToPx() }, with(density) { dragY.dp.roundToPx() }) }
                            .width(74.dp)
                            .zIndex(if (isDragging) 50f else 1f)
                            .pointerInput(sc.id) {
                                detectTapGestures(
                                    onTap = {
                                        startMenuOpen = false
                                        contextMenuIconId = null
                                        showDesktopMenu = false
                                        selectedIconId = sc.id
                                        if (sc.windowId != null) {
                                            vm.openWindow(sc.windowId)
                                        } else if (sc.noteId != null) {
                                            val n = vm.notes.find { it.id == sc.noteId }
                                            if (n != null) vm.openWindow("note:${n.id}", n.title)
                                        }
                                    },
                                    onLongPress = {
                                        RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                                        selectedIconId = sc.id
                                        contextMenuIconId = sc.id
                                        showDesktopMenu = false
                                    }
                                )
                            }
                            .pointerInput(sc.id, boundsW, boundsH) {
                                detectDragGestures(
                                    onDragStart = {
                                        isDragging = true
                                        selectedIconId = sc.id
                                        contextMenuIconId = null
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        // Check drop onto Recycle Bin
                                        if (sc.id != "icon-recycle" && recyclePos != null) {
                                            val distToBin = hypot(dragX - recyclePos.first, dragY - recyclePos.second)
                                            if (distToBin < 46f) {
                                                if (sc.noteId != null) {
                                                    vm.deleteNote(sc.noteId)
                                                    showToast("Moved note to Recycle Bin")
                                                } else {
                                                    vm.hideDesktopIcon(sc.id)
                                                    showToast("Removed ${sc.label} shortcut")
                                                }
                                                return@detectDragGestures
                                            }
                                        }
                                        val snapC = ((dragX - sideMargin) / cellW).roundToInt().coerceIn(0, maxCols - 1)
                                        val snapR = ((dragY - 8f) / cellH).roundToInt().coerceIn(0, maxRows - 1)
                                        val nx = sideMargin + snapC * cellW + colPadX
                                        val ny = 8f + snapR * cellH
                                        dragX = nx
                                        dragY = ny
                                        vm.setIconPos(sc.id, nx, ny)
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        dragX = ix; dragY = iy
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    dragX = (dragX + dragAmount.x / density.density).coerceIn(0f, boundsW - 74f)
                                    dragY = (dragY + dragAmount.y / density.density).coerceIn(0f, boundsH - 76f)
                                }
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(if (selectedIconId == sc.id) Color(0x44000080) else Color.Transparent)
                                .padding(vertical = 3.dp, horizontal = 2.dp)
                        ) {
                            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                PixelIcon(sc.icon, 36.dp)
                                if (sc.badgeCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 3.dp, y = (-2).dp)
                                            .background(Color(0xFFE67FA2))
                                            .border(1.5.dp, Color.Black)
                                            .padding(horizontal = 4.dp, vertical = 0.5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (sc.badgeCount > 99) "99+" else sc.badgeCount.toString(),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = sc.label,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .background(if (selectedIconId == sc.id) theme.titleBarActive else Color(0x55000000))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // Interactive Desk Cat (OFF by default, sits on taskbar with gravity)
                DeskCat(vm = vm, boundsW = boundsW, boundsH = boundsH)

                // Open Application Windows (stable key + ID order so zIndex changes never glitch drag gestures!)
                vm.windows.values.filter { it.isOpen && !it.isMinimized }.sortedBy { it.id }.forEach { win ->
                    key(win.id) {
                    val iconName = when {
                        win.id.startsWith("note:") -> "note"
                        win.id == "explorer" -> "computer"
                        win.id == "gallery" -> "gallery"
                        win.id == "camera" -> "camera"
                        win.id == "music" -> "music"
                        win.id == "calendar" -> "calendar"
                        win.id == "reminders" -> "reminder"
                        win.id == "habits" -> "habit"
                        win.id == "paint" -> "paint"
                        win.id == "recorder" -> "recorder"
                        win.id == "recycle" -> "recycle"
                        win.id == "settings" -> "settings"
                        win.id == "status" -> "status"
                        win.id == "sweeper" -> "sweeper"
                        win.id == "browser" -> "ie"
                        win.id == "qrscanner" -> "qr"
                        win.id == "mail" -> "mail"
                        win.id == "calc" -> "calc"
                        win.id == "terminal" -> "terminal"
                        win.id == "run" -> "run"
                        win.id == "taskmgr" -> "taskmgr"
                        win.id == "phone" -> "phone"
                        win.id == "clock" -> "clock"
                        win.id == "control" -> "control"
                        win.id == "weather" -> "weather"
                        win.id == "reader" -> "book"
                        win.id == "phoneapps" -> "phoneapps"
                        else -> "note"
                    }
                    RetroWindow(
                        vm = vm,
                        win = win,
                        boundsWidthDp = boundsW,
                        boundsHeightDp = boundsH,
                        icon = iconName,
                        accentTitleBar = win.id == "sweeper"
                    ) {
                        when {
                            win.id.startsWith("note:") -> {
                                val noteId = win.id.removePrefix("note:")
                                val note = vm.notes.find { it.id == noteId }
                                if (note != null) NoteWindowContent(vm, note)
                            }
                            win.id == "explorer" -> FileExplorerContent(vm)
                            win.id == "gallery" -> GalleryWindow(vm, theme)
                            win.id == "camera" -> CameraWindow(vm, theme)
                            win.id == "music" -> MusicPlayerWindow(vm, theme)
                            win.id == "calendar" -> CalendarContent(vm)
                            win.id == "reminders" -> RemindersContent(vm)
                            win.id == "habits" -> TodoListContent(vm)
                            win.id == "paint" -> MsPaintWindow(vm, theme)
                            win.id == "recorder" -> SoundRecorderWindow(vm, theme)
                            win.id == "recycle" -> RecycleBinContent(vm)
                            win.id == "settings" -> SettingsWindow(vm, theme)
                            win.id == "status" -> SystemUpdatesWindow(vm, theme)
                            win.id == "sweeper" -> HeartSweeperWindow(vm, theme)
                            win.id == "browser" -> BrowserWindow(vm, theme)
                            win.id == "qrscanner" -> QrScannerWindow(vm, theme)
                            win.id == "mail" -> EmailWindow(vm, theme)
                            win.id == "calc" -> CalculatorWindow(vm, theme)
                            win.id == "terminal" -> TerminalWindow(vm, theme)
                            win.id == "run" -> RunDialogWindow(vm, theme)
                            win.id == "taskmgr" -> TaskManagerWindow(vm, theme)
                            win.id == "phone" -> PhoneWindow(vm, theme)
                            win.id == "clock" -> ClockWindow(vm, theme)
                            win.id == "control" -> ControlPanelWindow(vm, theme)
                            win.id == "weather" -> WeatherWindow(vm, theme)
                            win.id == "reader" -> BookReaderWindow(vm, theme)
                            win.id == "phoneapps" -> PhoneAppsWindow(vm, theme)
                        }
                    }
                    }
                }

                // Reminder Popups
                val activePopup = vm.reminders.firstOrNull { it.showPopup }
                if (activePopup != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .zIndex(950f)
                            .width(260.dp)
                            .win98Bevel(sunken = false, bg = theme.windowBg)
                            .padding(8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(theme.titleBarActive)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text("Reminder!", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                            }
                            Text(activePopup.title, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = theme.textPrimary)
                            if (activePopup.message.isNotBlank()) {
                                Text(activePopup.message, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = theme.textSecondary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                RetroButton(label = "OK", palette = theme, active = true, onClick = { vm.dismissReminderPopup(activePopup.id) })
                            }
                        }
                    }
                }

                // Opt-in "You've Got Mail!" Popup
                val mailAlert = vm.mailPopupAlert
                if (mailAlert != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .zIndex(960f)
                            .width(240.dp)
                            .win98Bevel(sunken = false, bg = theme.windowBg)
                            .padding(8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelIcon("mail", 18.dp)
                                Text("You've Got Mail!", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = theme.accentPink)
                            }
                            Text(mailAlert.subject, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = theme.textPrimary)
                            Text("From: ${mailAlert.sender}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = theme.textSecondary)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                RetroButton(label = "Open Mail", palette = theme, onClick = {
                                    vm.dismissMailPopup()
                                    vm.openWindow("mail")
                                })
                                Spacer(Modifier.width(4.dp))
                                RetroButton(label = "Dismiss", palette = theme, onClick = { vm.dismissMailPopup() })
                            }
                        }
                    }
                }

                // Icon Long-Press Context Menu
                if (contextMenuIconId != null) {
                    val target = allShortcuts.find { it.id == contextMenuIconId }
                    if (target != null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .zIndex(970f)
                                .width(170.dp)
                                .win98Bevel(sunken = false, bg = theme.windowBg)
                                .padding(4.dp)
                        ) {
                            Text(target.label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = theme.textSecondary, modifier = Modifier.padding(4.dp))
                            StartRow("Open", target.icon, theme) {
                                contextMenuIconId = null
                                if (target.windowId != null) vm.openWindow(target.windowId)
                                else if (target.noteId != null) vm.openWindow("note:${target.noteId}", target.label)
                            }
                            if (target.id != "icon-recycle") {
                                StartRow(if (target.noteId != null) "Delete Note" else "Hide from Desktop", "recycle", theme) {
                                    contextMenuIconId = null
                                    if (target.noteId != null) vm.deleteNote(target.noteId) else vm.hideDesktopIcon(target.id)
                                }
                            }
                            StartRow("Auto-Arrange Icons", "settings", theme) {
                                contextMenuIconId = null
                                vm.resetIconPositions()
                            }
                        }
                    }
                }

                // Empty Desktop Long-Press Menu
                if (showDesktopMenu) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .zIndex(970f)
                            .width(180.dp)
                            .win98Bevel(sunken = false, bg = theme.windowBg)
                            .padding(4.dp)
                    ) {
                        StartRow("New Desktop Note", "note", theme) {
                            showDesktopMenu = false
                            vm.createNote(pinned = true)
                        }
                        if (vm.clipboard != null) {
                            StartRow("Paste to Desktop", "note", theme) {
                                showDesktopMenu = false
                                vm.pasteFromClipboard("root-notes", pinOnDesktop = true)
                            }
                        }
                        StartRow("Auto-Arrange Icons", "computer", theme) {
                            showDesktopMenu = false
                            vm.resetIconPositions()
                        }
                        StartRow("Display Properties", "settings", theme) {
                            showDesktopMenu = false
                            vm.openWindow("settings")
                        }
                    }
                }

                // Start Menu with Cascading Submenus Aligned Next to Active Row
                if (startMenuOpen) {
                    val programsList = listOf(
                        StartMenuEntry("MS Paint", "paint", "icon-paint") { vm.openWindow("paint") },
                        StartMenuEntry("Calculator", "calc", "icon-calc") { vm.openWindow("calc") },
                        StartMenuEntry("Internet Voyager", "ie", "icon-browser") { vm.openWindow("browser") },
                        StartMenuEntry("QR Scanner", "qr") { vm.openWindow("qrscanner") },
                        StartMenuEntry("E-mail", "mail", "icon-mail") { vm.openWindow("mail") },
                        StartMenuEntry("Music Player", "music", "icon-music") { vm.openWindow("music") },
                        StartMenuEntry("Sound Recorder", "recorder", "icon-recorder") { vm.openWindow("recorder") },
                        StartMenuEntry("Camera", "camera", "icon-camera") { vm.openWindow("camera") },
                        StartMenuEntry("Phone", "phone", "icon-phone") { vm.openWindow("phone") },
                        StartMenuEntry("Clock & Timer", "clock", "icon-clock") { vm.openWindow("clock") },
                        StartMenuEntry("E-Reader", "book", "icon-reader") { vm.openWindow("reader") },
                        StartMenuEntry("Phone Apps", "phoneapps") { vm.openWindow("phoneapps") },
                        StartMenuEntry("Calendar", "calendar", "icon-calendar") { vm.openWindow("calendar") },
                        StartMenuEntry("Reminders", "reminder", "icon-reminders") { vm.openWindow("reminders") },
                        StartMenuEntry("To do list", "habit", "icon-habits") { vm.openWindow("habits") }
                    )
                    val docsList = listOf(
                        StartMenuEntry("New Note", "note") { vm.createNote(pinned = true) },
                        StartMenuEntry("My Computer", "computer", "icon-explorer") { vm.openWindow("explorer") },
                        StartMenuEntry("My Pictures", "gallery", "icon-gallery") { vm.openWindow("gallery") }
                    )
                    val gamesList = listOf(
                        StartMenuEntry("HeartSweeper", "sweeper", "icon-sweeper") { vm.openWindow("sweeper") }
                    )
                    val settingsList = listOf(
                        StartMenuEntry("Control Panel", "control", "icon-control") { vm.openWindow("control") },
                        StartMenuEntry("Display", "settings", "icon-settings") { vm.openWindow("settings") },
                        StartMenuEntry("Updates", "status", "icon-status") { vm.openWindow("status") },
                        StartMenuEntry("Weather", "weather") { vm.openWindow("weather") },
                        StartMenuEntry(if (vm.deskCatEnabled) "Hide Desk Cat" else "Summon Desk Cat", "cat") {
                            vm.deskCatEnabled = !vm.deskCatEnabled
                            vm.persist()
                        }
                    )

                    val activeEntries = when (activeSubmenu) {
                        "programs" -> programsList
                        "documents" -> docsList
                        "games" -> gamesList
                        "settings" -> settingsList
                        else -> null
                    }
                    // Align each cascading submenu right beside its own Start Menu row!
                    val submenuBottomOffset = when (activeSubmenu) {
                        "programs" -> 0.dp
                        "documents" -> 114.dp
                        "games" -> 142.dp
                        "settings" -> 54.dp
                        else -> 0.dp
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .zIndex(980f)
                    ) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .width(194.dp)
                                .win98Bevel(sunken = false, bg = theme.windowBg)
                                .padding(2.dp)
                        ) {
                            // Classic vertical RetroVibe OS banner
                            Box(
                                modifier = Modifier
                                    .width(26.dp)
                                    .height(232.dp)
                                    .background(Brush.verticalGradient(listOf(theme.titleBarActive, theme.titleBarAccent))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "RetroVibe OS",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier
                                        .rotate(-90f)
                                        .requiredWidth(206.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                StartRow("Programs  >", "folder", theme, activeSubmenu == "programs") {
                                    activeSubmenu = if (activeSubmenu == "programs") null else "programs"
                                }
                                StartRow("Documents >", "note", theme, activeSubmenu == "documents") {
                                    activeSubmenu = if (activeSubmenu == "documents") null else "documents"
                                }
                                StartRow("Games     >", "sweeper", theme, activeSubmenu == "games") {
                                    activeSubmenu = if (activeSubmenu == "games") null else "games"
                                }
                                StartRow("Settings  >", "settings", theme, activeSubmenu == "settings") {
                                    activeSubmenu = if (activeSubmenu == "settings") null else "settings"
                                }
                                Box(Modifier.fillMaxWidth().height(1.dp).background(theme.borderDark).padding(vertical = 2.dp))
                                StartRow("Run...", "run", theme) {
                                    startMenuOpen = false; activeSubmenu = null
                                    vm.openWindow("run")
                                }
                                StartRow("Task Manager", "taskmgr", theme) {
                                    startMenuOpen = false; activeSubmenu = null
                                    vm.openWindow("taskmgr")
                                }
                                Box(Modifier.fillMaxWidth().height(1.dp).background(theme.borderDark).padding(vertical = 2.dp))
                                StartRow("Shut Down...", "shutdown", theme) {
                                    startMenuOpen = false; activeSubmenu = null
                                    showShutdownDialog = true
                                }
                            }
                        }

                        // Cascading submenu aligned beside the active row
                        if (activeEntries != null) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .offset(x = 192.dp, y = -submenuBottomOffset)
                                    .width(168.dp)
                                    .win98Bevel(sunken = false, bg = theme.windowBg)
                                    .padding(3.dp)
                            ) {
                                activeEntries.forEach { entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                startMenuOpen = false
                                                activeSubmenu = null
                                                entry.action()
                                            }
                                            .padding(horizontal = 5.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        PixelIcon(entry.icon, 16.dp)
                                        Text(
                                            text = entry.label,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (entry.desktopIconId != null && entry.desktopIconId in vm.hiddenDesktopIcons) {
                                            Text(
                                                text = "[+]",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = theme.accentPink,
                                                modifier = Modifier.clickable {
                                                    vm.showDesktopIcon(entry.desktopIconId)
                                                    showToast("Pinned ${entry.label} to Desktop!")
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Taskbar Tab Long-Press Context Menu
                if (tabMenuWindowId != null) {
                    val targetWin = vm.windows[tabMenuWindowId]
                    if (targetWin != null && targetWin.isOpen) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 64.dp, bottom = 4.dp)
                                .zIndex(985f)
                                .width(176.dp)
                                .win98Bevel(sunken = false, bg = theme.windowBg)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = targetWin.title,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = theme.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                            Box(Modifier.fillMaxWidth().height(1.dp).background(theme.borderDark))
                            StartRow(if (targetWin.isMinimized) "Restore" else "Minimize", "computer", theme) {
                                tabMenuWindowId = null
                                if (targetWin.isMinimized) vm.focusWindow(targetWin.id) else vm.minimizeWindow(targetWin.id)
                            }
                            StartRow(if (targetWin.isMaximized) "Unmaximize" else "Maximize", "settings", theme) {
                                tabMenuWindowId = null
                                vm.focusWindow(targetWin.id)
                                vm.toggleMaximizeWindow(targetWin.id)
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(theme.borderDark))
                            StartRow("Close", "shutdown", theme) {
                                tabMenuWindowId = null
                                vm.closeWindow(targetWin.id)
                            }
                        }
                    } else {
                        tabMenuWindowId = null
                    }
                }

                // Toast banner
                if (toastMsg != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .zIndex(990f)
                            .win98Bevel(sunken = false, bg = theme.windowBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(toastMsg!!, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                    }
                }

                // Shut Down Dialog
                if (showShutdownDialog) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(995f)
                            .background(Color(0x66000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .width(270.dp)
                                .win98Bevel(sunken = false, bg = theme.windowBg)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(theme.titleBarActive)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("Shut Down RetroVibe OS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                            }
                            Text("What do you want RetroVibe OS to do?", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = theme.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                RetroButton(label = "Shut Down", palette = theme, active = true, onClick = {
                                    RetroSoundEngine.play(RetroSound.SHUTDOWN, vm.soundEnabled)
                                    showShutdownDialog = false
                                    poweredOff = true
                                })
                                RetroButton(label = "Restart", palette = theme, onClick = {
                                    RetroSoundEngine.play(RetroSound.SHUTDOWN, vm.soundEnabled)
                                    showShutdownDialog = false
                                    booted = false
                                })
                                RetroButton(label = "Cancel", palette = theme, onClick = { showShutdownDialog = false })
                            }
                        }
                    }
                }
            }

            // Win98 Bottom Taskbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(theme.taskbarBg)
                    .win98Bevel(true, theme.borderLight, theme.borderDark, theme.borderDeep, 2.dp)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Start Button
                RetroButton(
                    theme = theme,
                    soundEnabled = vm.soundEnabled,
                    active = startMenuOpen,
                    onClick = {
                        startMenuOpen = !startMenuOpen
                        activeSubmenu = null
                    }
                ) {
                    PixelIcon("winFlag", 15.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Start", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 11.sp, color = theme.textPrimary)
                }

                // Active Window Tabs (tap to focus/minimize, hold to close)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    vm.windows.values.filter { it.isOpen }.forEach { win ->
                        val active = vm.activeWindowId == win.id && !win.isMinimized
                        val tabView = LocalView.current
                        var tabPressed by remember { mutableStateOf(false) }
                        val tabDown = active || tabPressed
                        val tabBg = if (tabPressed) {
                            theme.titleBarActive.copy(alpha = 0.28f).compositeOver(if (active) theme.accentSoft else theme.windowBg)
                        } else if (active) {
                            theme.accentSoft
                        } else {
                            theme.windowBg
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .background(tabBg)
                                .win98Bevel(!tabDown, theme.borderLight, theme.borderDark, theme.borderDeep)
                                .pointerInput(win.id, active) {
                                    detectTapGestures(
                                        onPress = {
                                            tabPressed = true
                                            tryAwaitRelease()
                                            tabPressed = false
                                        },
                                        onTap = {
                                            tabMenuWindowId = null
                                            tabView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                                            if (active) vm.minimizeWindow(win.id) else vm.focusWindow(win.id)
                                        },
                                        onLongPress = {
                                            tabView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                                            startMenuOpen = false
                                            tabMenuWindowId = win.id
                                        }
                                    )
                                }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .offset(x = if (tabPressed) 1.dp else 0.dp, y = if (tabPressed) 1.dp else 0.dp)
                        ) {
                            Text(
                                text = win.title.take(14),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        }
                    }
                }

                // Gentle background weather refresh for the taskbar pill
                LaunchedEffect(vm.weatherLat, vm.weatherLon) {
                    val snap = fetchWeatherSnapshot(vm.weatherLat, vm.weatherLon)
                    if (snap != null) {
                        vm.weatherTemp = snap.first.first
                        vm.weatherCode = snap.first.second
                    }
                }

                // System Tray
                Row(
                    modifier = Modifier
                        .win98Bevel(sunken = true, bg = theme.windowBg, strokeDp = 1.5.dp)
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(Modifier.clickable {
                        vm.soundEnabled = !vm.soundEnabled
                        vm.persist()
                        showToast(if (vm.soundEnabled) "Sound: ON" else "Sound: Muted")
                    }) {
                        PixelIcon(if (vm.soundEnabled) "speaker" else "speakerMute", 14.dp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.clickable {
                            RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                            vm.openWindow("weather")
                        }
                    ) {
                        PixelIcon("weather", 14.dp)
                        if (vm.weatherTemp != null) {
                            Text(
                                text = "${vm.weatherTemp}°",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = theme.textPrimary
                            )
                        }
                    }
                    Text(
                        text = clockStr,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = theme.textPrimary,
                        modifier = Modifier.clickable { vm.openWindow("clock") }
                    )
                }
            }
        }

        // Optional CRT Scanlines Overlay
        if (vm.crtScanlines) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var y = 0f
                while (y < size.height) {
                    drawLine(Color(0x1C000000), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                    y += 4f
                }
            }
        }

        // Fullscreen Screensaver Overlay
        if (vm.screensaverActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2000f)
                    .clickable {
                        vm.screensaverActive = false
                        idleSeconds = 0
                    }
            ) {
                ScreensaverCanvas(vm.screensaverId, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun StartRow(
    label: String,
    icon: String,
    theme: com.retrovibe.os.theme.RetroThemeColors,
    selected: Boolean = false,
    onClick: () -> Unit
) {
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
    val highlighted = selected || pressed || clickFlash
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) theme.titleBarActive else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null) {
                clickSeq++
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        PixelIcon(icon, 16.dp)
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = if (highlighted) Color.White else theme.textPrimary,
            maxLines = 1
        )
    }
}
