package com.retrovibe.os.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroThemes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CAT_PALETTE = mapOf(
    'k' to Color(0xFF1C1824),
    'o' to Color(0xFFF4B878),
    'l' to Color(0xFFFFE2BD),
    'd' to Color(0xFFD18D4B),
    'p' to Color(0xFFF28FA8),
    'w' to Color(0xFFFFFFFF),
    'e' to Color(0xFF1C1824),
    'f' to Color(0xFF6FB7E8),
    'z' to Color(0xFF4A6FD0)
)

private val CAT_IDLE_1 = listOf(
    "................",
    "..kk........kk..",
    ".kopkkkkkkkkpok.",
    ".kooooooooooook.",
    ".kooodoooodoook.",
    ".kooeeooooeeook.",
    ".kooewooooewook.",
    ".kooollpplloook.",
    ".kooolkllkloook.",
    "..kllllllllllk..",
    "..kooolllloook.k",
    "..kdoooooooodkok",
    "..kdoooooooodkok",
    ".kllkooooookllkk",
    ".kkkkkkkkkkkkkk.",
    "................"
)

private val CAT_IDLE_2 = listOf(
    "................",
    "..kk........kk..",
    ".kopkkkkkkkkpok.",
    ".kooooooooooook.",
    ".kooodoooodoook.",
    ".kooooooooooook.",
    ".kookkooookkook.",
    ".kooollpplloook.",
    ".kooolkllkloook.",
    "..kllllllllllk.k",
    "..kooolllloookok",
    "..kdoooooooodkok",
    "..kdoooooooodkok",
    ".kllkooooookllkk",
    ".kkkkkkkkkkkkkk.",
    "................"
)

private val CAT_PURR = listOf(
    "......p..p......",
    "..kk...pp...kk..",
    ".kopkkkkkkkkpok.",
    ".kooooooooooook.",
    ".kooodoooodoook.",
    ".kookkooookkook.",
    ".kopoooooooopok.",
    ".kopollppllopok.",
    ".kooolkllkloook.",
    "..kllllllllllk.k",
    "..kooolllloookok",
    "..kdoooooooodkok",
    "..kdoooooooodkok",
    ".kllkooooookllkk",
    ".kkkkkkkkkkkkkk.",
    "................"
)

private val CAT_EAT = listOf(
    "................",
    "..kk........kk..",
    ".kopkkkkkkkkpok.",
    ".kooooooooooook.",
    ".kooodoooodoook.",
    ".kooeeooooeeook.",
    ".kooewooooewook.",
    ".kooollpplloook.",
    ".kooolkkkkloook.",
    "..kllllppllllk.k",
    "..kooolllloookok",
    "..kdkkkkkkkodkok",
    "..kkfffffwfkk...",
    ".kllkkkkkkkkllkk",
    ".kkkkkkkkkkkkkk.",
    "................"
)

private val CAT_SLEEP = listOf(
    "...........zzz..",
    ".............z..",
    "...........zzz..",
    "..kk........kk..",
    ".kopkkkkkkkkpok.",
    ".kooooooooooook.",
    ".kooodoooodoook.",
    ".kookkooookkook.",
    ".kooooooooooook.",
    ".kooollpplloook.",
    ".kooolkllkloook.",
    "..kllllllllllk..",
    "..kooolllloook..",
    ".kllkooooookllk.",
    ".kkkkkkkkkkkkkk.",
    "................"
)


