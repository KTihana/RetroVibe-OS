package com.retrovibe.os.ui.windows

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.model.ScreensaverId
import com.retrovibe.os.model.ThemeId
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.theme.paletteFor
import com.retrovibe.os.ui.RetroButton
import com.retrovibe.os.ui.ScreensaverCanvas
import com.retrovibe.os.ui.win98Bevel
import com.retrovibe.os.theme.textDark
import com.retrovibe.os.theme.accent
import com.retrovibe.os.theme.desktopBg
import com.retrovibe.os.theme.titleBarStart

private val DESKTOP_ICON_CATALOG = listOf(
    "icon-explorer" to "My Computer",
    "icon-gallery" to "My Pictures",
    "icon-camera" to "Camera",
    "icon-music" to "Music",
    "icon-calendar" to "Calendar",
    "icon-reminders" to "Reminders",
    "icon-habits" to "To do list",
    "icon-paint" to "MS Paint",
    "icon-browser" to "Internet Voyager",
    "icon-mail" to "E-mail",
    "icon-recycle" to "Recycle Bin",
    "icon-sweeper" to "HeartSweeper",
    "icon-calc" to "Calculator",
    "icon-recorder" to "Sound Recorder",
    "icon-taskmgr" to "Task Manager",
    "icon-settings" to "Display",
    "icon-status" to "Updates"
)

