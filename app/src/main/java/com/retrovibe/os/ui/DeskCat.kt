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

private val MINDI_PALETTE = mapOf(
    'k' to Color(0xFF1C1420),
    'l' to Color(0xFFFFDF9E),
    'o' to Color(0xFFF5A958),
    'm' to Color(0xFFE68D3E),
    'd' to Color(0xFFC46B24),
    'p' to Color(0xFFF488A4),
    'q' to Color(0xFFFFBCD0),
    'w' to Color(0xFFFFFFFF),
    'e' to Color(0xFF181420),
    'u' to Color(0xFF181420),
    'f' to Color(0xFF5CB8EA),
    'g' to Color(0xFFA8E4FF),
    'z' to Color(0xFF5A84E0)
)

private val SPOOKS_PALETTE = mapOf(
    'k' to Color(0xFF0A0912),
    'l' to Color(0xFF524F6E),
    'o' to Color(0xFF2E2C40),
    'm' to Color(0xFF232132),
    'd' to Color(0xFF171524),
    'p' to Color(0xFFF08CA8),
    'q' to Color(0xFFFFC2D4),
    'w' to Color(0xFFFFFFFF),
    'e' to Color(0xFF56E878),
    'u' to Color(0xFF9894B4),
    'f' to Color(0xFF5CB8EA),
    'g' to Color(0xFFA8E4FF),
    'z' to Color(0xFF8CA8FF)
)

private val MINDI_CAT_IDLE_1 = listOf(
    "................................",
    "................................",
    "................................",
    ".........k............k.........",
    "........kok..........kok........",
    "........kook........kook........",
    "........kqook......kooqk........",
    ".......koppokkkkkkkkppook.......",
    ".......koppooododmdoppook.......",
    "........kllloodododmmmdk........",
    ".......klllolododmmmmmmdk.......",
    "......kllloloooooommmmmmdk......",
    "......klllweeoooomoweemdmk......",
    "......klddeeeooooomeeedddk......",
    "......kllleeeooppmoeeemdmk......",
    "......kldpplououuoummppddk......",
    ".......klppolouooummmppdk.......",
    "........kllloooomommmmdk.kkk....",
    ".........kklloooommmmkk.klook...",
    ".........klkkddddddkkdk.koddk...",
    "........kllololololomddk.kklok..",
    ".......kllolololololdmddk.kodk..",
    ".......klllololololomdddkklok...",
    "......klllooololololmmdddkodk...",
    "......kllllooooooooomdmddlok....",
    "......kllloooooooooommddlodk....",
    "......kllllkoookkmmmkdmlodk.....",
    ".......klloklllkklllkmdodk......",
    ".......klllklllkklllkdddk.......",
    "........kllkomokkmmmkddk........",
    ".........kkkkkkkkkkkkkk.........",
    "................................"
)

private val MINDI_CAT_IDLE_2 = listOf(
    "................................",
    "................................",
    "................................",
    ".........k............k.........",
    "........kok..........kok........",
    "........kook........kook........",
    "........kqook......kooqk........",
    ".......koppokkkkkkkkppook.......",
    ".......koppooododmdoppook.......",
    "........kllloodododmmmdk........",
    ".......klllolododmmmmmmdk.......",
    "......kllloloooooommmmmmdk......",
    "......klllloooooomommmmdmk......",
    "......kldduuuooooomuuudddk......",
    "......kllllooooppmommmmdmk......",
    "......kldpplououuoummppddk......",
    ".......klppolouooummmppdlok.....",
    "........kllloooomommmmdkodok....",
    ".........kklloooommmmkk.kodk....",
    ".........klkkddddddkkdk..klok...",
    "........kllololololomddk.kodk...",
    ".......kllolololololdmddkklok...",
    ".......klllololololomdddkkodk...",
    "......klllooololololmmdddlok....",
    "......kllllooooooooomdmdlodk....",
    "......kllloooooooooommdlodk.....",
    "......kllllkoookkmmmkdmodk......",
    ".......klloklllkklllkmddk.......",
    ".......klllklllkklllkdddk.......",
    "........kllkomokkmmmkddk........",
    ".........kkkkkkkkkkkkkk.........",
    "................................"
)

