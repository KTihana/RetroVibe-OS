package com.retrovibe.os.ui.windows

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton
import com.retrovibe.os.ui.win98Bevel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

private val HEART_BOARD_MASK = listOf(
    "00111100000111100",
    "01111110001111110",
    "11111111011111111",
    "11111111111111111",
    "11111111111111111",
    "11111111111111111",
    "11111111111111111",
    "01111111111111110",
    "00111111111111100",
    "00011111111111000",
    "00001111111110000",
    "00000111111100000",
    "00000011111000000",
    "00000001110000000",
    "00000000100000000"
)

private enum class SweeperDiff(val label: String, val rows: Int, val cols: Int, val mines: Int) {
    EASY("9x9", 9, 9, 10),
    HEART("Heart", 15, 17, 26),
    MEDIUM("16x16", 16, 16, 40)
}

private data class SweeperCell(
    val r: Int,
    val c: Int,
    val active: Boolean = true,
    val isMine: Boolean = false,
    val revealed: Boolean = false,
    val flagged: Boolean = false,
    val exploded: Boolean = false,
    val adj: Int = 0
)

private val NUM_COLORS = mapOf(
    1 to Color(0xFF1545D8),
    2 to Color(0xFF147A28),
    3 to Color(0xFFD81B3A),
    4 to Color(0xFF0E1B7A),
    5 to Color(0xFF7A1212),
    6 to Color(0xFF0E7878),
    7 to Color(0xFF222222),
    8 to Color(0xFF666666)
)