@Composable
fun SettingsWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0=Screen Saver, 1=Background, 2=Desktop Icons

    val wallPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Throwable) {
            }
            vm.customWallpaperUri = uri.toString()
            vm.persist()
            RetroSoundEngine.play(RetroSound.SUCCESS, vm.soundEnabled)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(8.dp)
    ) {
        // Tabs
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            listOf("Screen Saver", "Background", "Desktop Icons").forEachIndexed { idx, lbl ->
                RetroButton(label = lbl, palette = palette, active = activeTab == idx, onClick = {
                    RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                    activeTab = idx
                })
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (activeTab) {
                0 -> {
                    // Win98 CRT Monitor with live screensaver preview!
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(204.dp)
                                .height(142.dp)
                                .win98Bevel(sunken = false, bg = Color(0xFFDCD6C8))
                                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .win98Bevel(sunken = true, bg = Color.Black)
                                    .padding(2.dp)
                                    .clipToBounds()
                            ) {
                                if (vm.screensaverId == ScreensaverId.NONE) {
                                    Box(Modifier.fillMaxSize().background(palette.desktopBg))
                                } else {
                                    ScreensaverCanvas(vm.screensaverId, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                        Box(modifier = Modifier.width(64.dp).height(10.dp).background(Color(0xFF9A9A9A)))
                        Box(modifier = Modifier.width(120.dp).height(8.dp).win98Bevel(sunken = false, bg = Color(0xFFD4D0C8)))
                    }

                    Text("Screen Saver:", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .win98Bevel(sunken = true, bg = Color.White)
                            .padding(3.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ScreensaverId.entries.forEach { ss ->
                            val selected = vm.screensaverId == ss
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (selected) palette.accent else Color.Transparent)
                                    .clickable {
                                        RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                                        vm.screensaverId = ss
                                        vm.persist()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = ss.label,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = if (selected) Color.White else Color.Black
                                )
                                Text(
                                    text = ss.desc.take(22),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = if (selected) Color(0xFFFFE5F0) else Color(0xFF666666)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RetroButton(
                            label = "Preview Fullscreen",
                            palette = palette,
                            active = true,
                            onClick = {
                                if (vm.screensaverId != ScreensaverId.NONE) vm.screensaverActive = true
                            }
                        )
                        RetroButton(
                            label = "Wait: ${if (vm.screensaverWaitSec == 0) "Off" else "${vm.screensaverWaitSec}s"}",
                            palette = palette,
                            onClick = {
                                vm.screensaverWaitSec = when (vm.screensaverWaitSec) {
                                    0 -> 30
                                    30 -> 60
                                    60 -> 180
                                    else -> 0
                                }
                                vm.persist()
                            }
                        )
                    }
                }
                1 -> {
                    Text("Color Scheme:", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    ThemeId.entries.forEach { tid ->
                        val p = paletteFor(tid)
                        val sel = vm.themeId == tid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .win98Bevel(sunken = sel, bg = if (sel) palette.accent else palette.windowBg)
                                .clickable {
                                    RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                                    vm.themeId = tid
                                    vm.persist()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(Modifier.size(16.dp).background(p.desktopBg).border(1.dp, Color.Black))
                            Box(Modifier.size(16.dp).background(p.titleBarStart).border(1.dp, Color.Black))
                            Text(
                                text = p.name,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (sel) Color.White else palette.textDark
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RetroButton(label = "Pick Wallpaper...", palette = palette, onClick = {
                            wallPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        if (vm.customWallpaperUri != null) {
                            RetroButton(label = "Clear Wallpaper", palette = palette, onClick = {
                                vm.customWallpaperUri = null
                                vm.persist()
                            })
                        }
                    }

                    // Retro toggles
                    RetroButton(
                        label = if (vm.crtScanlines) "[x] CRT Scanlines" else "[ ] CRT Scanlines",
                        palette = palette,
                        onClick = { vm.crtScanlines = !vm.crtScanlines; vm.persist() }
                    )
                    RetroButton(
                        label = if (vm.soundEnabled) "[x] Retro 8-bit Sounds" else "[ ] Retro 8-bit Sounds",
                        palette = palette,
                        onClick = { vm.soundEnabled = !vm.soundEnabled; vm.persist() }
                    )
                    RetroButton(
                        label = if (vm.deskCatEnabled) "[x] Desk Cat Pet" else "[ ] Desk Cat Pet",
                        palette = palette,
                        onClick = {
                            vm.deskCatEnabled = !vm.deskCatEnabled
                            if (vm.deskCatEnabled) RetroSoundEngine.play(RetroSound.PURR, vm.soundEnabled)
                            vm.persist()
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RetroButton(
                            label = if (vm.deskCatVariant == "mindi") "[*] Mindi (Ginger)" else "[ ] Mindi (Ginger)",
                            palette = palette,
                            active = vm.deskCatVariant == "mindi",
                            onClick = {
                                vm.deskCatVariant = "mindi"
                                vm.deskCatEnabled = true
                                RetroSoundEngine.play(RetroSound.PURR, vm.soundEnabled)
                                vm.persist()
                            }
                        )
                        RetroButton(
                            label = if (vm.deskCatVariant == "spooks") "[*] Spooks (Black)" else "[ ] Spooks (Black)",
                            palette = palette,
                            active = vm.deskCatVariant == "spooks",
                            onClick = {
                                vm.deskCatVariant = "spooks"
                                vm.deskCatEnabled = true
                                RetroSoundEngine.play(RetroSound.PURR, vm.soundEnabled)
                                vm.persist()
                            }
                        )
                    }
                    RetroButton(
                        label = if (vm.loginPromptEnabled) "[x] Win98 Login Screen on Boot" else "[ ] Win98 Login Screen on Boot",
                        palette = palette,
                        onClick = { vm.loginPromptEnabled = !vm.loginPromptEnabled; vm.persist() }
                    )
                }
                else -> {
                    Text(
                        text = "Toggle which shortcuts appear on your Desktop:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = palette.textDark
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .win98Bevel(sunken = true, bg = Color.White)
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        DESKTOP_ICON_CATALOG.forEach { (id, label) ->
                            val visible = id !in vm.hiddenDesktopIcons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.toggleDesktopIcon(id) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (visible) "[x]" else "[ ]",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (visible) palette.accent else Color.Gray
                                )
                                Text(
                                    text = label,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                    RetroButton(label = "Reset Icon Grid Positions", palette = palette, onClick = { vm.resetIconPositions() })
                }
            }
        }
    }
}