private val MINDI_CAT_PURR = listOf(
    ".............pq..pp.............",
    "............qwpppppp............",
    ".............pppppp.............",
    ".........k....pppp....k.........",
    "........kok....pp....kok........",
    "........kook........kook........",
    "........kqook......kooqk........",
    ".......koppokkkkkkkkppook.......",
    ".......koppooododmdoppook.......",
    "........kllloodododmmmdk........",
    ".......klllolododmmmmmmdk.......",
    "......kllloloooooommmmmmdk......",
    "......klllluooooomomummdmk......",
    "......kldduluooooomumudddk......",
    "......klpppooooppmommpppmk......",
    "......klppplououuoummpppdk......",
    ".......klppolouooummmppdlok.....",
    "........kllloooomommmmdkodok....",
    ".........kklloooommmmkk.kodk....",
    ".........klkkddddddkkdk..klok...",
    "........kllololololomddk.kodk...",
    ".......kllolololololdmddkklok...",
    ".......klllololololomdddkkodk...",
    "......klllooololololmmdddlok....",
    "......kllllooooooooomdmdlodk....",
    "......kllloooooooooommdlodk.....",
    "......kllllkoookkmmmkdmodk......",
    ".......klloklllkklllkmddk.......",
    ".......klllklllkklllkdddk.......",
    "........kllkomokkmmmkddk........",
    ".........kkkkkkkkkkkkkk.........",
    "................................"
)

private val MINDI_CAT_EAT = listOf(
    "................................",
    "................................",
    "................................",
    ".........k............k.........",
    "........kok..........kok........",
    "........kook........kook........",
    "........kqook......kooqk........",
    ".......koppokkkkkkkkppook.......",
    ".......koppooododmdoppook.......",
    "........kllloodododmmmdk........",
    ".......klllolododmmmmmmdk.......",
    "......kllloloooooommmmmmdk......",
    "......klllluooooomomummdmk......",
    "......kldduluooooomumudddk......",
    "......kllllooooppmommmmdmk......",
    "......kldpplooppppmmmppddk......",
    ".......klppolopqqpmmmppdk.......",
    "........kllloooomommmmdk.kkk....",
    ".........kklloooommmmkk.klook...",
    ".........klkkddddddkkdk.koddk...",
    "........kllololololomddk.kklok..",
    ".......kllolololololdmddk.kodk..",
    ".......klllololololomdddkklok...",
    "......klllooololololmmdddkodk...",
    "......kllllooooooooomdmddlok....",
    "......kllloookkkkkkokkkdlodk....",
    "......kllllkkggggffkkgklodk.....",
    ".......kllokfwffffffkfkodk......",
    ".......klllkkffffffkkfkdk.......",
    "........kllkokkkkkkmkkkk........",
    ".........kkkkkkkkkkkkkk.........",
    "................................"
)

private val MINDI_CAT_SLEEP = listOf(
    "......................zzzz......",
    "........................z..zzz..",
    ".......................z....z...",
    "......................zzzz.zzz..",
    ".........k............k.........",
    "........kok..........kok........",
    "........kook........kook........",
    "........kqook......kooqk........",
    ".......koppokkkkkkkkppook.......",
    ".......koppooldodmdoppook.......",
    "........kllolododmdmmmmk........",
    ".......klllloododommmmdmk.......",
    "......klllloooooomommmmdmk......",
    "......kllloloooooommmmmmdk......",
    "......kldduouoooomoumuddmk......",
    "......klllouoooppommummmdk......",
    "......kldppoooooomommppdmk......",
    ".......klpplooouuommmppmk.......",
    "........klloloooommmmmmk........",
    ".........kkllloommmmdkk.........",
    ".........klkkddddddkkdk.........",
    "........klllololololdddk........",
    ".......klllololololomdddk.......",
    ".......kllolololololdmddk.......",
    "......kllllololololomdmddk......",
    "......kllloooooooooommdddk......",
    "......kllllooooooooomdmddk......",
    "......klllokoookkmmmkmdddkkk....",
    ".......klllklllkklllkdollllok...",
    ".......klloklllkklllkmooooook...",
    "........kllkomokkmmmkdoddddok...",
    ".........kkkkkkkkkkkkkkkkkkk...."
)

