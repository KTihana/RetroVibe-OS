package com.retrovibe.os.ui.windows

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton

@Composable
fun ControlPanelWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var statusText by remember { mutableStateOf("Quick Phone & RetroVibe Controls") }

    fun toggleFlashlight() {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val backId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                    chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (backId != null) {
                val next = !vm.torchOn
                cm.setTorchMode(backId, next)
                vm.torchOn = next
                statusText = if (next) "Flashlight ON" else "Flashlight OFF"
            } else {
                statusText = "No flashlight found on device"
            }
        } catch (t: Throwable) {
            statusText = "Flashlight unavailable: ${t.message}"
        }
    }

    fun openAndroidSettings(action: String, label: String) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            statusText = "Opened $label"
        } catch (_: Throwable) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Throwable) {}
        }
    }

    fun adjustVolume(direction: Int) {
        try {
            audioManager?.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            )
            statusText = if (direction > 0) "Volume Up" else "Volume Down"
        } catch (_: Throwable) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .verticalScroll(rememberScrollState())
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Quick Hardware Toggles
        BevelBox(theme = palette, raised = true, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Quick Controls",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RetroButton(
                        label = if (vm.torchOn) "Flashlight: ON" else "Flashlight: OFF",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        active = vm.torchOn,
                        modifier = Modifier.weight(1f),
                        onClick = { toggleFlashlight() }
                    )
                    RetroButton(
                        label = if (vm.soundEnabled) "8-bit SFX: ON" else "8-bit SFX: OFF",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        active = vm.soundEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            vm.soundEnabled = !vm.soundEnabled
                            vm.persist()
                        }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RetroButton(
                        label = "Vol -",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = { adjustVolume(AudioManager.ADJUST_LOWER) }
                    )
                    RetroButton(
                        label = "Vol +",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = { adjustVolume(AudioManager.ADJUST_RAISE) }
                    )
                    RetroButton(
                        label = if (vm.crtScanlines) "CRT: ON" else "CRT: OFF",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        active = vm.crtScanlines,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            vm.crtScanlines = !vm.crtScanlines
                            vm.persist()
                        }
                    )
                }
            }
        }

        // Phone Connectivity & Quiet Shortcuts
        BevelBox(theme = palette, raised = true, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "Phone Settings",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RetroButton(
                        label = "Wi-Fi",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = { openAndroidSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi") }
                    )
                    RetroButton(
                        label = "Bluetooth",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = { openAndroidSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth") }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RetroButton(
                        label = "Brightness",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = { openAndroidSettings(Settings.ACTION_DISPLAY_SETTINGS, "Display") }
                    )
                    RetroButton(
                        label = "Sound / DND",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = { openAndroidSettings(Settings.ACTION_SOUND_SETTINGS, "Sound") }
                    )
                }
            }
        }

        // Home Launcher Mode Switcher (Super Easy Reversible Switch!)
        BevelBox(theme = palette, raised = true, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PixelIcon("computer", 18.dp)
                    Text(
                        "Home Launcher Mode",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }
                Text(
                    "Set RetroVibe OS as your phone's Home Screen — or switch back to your original launcher anytime in 2 taps.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = palette.textSecondary
                )
                RetroButton(
                    label = "Switch Home Launcher",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    active = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openAndroidSettings(Settings.ACTION_HOME_SETTINGS, "Default Home App") }
                )
            }
        }

        // Status Bar
        BevelBox(theme = palette, raised = false, modifier = Modifier.fillMaxWidth()) {
            Text(
                statusText,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = palette.textPrimary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}