@Composable
fun HeartSweeperWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("heartsweeper_records", Context.MODE_PRIVATE) }

    var diff by remember { mutableStateOf(SweeperDiff.HEART) }
    var flagMode by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) } // 1.0f = fits entire board cleanly inside window
    var gameState by remember { mutableStateOf("ready") } // ready | playing | won | lost
    var timerSec by remember { mutableIntStateOf(0) }
    var board by remember { mutableStateOf<List<List<SweeperCell>>>(emptyList()) }
    var bestTime by remember(diff) { mutableIntStateOf(prefs.getInt("best_${diff.name}", -1)) }

    fun createEmptyBoard(d: SweeperDiff): List<List<SweeperCell>> =
        List(d.rows) { r ->
            List(d.cols) { c ->
                val active = if (d == SweeperDiff.HEART) HEART_BOARD_MASK[r][c] == '1' else true
                SweeperCell(r, c, active = active)
            }
        }

    fun reset(d: SweeperDiff = diff) {
        diff = d
        board = createEmptyBoard(d)
        gameState = "ready"
        timerSec = 0
        flagMode = false
        zoomScale = if (d == SweeperDiff.EASY) 1f else 1.35f
    }

    LaunchedEffect(Unit) { reset(SweeperDiff.HEART) }

    LaunchedEffect(gameState) {
        while (gameState == "playing") {
            delay(1000)
            timerSec = (timerSec + 1).coerceAtMost(999)
        }
    }

    fun neighbors(grid: List<List<SweeperCell>>, r: Int, c: Int): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr
            val nc = c + dc
            if (nr in grid.indices && nc in grid[0].indices && grid[nr][nc].active) {
                out.add(nr to nc)
            }
        }
        return out
    }

    fun placeMines(initial: List<List<SweeperCell>>, safeR: Int, safeC: Int): List<List<SweeperCell>> {
        val mutable = initial.map { it.toMutableList() }.toMutableList()
        val candidates = mutableListOf<Pair<Int, Int>>()
        for (r in mutable.indices) {
            for (c in mutable[r].indices) {
                if (mutable[r][c].active && (abs(r - safeR) > 1 || abs(c - safeC) > 1)) {
                    candidates.add(r to c)
                }
            }
        }
        candidates.shuffle(Random)
        candidates.take(diff.mines).forEach { (r, c) ->
            mutable[r][c] = mutable[r][c].copy(isMine = true)
        }
        for (r in mutable.indices) {
            for (c in mutable[r].indices) {
                if (!mutable[r][c].active || mutable[r][c].isMine) continue
                val count = neighbors(mutable, r, c).count { (nr, nc) -> mutable[nr][nc].isMine }
                mutable[r][c] = mutable[r][c].copy(adj = count)
            }
        }
        return mutable
    }

    fun toggleFlagAt(r: Int, c: Int) {
        if (gameState == "won" || gameState == "lost") return
        val target = board.getOrNull(r)?.getOrNull(c) ?: return
        if (!target.active || target.revealed) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
        board = board.mapIndexed { ri, row ->
            row.mapIndexed { ci, cell ->
                if (ri == r && ci == c) cell.copy(flagged = !cell.flagged) else cell
            }
        }
    }

    fun checkWinAndCommit(mutable: MutableList<MutableList<SweeperCell>>) {
        val allSafeRevealed = mutable.flatten().none { it.active && !it.isMine && !it.revealed }
        if (allSafeRevealed) {
            // Auto-flag all remaining mines on victory!
            for (ri in mutable.indices) for (ci in mutable[ri].indices) {
                val cell = mutable[ri][ci]
                if (cell.active && cell.isMine && !cell.flagged) {
                    mutable[ri][ci] = cell.copy(flagged = true)
                }
            }
            board = mutable
            gameState = "won"
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            RetroSoundEngine.play(RetroSound.SUCCESS, vm.soundEnabled)
            if (bestTime < 0 || timerSec < bestTime) {
                bestTime = timerSec
                prefs.edit().putInt("best_${diff.name}", timerSec).apply()
            }
        } else {
            board = mutable
            RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
        }
    }

    fun floodReveal(mutable: MutableList<MutableList<SweeperCell>>, startR: Int, startC: Int) {
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startR to startC)
        mutable[startR][startC] = mutable[startR][startC].copy(revealed = true)
        while (queue.isNotEmpty()) {
            val (cr, cc) = queue.removeFirst()
            if (mutable[cr][cc].adj == 0) {
                for ((nr, nc) in neighbors(mutable, cr, cc)) {
                    val nb = mutable[nr][nc]
                    if (!nb.revealed && !nb.flagged && !nb.isMine) {
                        mutable[nr][nc] = nb.copy(revealed = true)
                        queue.add(nr to nc)
                    }
                }
            }
        }
    }

    fun triggerLoss(mutable: MutableList<MutableList<SweeperCell>>, hitR: Int, hitC: Int) {
        for (ri in mutable.indices) for (ci in mutable[ri].indices) {
            val cell = mutable[ri][ci]
            if (cell.isMine && !cell.flagged) {
                mutable[ri][ci] = cell.copy(revealed = true, exploded = (ri == hitR && ci == hitC))
            }
        }
        board = mutable
        gameState = "lost"
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        RetroSoundEngine.play(RetroSound.ERROR, vm.soundEnabled)
    }

    // Tap a revealed number whose adjacent flags match its number -> auto-clear remaining safe neighbors!
    fun chordAt(r: Int, c: Int) {
        if (gameState != "playing") return
        val cell = board.getOrNull(r)?.getOrNull(c) ?: return
        if (!cell.revealed || cell.adj <= 0) return
        val nbs = neighbors(board, r, c)
        val flagCount = nbs.count { (nr, nc) -> board[nr][nc].flagged }
        if (flagCount != cell.adj) return
        val hiddenUnflagged = nbs.filter { (nr, nc) -> !board[nr][nc].revealed && !board[nr][nc].flagged }
        if (hiddenUnflagged.isEmpty()) return

        val mutable = board.map { it.toMutableList() }.toMutableList()
        for ((nr, nc) in hiddenUnflagged) {
            if (mutable[nr][nc].isMine) {
                triggerLoss(mutable, nr, nc)
                return
            }
            if (!mutable[nr][nc].revealed) {
                floodReveal(mutable, nr, nc)
            }
        }
        checkWinAndCommit(mutable)
    }

    fun tapCell(r: Int, c: Int) {
        if (gameState == "won" || gameState == "lost") return
        var grid = board
        val target = grid.getOrNull(r)?.getOrNull(c) ?: return
        if (!target.active) return

        // Tapping a revealed number performs smart chording!
        if (target.revealed) {
            chordAt(r, c)
            return
        }

        // First tap always starts the game safely even if flag mode was left on
        if (flagMode && gameState == "playing") {
            toggleFlagAt(r, c)
            return
        }

        if (target.flagged) return

        if (gameState == "ready") {
            grid = placeMines(grid, r, c)
            gameState = "playing"
        }

        val mutable = grid.map { it.toMutableList() }.toMutableList()
        if (mutable[r][c].isMine) {
            triggerLoss(mutable, r, c)
            return
        }

        floodReveal(mutable, r, c)
        checkWinAndCommit(mutable)
    }

    val flagsUsed = board.flatten().count { it.flagged }
    val minesLeft = diff.mines - flagsUsed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFD6E6))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Difficulty + Zoom controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SweeperDiff.entries.forEach { d ->
                RetroButton(
                    label = d.label,
                    palette = palette,
                    active = diff == d,
                    onClick = {
                        zoomScale = 1.0f
                        reset(d)
                    }
                )
            }
            Spacer(Modifier.weight(1f))
            RetroButton(
                label = "-",
                palette = palette,
                onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(1.0f) }
            )
            RetroButton(
                label = "+",
                palette = palette,
                onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(2.5f) }
            )
        }

        // Classic LED Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .win98Bevel(sunken = true, bg = Color(0xFFFFC1D9))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF280512))
                    .border(1.dp, Color(0xFF804060))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                val displayMines = if (minesLeft < 0) "-${(-minesLeft).toString().padStart(2, '0')}" else minesLeft.toString().padStart(3, '0')
                Text(
                    text = displayMines,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFFFF2E63)
                )
            }

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .win98Bevel(sunken = false, bg = Color(0xFFFFD6E6))
                    .clickable { reset(diff) },
                contentAlignment = Alignment.Center
            ) {
                PixelIcon(
                    when (gameState) {
                        "won" -> "wonHeart"
                        "lost" -> "brokenHeart"
                        else -> "heart"
                    },
                    20.dp
                )
            }

            Box(
                modifier = Modifier
                    .background(Color(0xFF280512))
                    .border(1.dp, Color(0xFF804060))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = timerSec.toString().padStart(3, '0'),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFFFF2E63)
                )
            }
        }

        Spacer(Modifier.height(5.dp))

        // Auto-Fit + Zoomable Board (always fits entire Heart / 9x9 / 16x16 at 1.0x!)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .win98Bevel(sunken = true, bg = Color(0xFFFFE8F2))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            val fitCellW = (maxWidth.value - 4f) / diff.cols
            val fitCellH = (maxHeight.value - 4f) / diff.rows
            val fitCell = minOf(fitCellW, fitCellH).coerceIn(12f, 36f)
            val cellSize = (fitCell * zoomScale).coerceIn(12f, 46f).dp

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                board.forEach { row ->
                    Row {
                        row.forEach { cell ->
                            if (!cell.active) {
                                Spacer(Modifier.size(cellSize))
                            } else {
                                val bg = when {
                                    cell.exploded -> Color(0xFFFF3B5C)
                                    cell.revealed -> Color(0xFFFFF2F7)
                                    else -> Color(0xFFFFAEC6)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(cellSize)
                                        .then(
                                            if (cell.revealed) {
                                                Modifier
                                                    .background(bg)
                                                    .border(0.5.dp, Color(0xFFE5A4BF))
                                            } else {
                                                Modifier.win98Bevel(sunken = false, bg = bg, strokeDp = 1.5.dp)
                                            }
                                        )
                                        .pointerInput(cell.r, cell.c, gameState, flagMode) {
                                            detectTapGestures(
                                                onTap = { tapCell(cell.r, cell.c) },
                                                onLongPress = { toggleFlagAt(cell.r, cell.c) }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        gameState == "lost" && cell.flagged && !cell.isMine ->
                                            PixelIcon("wrongFlag", (cellSize.value * 0.76f).dp)
                                        cell.flagged && !cell.revealed ->
                                            PixelIcon("flag_heart", (cellSize.value * 0.74f).dp)
                                        cell.revealed && cell.isMine ->
                                            PixelIcon("brokenHeart", (cellSize.value * 0.78f).dp)
                                        cell.revealed && cell.adj > 0 -> Text(
                                            text = cell.adj.toString(),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black,
                                            fontSize = (cellSize.value * 0.56f).coerceIn(9f, 20f).sp,
                                            color = NUM_COLORS[cell.adj] ?: Color.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        // Thumb-friendly bottom Dig / Flag bar + Best Time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RetroButton(
                label = if (!flagMode) "[*] Dig" else "Dig",
                palette = palette,
                active = !flagMode,
                modifier = Modifier.weight(1f),
                onClick = { flagMode = false }
            )
            RetroButton(
                label = if (flagMode) "[*] Flag" else "Flag",
                palette = palette,
                active = flagMode,
                modifier = Modifier.weight(1f),
                onClick = { flagMode = true }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = when (gameState) {
                    "won" -> "YOU WON! Tap heart to replay"
                    "lost" -> "BOOM! Tap heart to retry"
                    else -> "Hold to flag • Tap number to chord"
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Color(0xFF7A1C42)
            )
            Text(
                text = if (bestTime >= 0) "Best: ${bestTime}s" else "Best: --",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Color(0xFF7A1C42)
            )
        }
    }
}