private val SPOOKS_CAT_IDLE_1 = listOf(
    "................................",
    "................................",
    "................................",
    ".........k............k.........",
    "........kok..........kok........",
    "........kook........kook........",
    "........kqook......kooqk........",
    ".......koppokkkkkkkkppook.......",
    ".......koppooododmdoppook.......",
    "........kllloodododmmmdk........",
    ".......klllolododmmmmmmdk.......",
    "......kllloloooooommmmmmdk......",
    "......klllweeoooomoweemdmk......",
    "......klddekeooooomekedddk......",
    ".....klllleeeooppmoeeemdmdk.....",
    "......kldpplououuoummppddk......",
    ".......klppolouooummmppdk.......",
    "........kllloooomommmmdk.kkkk...",
    ".........kklloooommmmkk.kloook..",
    ".........klkkddddddkkdk.kooook..",
    "........kllololololomddkkooddok.",
    ".......kllolololololdmddkkkoook.",
    "......kllllololololomdddkkloodk.",
    "......klllooololololmmdddkoook..",
    ".....kollllooooooooomdmddloodk..",
    "......kllloooooooooommddloook...",
    "......kllllkoookkmmmkdmlooodk...",
    ".......klloklllkklllkmdooodk....",
    ".......klllklllkklllkddoodk.....",
    "........kllkomokkmmmkddkkk......",
    ".........kkkkkkkkkkkkkk.........",
    "................................"
)

private val SPOOKS_CAT_IDLE_2 = listOf(
    "................................",
    "................................",
    "................................",
    ".........k............k.........",
    "........kok..........kok........",
    "........kook........kook........",
    "........kqook......kooqk........",
    ".......koppokkkkkkkkppook.......",
    ".......koppooododmdoppook.......",
    "........kllloodododmmmdk........",
    ".......klllolododmmmmmmdk.......",
    "......kllloloooooommmmmmdk......",
    "......klllloooooomommmmdmk......",
    "......kldduuuooooomuuudddk......",
    ".....klllllooooppmommmmdmdk.....",
    "......kldpplououuoummppddkk.....",
    ".......klppolouooummmppdlook....",
    "........kllloooomommmmdkooook...",
    ".........kklloooommmmkkkoodok...",
    ".........klkkddddddkkdk.koodok..",
    "........kllololololomddk.koook..",
    ".......kllolololololdmddkkoodk..",
    "......kllllololololomdddkkoook..",
    "......klllooololololmmdddloodk..",
    ".....kollllooooooooomdmdloook...",
    "......kllloooooooooommdlooodk...",
    "......kllllkoookkmmmkdmooodk....",
    ".......klloklllkklllkmdoodk.....",
    ".......klllklllkklllkdddkk......",
    "........kllkomokkmmmkddk........",
    ".........kkkkkkkkkkkkkk.........",
    "................................"
)

private val SPOOKS_CAT_PURR = listOf(
    ".............pq..pp.............",
    "............qwpppppp............",
    ".............pppppp.............",
    ".........k....pppp....k.........",
    "........kok....pp....kok........",
    "........kook........kook........",
    "........kqook......kooqk........",
    ".......koppokkkkkkkkppook.......",
    ".......koppooododmdoppook.......",
    "........kllloodododmmmdk........",
    ".......klllolododmmmmmmdk.......",
    "......kllloloooooommmmmmdk......",
    "......klllluooooomomummdmk......",
    "......kldduluooooomumudddk......",
    ".....kllpppooooppmommpppmdk.....",
    "......klppplououuoummpppdkk.....",
    ".......klppolouooummmppdlook....",
    "........kllloooomommmmdkooook...",
    ".........kklloooommmmkkkoodok...",
    ".........klkkddddddkkdk.koodok..",
    "........kllololololomddk.koook..",
    ".......kllolololololdmddkkoodk..",
    "......kllllololololomdddkkoook..",
    "......klllooololololmmdddloodk..",
    ".....kollllooooooooomdmdloook...",
    "......kllloooooooooommdlooodk...",
    "......kllllkoookkmmmkdmooodk....",
    ".......klloklllkklllkmdoodk.....",
    ".......klllklllkklllkdddkk......",
    "........kllkomokkmmmkddk........",
    ".........kkkkkkkkkkkkkk.........",
    "................................"
)

