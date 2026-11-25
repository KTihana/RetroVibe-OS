package com.retrovibe.os.ui.windows

import android.content.Intent
import android.provider.AlarmClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.RetroButton
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ClockWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    val view = LocalView.current
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Alarms, 1 = Focus Timer, 2 = Stopwatch

    // Live Clock
    var nowStr by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val tf = DateTimeFormatter.ofPattern("HH:mm:ss")
        val df = DateTimeFormatter.ofPattern("EEE, MMM d")
        while (true) {
            val now = LocalDateTime.now()
            nowStr = now.format(tf)
            dateStr = now.format(df)
            delay(500)
        }
    }

    // Alarm Builder state
    var alarmHour by remember { mutableIntStateOf(7) }
    var alarmMin by remember { mutableIntStateOf(30) }
    var alarmLabel by remember { mutableStateOf("") }

    // Focus Timer state
    var timerTotalSec by remember { mutableIntStateOf(25 * 60) }
    var timerLeftSec by remember { mutableIntStateOf(25 * 60) }
    var timerRunning by remember { mutableStateOf(false) }
    var timerDone by remember { mutableStateOf(false) }

    LaunchedEffect(timerRunning) {
        while (timerRunning && timerLeftSec > 0) {
            delay(1000)
            timerLeftSec--
            if (timerLeftSec == 0) {
                timerRunning = false
                timerDone = true
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                RetroSoundEngine.play(RetroSound.NOTIFY, vm.soundEnabled)
            }
        }
    }

    // Stopwatch state
    var swRunning by remember { mutableStateOf(false) }
    var swTenths by remember { mutableLongStateOf(0L) }
    val laps = remember { mutableStateListOf<String>() }

    LaunchedEffect(swRunning) {
        while (swRunning) {
            delay(100)
            swTenths++
        }
    }

    fun formatSw(tenths: Long): String {
        val totalSec = tenths / 10
        val min = totalSec / 60
        val sec = totalSec % 60
        val d = tenths % 10
        return "%02d:%02d.%d".format(min, sec, d)
    }

    fun sendToSystemAlarm(hour: Int, minute: Int, label: String) {
        try {
            val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "RetroVibe Alarm" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            }
            context.startActivity(i)
        } catch (_: Throwable) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Live Retro Digital Clock Header
        BevelBox(theme = palette, raised = false, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.windowSurface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    nowStr,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary
                )
                Text(
                    dateStr,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = palette.textSecondary
                )
            }
        }

        // Tabs
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RetroButton("Alarms", palette, active = activeTab == 0, modifier = Modifier.weight(1f), onClick = { activeTab = 0 })
            RetroButton("Focus", palette, active = activeTab == 1, modifier = Modifier.weight(1f), onClick = { activeTab = 1 })
            RetroButton("Stopwatch", palette, active = activeTab == 2, modifier = Modifier.weight(1f), onClick = { activeTab = 2 })
        }

        when (activeTab) {
            0 -> {
                // New Alarm Box
                BevelBox(theme = palette, raised = true, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            RetroButton("-", palette, onClick = { alarmHour = (alarmHour + 23) % 24 })
                            Text(
                                " %02d ".format(alarmHour),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            RetroButton("+", palette, onClick = { alarmHour = (alarmHour + 1) % 24 })
                            Text(" : ", fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                            RetroButton("-5", palette, onClick = { alarmMin = (alarmMin + 55) % 60 })
                            Text(
                                " %02d ".format(alarmMin),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                            RetroButton("+5", palette, onClick = { alarmMin = (alarmMin + 5) % 60 })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            BasicTextField(
                                value = alarmLabel,
                                onValueChange = { alarmLabel = it },
                                singleLine = true,
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = palette.textPrimary),
                                decorationBox = { inner ->
                                    Box(Modifier.fillMaxWidth().background(palette.windowSurface).border(1.dp, palette.borderDark).padding(5.dp)) {
                                        if (alarmLabel.isEmpty()) Text("Label (Wake up...)", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = palette.textSecondary)
                                        inner()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            RetroButton("+ Add", palette, active = true, onClick = {
                                val lbl = alarmLabel.ifBlank { "Alarm" }
                                vm.addAlarm(alarmHour, alarmMin, lbl)
                                sendToSystemAlarm(alarmHour, alarmMin, lbl)
                                alarmLabel = ""
                            })
                        }
                    }
                }

                // Saved Alarms List
                BevelBox(theme = palette, raised = false, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (vm.alarms.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No alarms yet.\nSet a time above!",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = palette.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            items(vm.alarms, key = { it.id }) { a ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    RetroButton(
                                        label = if (a.enabled) "ON" else "OFF",
                                        theme = palette,
                                        soundEnabled = vm.soundEnabled,
                                        active = a.enabled,
                                        onClick = { vm.toggleAlarm(a.id) }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "%02d:%02d".format(a.hour, a.minute),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (a.enabled) palette.textPrimary else palette.textSecondary
                                        )
                                        Text(
                                            a.label,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = palette.textSecondary
                                        )
                                    }
                                    RetroButton("Set", palette, onClick = { sendToSystemAlarm(a.hour, a.minute, a.label) })
                                    RetroButton("×", palette, onClick = { vm.deleteAlarm(a.id) })
                                }
                                HorizontalDivider(color = palette.borderDark, thickness = 1.dp)
                            }
                        }
                    }
                }
            }

            1 -> {
                // Focus / Pomodoro Timer
                BevelBox(theme = palette, raised = false, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(palette.windowSurface)
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = if (timerDone) "TIME'S UP!" else "%02d:%02d".format(timerLeftSec / 60, timerLeftSec % 60),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (timerDone) palette.accentPink else palette.textPrimary
                        )

                        // Chunky Win98 Progress Bar
                        val pct = if (timerTotalSec > 0) timerLeftSec.toFloat() / timerTotalSec.toFloat() else 0f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .border(1.dp, palette.borderDark)
                                .background(palette.windowBg)
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val blocks = 14
                            val filled = (pct * blocks).toInt()
                            for (i in 0 until blocks) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(if (i < filled) palette.titleBarActive else Color.Transparent)
                                )
                            }
                        }

                        // Presets
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(5, 15, 25, 45).forEach { mins ->
                                RetroButton(
                                    label = "${mins}m",
                                    theme = palette,
                                    soundEnabled = vm.soundEnabled,
                                    active = timerTotalSec == mins * 60 && !timerRunning,
                                    onClick = {
                                        timerRunning = false
                                        timerDone = false
                                        timerTotalSec = mins * 60
                                        timerLeftSec = mins * 60
                                    }
                                )
                            }
                        }

                        // Start / Pause / Reset
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RetroButton(
                                label = if (timerRunning) "Pause" else "Start Focus",
                                theme = palette,
                                soundEnabled = vm.soundEnabled,
                                active = true,
                                onClick = {
                                    timerDone = false
                                    if (timerLeftSec == 0) timerLeftSec = timerTotalSec
                                    timerRunning = !timerRunning
                                }
                            )
                            RetroButton(
                                label = "Reset",
                                theme = palette,
                                soundEnabled = vm.soundEnabled,
                                onClick = {
                                    timerRunning = false
                                    timerDone = false
                                    timerLeftSec = timerTotalSec
                                }
                            )
                        }
                    }
                }
            }

            else -> {
                // Stopwatch
                BevelBox(theme = palette, raised = false, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.windowSurface)
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            formatSw(swTenths),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RetroButton(
                                label = if (swRunning) "Stop" else "Start",
                                theme = palette,
                                soundEnabled = vm.soundEnabled,
                                active = true,
                                onClick = { swRunning = !swRunning }
                            )
                            RetroButton(
                                label = "Lap",
                                theme = palette,
                                soundEnabled = vm.soundEnabled,
                                enabled = swTenths > 0L,
                                onClick = { laps.add(0, "Lap ${laps.size + 1}  —  ${formatSw(swTenths)}") }
                            )
                            RetroButton(
                                label = "Reset",
                                theme = palette,
                                soundEnabled = vm.soundEnabled,
                                onClick = {
                                    swRunning = false
                                    swTenths = 0L
                                    laps.clear()
                                }
                            )
                        }
                    }
                }

                BevelBox(theme = palette, raised = false, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (laps.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No laps recorded.", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = palette.textSecondary)
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(laps) { _, lap ->
                                Text(lap, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = palette.textPrimary)
                                HorizontalDivider(color = palette.borderDark, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