@Composable
fun DeskCat(vm: OSViewModel, boundsW: Float, boundsH: Float) {
    if (!vm.deskCatEnabled) return
    val theme = RetroThemes.of(vm.themeId)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val catSize = 54f
    val floorY = (boundsH - catSize + 4f).coerceAtLeast(0f)

    var mood by remember { mutableStateOf("idle") }
    var frame by remember { mutableIntStateOf(0) }
    var bubble by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var posX by remember { mutableFloatStateOf(vm.deskCatX.coerceIn(8f, (boundsW - catSize - 8f).coerceAtLeast(8f))) }
    var posY by remember { mutableFloatStateOf(floorY) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(floorY) {
        if (!dragging) posY = floorY
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(650)
            frame = (frame + 1) % 2
        }
    }

    fun showMsg(m: String) {
        bubble = m
        scope.launch {
            delay(2000)
            if (bubble == m) bubble = null
        }
    }

    fun pet() {
        RetroSoundEngine.play(RetroSound.PURR, vm.soundEnabled)
        mood = "purr"
        showMsg("Purrr...")
        scope.launch {
            delay(2000)
            if (mood == "purr") mood = "idle"
        }
    }

    fun fallToTaskbar() {
        scope.launch {
            var vy = 0f
            var bounced = false
            while (posY < floorY || !bounced) {
                vy += 1.45f
                posY += vy
                if (posY >= floorY) {
                    posY = floorY
                    if (!bounced && vy > 6f) {
                        vy = -vy * 0.28f
                        bounced = true
                        RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                    } else {
                        break
                    }
                }
                delay(16)
            }
            posY = floorY
            vm.deskCatX = posX
            vm.persist()
        }
    }

    val sprite = when (mood) {
        "sleep" -> CAT_SLEEP
        "purr" -> CAT_PURR
        "eat" -> CAT_EAT
        else -> if (frame == 0) CAT_IDLE_1 else CAT_IDLE_2
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(with(density) { posX.dp.roundToPx() }, with(density) { posY.dp.roundToPx() }) }
            .zIndex(if (dragging || menuOpen) 900f else 12f)
    ) {
        if (bubble != null) {
            Box(
                modifier = Modifier
                    .offset(y = (-22).dp)
                    .background(Color(0xFFFFFDF5))
                    .border(1.5.dp, Color(0xFF111111))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(bubble!!, fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }

        Canvas(
            modifier = Modifier
                .size(catSize.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            menuOpen = false
                            pet()
                        },
                        onLongPress = {
                            menuOpen = !menuOpen
                        }
                    )
                }
                .pointerInput(boundsW, floorY) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            menuOpen = false
                        },
                        onDragEnd = {
                            dragging = false
                            if (floorY - posY > 40f) showMsg("wheee!")
                            fallToTaskbar()
                        },
                        onDragCancel = {
                            dragging = false
                            fallToTaskbar()
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        posX = (posX + dragAmount.x / density.density).coerceIn(8f, (boundsW - catSize - 8f).coerceAtLeast(8f))
                        posY = (posY + dragAmount.y / density.density).coerceIn(8f, floorY)
                    }
                }
        ) {
            val cw = size.width / 16f
            val ch = size.height / 16f
            for (y in sprite.indices) {
                for (x in sprite[y].indices) {
                    val col = CAT_PALETTE[sprite[y][x]] ?: continue
                    drawRect(col, Offset(x * cw, y * ch), Size(cw + 0.5f, ch + 0.5f))
                }
            }
        }

        if (menuOpen) {
            BevelBox(
                theme = theme,
                raised = true,
                modifier = Modifier
                    .offset(x = if (posX > boundsW - 140f) (-70).dp else 0.dp, y = (-118).dp)
                    .width(122.dp)
            ) {
                Column(modifier = Modifier.padding(2.dp)) {
                    CatMenuRow("Pet Cat", theme.textPrimary) { menuOpen = false; pet() }
                    CatMenuRow("Feed Treat", theme.textPrimary) {
                        menuOpen = false
                        RetroSoundEngine.play(RetroSound.PURR, vm.soundEnabled)
                        mood = "eat"
                        showMsg("Yummy!")
                        scope.launch { delay(2400); if (mood == "eat") mood = "idle" }
                    }
                    CatMenuRow(if (mood == "sleep") "Wake Up" else "Take a Nap", theme.textPrimary) {
                        menuOpen = false
                        mood = if (mood == "sleep") "idle" else "sleep"
                        showMsg(if (mood == "sleep") "Zzz..." else "Meow!")
                    }
                    CatMenuRow("Remove Cat", Color(0xFFB01237)) {
                        menuOpen = false
                        RetroSoundEngine.play(RetroSound.TRASH, vm.soundEnabled)
                        vm.deskCatEnabled = false
                        vm.persist()
                    }
                }
            }
        }
    }
}

@Composable
private fun CatMenuRow(label: String, color: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}