private val SPOOKS_CAT_EAT = listOf(
    "................................",
    "................................",
    "................................",
    ".........k............k.........",
    "........kok..........kok........",
    "........kook........kook........",
    "........kqook......kooqk........",
    ".......koppokkkkkkkkppook.......",
    ".......koppooododmdoppook.......",
    "........kllloodododmmmdk........",
    ".......klllolododmmmmmmdk.......",
    "......kllloloooooommmmmmdk......",
    "......klllluooooomomummdmk......",
    "......kldduluooooomumudddk......",
    ".....klllllooooppmommmmdmdk.....",
    "......kldpplooppppmmmppddk......",
    ".......klppolopqqpmmmppdk.......",
    "........kllloooomommmmdk.kkkk...",
    ".........kklloooommmmkk.kloook..",
    ".........klkkddddddkkdk.kooook..",
    "........kllololololomddkkooddok.",
    ".......kllolololololdmddkkkoook.",
    "......kllllololololomdddkkloodk.",
    "......klllooololololmmdddkoook..",
    ".....kollllooooooooomdmddloodk..",
    "......kllloookkkkkkokkkdloook...",
    "......kllllkkggggffkkgklooodk...",
    ".......kllokfwffffffkfkooodk....",
    ".......klllkkffffffkkfkoodk.....",
    "........kllkokkkkkkmkkkkkk......",
    ".........kkkkkkkkkkkkkk.........",
    "................................"
)

private val SPOOKS_CAT_SLEEP = listOf(
    "......................zzzz......",
    "........................z..zzz..",
    ".......................z....z...",
    "......................zzzz.zzz..",
    ".........k............k.........",
    "........kok..........kok........",
    "........kook........kook........",
    "........kqook......kooqk........",
    ".......koppokkkkkkkkppook.......",
    ".......koppooldodmdoppook.......",
    "........kllolododmdmmmmk........",
    ".......klllloododommmmdmk.......",
    "......klllloooooomommmmdmk......",
    "......kllloloooooommmmmmdk......",
    "......kldduouoooomoumuddmk......",
    ".....kllllouoooppommummmddk.....",
    "......kldppoooooomommppdmk......",
    ".......klpplooouuommmppmk.......",
    "........klloloooommmmmmk........",
    ".........kkllloommmmdkk.........",
    ".........klkkddddddkkdk.........",
    "........klllololololdddk........",
    ".......klllololololomdddk.......",
    "......klllolololololdmddk.......",
    "......kllllololololomdmddk......",
    ".....kollloooooooooommddddk.....",
    "......kllllooooooooomdmddk......",
    "......klllokoookkmmmkmdddkkk....",
    ".......klllklllkklllkdollllok...",
    ".......klloklllkklllkmooooook...",
    "........kllkomokkmmmkdoddddok...",
    ".........kkkkkkkkkkkkkkkkkkk...."
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

    val isSpooks = vm.deskCatVariant == "spooks"
    val activePalette = if (isSpooks) SPOOKS_PALETTE else MINDI_PALETTE
    val sprite = if (isSpooks) {
        when (mood) {
            "sleep" -> SPOOKS_CAT_SLEEP
            "purr" -> SPOOKS_CAT_PURR
            "eat" -> SPOOKS_CAT_EAT
            else -> if (frame == 0) SPOOKS_CAT_IDLE_1 else SPOOKS_CAT_IDLE_2
        }
    } else {
        when (mood) {
            "sleep" -> MINDI_CAT_SLEEP
            "purr" -> MINDI_CAT_PURR
            "eat" -> MINDI_CAT_EAT
            else -> if (frame == 0) MINDI_CAT_IDLE_1 else MINDI_CAT_IDLE_2
        }
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
            val cw = size.width / 32f
            val ch = size.height / 32f
            for (y in sprite.indices) {
                for (x in sprite[y].indices) {
                    val col = activePalette[sprite[y][x]] ?: continue
                    drawRect(col, Offset(x * cw, y * ch), Size(cw + 0.5f, ch + 0.5f))
                }
            }
        }

        if (menuOpen) {
            BevelBox(
                theme = theme,
                raised = true,
                modifier = Modifier
                    .offset(x = if (posX > boundsW - 140f) (-70).dp else 0.dp, y = (-156).dp)
                    .width(142.dp)
            ) {
                Column(modifier = Modifier.padding(2.dp)) {
                    Text(
                        text = if (isSpooks) "~ Spooks ~" else "~ Mindi ~",
                        color = theme.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                    CatMenuRow(if (isSpooks) "Pet Spooks" else "Pet Mindi", theme.textPrimary) { menuOpen = false; pet() }
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
                    CatMenuRow(if (isSpooks) "Switch to Mindi" else "Switch to Spooks", theme.titleBarActive) {
                        menuOpen = false
                        vm.deskCatVariant = if (isSpooks) "mindi" else "spooks"
                        vm.persist()
                        RetroSoundEngine.play(RetroSound.PURR, vm.soundEnabled)
                        showMsg(if (vm.deskCatVariant == "spooks") "Hi, I'm Spooks!" else "Hi, I'm Mindi!")
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
