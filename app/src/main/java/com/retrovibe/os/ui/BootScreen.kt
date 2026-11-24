package com.retrovibe.os.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val BOOT_STEPS = listOf(
    "RetroVibe BIOS v98.4 · Memory OK (64MB)",
    "Mounting C:\\RETROVIBE\\ ...",
    "Loading Win98 Pixel Shell & Audio...",
    "Starting RetroVibe OS..."
)

@Composable
fun BootScreen(onFinish: () -> Unit) {
    var stepIdx by remember { mutableIntStateOf(0) }
    var barPos by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var elapsed = 0
        while (elapsed < 2200) {
            delay(110)
            elapsed += 110
            barPos = (barPos + 1) % 14
            stepIdx = when {
                elapsed > 1500 -> 3
                elapsed > 950 -> 2
                elapsed > 420 -> 1
                else -> 0
            }
        }
        onFinish()
    }

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C1020))
            .safeDrawingPadding()
            .clickable { onFinish() }
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("RETROVIBE SYSTEMS INC. (C) 1998", color = Color(0xFF7FA8D4), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            Text("OS-98 ANDROID EDITION · BUILD 2026", color = Color(0xFF7FA8D4), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelIcon("winFlag", size = 56.dp)
            Text("RetroVibe OS", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Black)

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .width(210.dp)
                    .height(16.dp)
                    .background(Color(0xFF161B2E))
                    .border(2.dp, Color(0xFF6B7BA0))
                    .padding(2.dp)
            ) {
                for (i in 0 until 14) {
                    val active = i == barPos || i == (barPos + 1) % 14 || i == (barPos + 2) % 14
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (active) Color(0xFFE688A8) else Color.Transparent)
                    )
                }
            }
            Text(BOOT_STEPS[stepIdx], color = Color(0xFFC8D6EB), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }

        Text("Tap anywhere to skip", color = Color(0xFF5C6B8A), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}
