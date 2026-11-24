package com.retrovibe.os.ui.windows

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.RetroButton
import com.retrovibe.os.ui.win98Bevel
import kotlinx.coroutines.delay
import com.retrovibe.os.theme.accent

@Composable
fun TaskManagerWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0=Apps, 1=Storage, 2=Perf
    var selectedWinId by remember { mutableStateOf<String?>(null) }
    var uptimeSec by remember { mutableStateOf(0) }
    val cpuSamples = remember { mutableStateListOf<Float>().apply { repeat(28) { add(0.22f) } } }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            uptimeSec++
            val rt = Runtime.getRuntime()
            val usedFrac = ((rt.totalMemory() - rt.freeMemory()).toFloat() / rt.maxMemory().coerceAtLeast(1L)).coerceIn(0.05f, 0.98f)
            if (cpuSamples.size >= 28) cpuSamples.removeAt(0)
            cpuSamples.add(usedFrac)
        }
    }

    val openWindows = vm.windows.values.filter { it.isOpen }.sortedByDescending { it.zIndex }
    val bm = remember { context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager }
    val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 100

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            listOf("Applications", "Storage", "Performance").forEachIndexed { idx, label ->
                RetroButton(label = label, palette = palette, active = activeTab == idx, onClick = {
                    RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                    activeTab = idx
                })
            }
        }

        when (activeTab) {
            0 -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .win98Bevel(sunken = true, bg = Color.White)
                        .padding(3.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(openWindows, key = { it.id }) { win ->
                        val sel = selectedWinId == win.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (sel) palette.accent else Color.Transparent)
                                .clickable { selectedWinId = win.id }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = win.title,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (sel) Color.White else Color.Black
                            )
                            Text(
                                text = if (win.isMinimized) "Minimized" else "Running",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = if (sel) Color.White else Color(0xFF008000)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    RetroButton(label = "End Task", palette = palette, onClick = {
                        selectedWinId?.let { vm.closeWindow(it); selectedWinId = null }
                    })
                    RetroButton(label = "Switch To", palette = palette, onClick = {
                        selectedWinId?.let { vm.focusWindow(it) }
                    })
                    RetroButton(label = "End All", palette = palette, onClick = {
                        openWindows.filter { it.id != "taskmgr" }.forEach { vm.closeWindow(it.id) }
                    })
                    Spacer(Modifier.weight(1f))
                    RetroButton(label = "New Task...", palette = palette, onClick = { vm.openWindow("run") })
                }
            }
            1 -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .win98Bevel(sunken = true, bg = Color.White)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "RetroVibe Local Storage: %.2f KB".format(vm.storageSizeKb()),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = palette.accent
                    )
                    Text("Notes: ${vm.notes.size}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("MS Paint Artworks: ${vm.artworks.size}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("Voice Memos: ${vm.voiceMemos.size}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("Permitted Gallery Photos: ${vm.permittedPhotos.size}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("Music Tracks: ${vm.tracks.size}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("To-Do Items: ${vm.todos.size}  |  Reminders: ${vm.reminders.size}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("Recycle Bin Items: ${vm.trash.size}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
            else -> {
                val rt = Runtime.getRuntime()
                val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
                val maxMb = rt.maxMemory() / (1024 * 1024)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .win98Bevel(sunken = true, bg = Color(0xFF061006))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("JVM Heap Usage History", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF39FF14))
                    Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
                        // Grid lines
                        for (i in 1..3) {
                            val y = size.height * i / 4f
                            drawLine(Color(0xFF143814), Offset(0f, y), Offset(size.width, y), 1f)
                        }
                        if (cpuSamples.size > 1) {
                            val dx = size.width / (cpuSamples.size - 1)
                            for (i in 1 until cpuSamples.size) {
                                val p1 = Offset((i - 1) * dx, size.height * (1f - cpuSamples[i - 1]))
                                val p2 = Offset(i * dx, size.height * (1f - cpuSamples[i]))
                                drawLine(Color(0xFF39FF14), p1, p2, 2.5f)
                            }
                        }
                    }
                    Text("Memory: ${usedMb} MB / ${maxMb} MB", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF88FF88))
                    Text("Battery: $batteryPct%  |  Active Windows: ${openWindows.size}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF88FF88))
                    Text("Session Uptime: ${uptimeSec / 60}m ${uptimeSec % 60}s", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF88FF88))
                }
            }
        }
    }
}
