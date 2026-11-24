package com.retrovibe.os.ui.windows

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton
import com.retrovibe.os.ui.win98Bevel
import kotlinx.coroutines.delay
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
    "00000000100000000",
    "00000000000000000"
)

private enum class SweeperDiff(val label: String, val rows: Int, val cols: Int, val mines: Int) {
    EASY("9x9", 9, 9, 10),
    MEDIUM("16x16", 16, 16, 40),
    HEART("Heart", 16, 17, 32)
}

private data class SweeperCell(
    val r: Int,
    val c: Int,
    val active: Boolean = true,
    val isMine: Boolean = false,
    val revealed: Boolean = false,
    val flagged: Boolean = false,
    val adj: Int = 0
)

private val NUM_COLORS = mapOf(
    1 to Color(0xFF0000FF),
    2 to Color(0xFF008000),
    3 to Color(0xFFFF0000),
    4 to Color(0xFF000080),
    5 to Color(0xFF800000),
    6 to Color(0xFF008080),
    7 to Color(0xFF000000),
    8 to Color(0xFF808080)
)

@Composable
fun HeartSweeperWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("heartsweeper_records", Context.MODE_PRIVATE) }

    var diff by remember { mutableStateOf(SweeperDiff.HEART) }
    var flagMode by remember { mutableStateOf(false) }
    var gameState by remember { mutableStateOf("ready") } // ready | playing | won | lost
    var timerSec by remember { mutableStateOf(0) }
    var board by remember { mutableStateOf<List<List<SweeperCell>>>(emptyList()) }
    var bestTime by remember(diff) { mutableStateOf(prefs.getInt("best_${diff.name}", -1)) }

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
    }

    LaunchedEffect(Unit) { reset(SweeperDiff.HEART) }

    LaunchedEffect(gameState) {
        while (gameState == "playing") {
            delay(1000)
            timerSec = (timerSec + 1).coerceAtMost(999)
        }
    }

    fun placeMines(initial: List<List<SweeperCell>>, safeR: Int, safeC: Int): List<List<SweeperCell>> {
        val mutable = initial.map { it.toMutableList() }.toMutableList()
        val candidates = mutableListOf<Pair<Int, Int>>()
        for (r in mutable.indices) {
            for (c in mutable[r].indices) {
                if (mutable[r][c].active && (kotlin.math.abs(r - safeR) > 1 || kotlin.math.abs(c - safeC) > 1)) {
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
                var count = 0
                for (dr in -1..1) for (dc in -1..1) {
                    val nr = r + dr; val nc = c + dc
                    if (nr in mutable.indices && nc in mutable[0].indices && mutable[nr][nc].active && mutable[nr][nc].isMine) {
                        count++
                    }
                }
                mutable[r][c] = mutable[r][c].copy(adj = count)
            }
        }
        return mutable
    }

    fun revealAt(r: Int, c: Int) {
        if (gameState == "won" || gameState == "lost") return
        var grid = board
        val target = grid.getOrNull(r)?.getOrNull(c) ?: return
        if (!target.active) return

        if (flagMode && !target.revealed) {
            RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
            board = grid.mapIndexed { ri, row ->
                row.mapIndexed { ci, cell ->
                    if (ri == r && ci == c) cell.copy(flagged = !cell.flagged) else cell
                }
            }
            return
        }

        if (target.flagged) return

        if (gameState == "ready") {
            grid = placeMines(grid, r, c)
            gameState = "playing"
        }

        val mutable = grid.map { it.toMutableList() }.toMutableList()
        val clicked = mutable[r][c]
        if (clicked.revealed) return

        if (clicked.isMine) {
            for (ri in mutable.indices) for (ci in mutable[ri].indices) {
                if (mutable[ri][ci].isMine) mutable[ri][ci] = mutable[ri][ci].copy(revealed = true)
            }
            board = mutable
            gameState = "lost"
            RetroSoundEngine.play(RetroSound.ERROR, vm.soundEnabled)
            return
        }

        // BFS flood reveal
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(r to c)
        mutable[r][c] = mutable[r][c].copy(revealed = true)
        while (queue.isNotEmpty()) {
            val (cr, cc) = queue.removeFirst()
            if (mutable[cr][cc].adj == 0) {
                for (dr in -1..1) for (dc in -1..1) {
                    val nr = cr + dr; val nc = cc + dc
                    if (nr in mutable.indices && nc in mutable[0].indices) {
                        val nb = mutable[nr][nc]
                        if (nb.active && !nb.revealed && !nb.flagged && !nb.isMine) {
                            mutable[nr][nc] = nb.copy(revealed = true)
                            queue.add(nr to nc)
                        }
                    }
                }
            }
        }

        val allSafeRevealed = mutable.flatten().none { it.active && !it.isMine && !it.revealed }
        board = mutable
        if (allSafeRevealed) {
            gameState = "won"
            RetroSoundEngine.play(RetroSound.SUCCESS, vm.soundEnabled)
            if (bestTime < 0 || timerSec < bestTime) {
                bestTime = timerSec
                prefs.edit().putInt("best_${diff.name}", timerSec).apply()
            }
        } else {
            RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
        }
    }

    val flagsUsed = board.flatten().count { it.flagged }
    val minesLeft = (diff.mines - flagsUsed).coerceAtLeast(0)
    val cellSize = if (diff == SweeperDiff.EASY) 28.dp else 18.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFD6E6))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Difficulty tabs
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SweeperDiff.entries.forEach { d ->
                RetroButton(
                    label = d.label,
                    palette = palette,
                    active = diff == d,
                    onClick = { reset(d) }
                )
            }
            Spacer(Modifier.weight(1f))
            RetroButton(
                label = if (flagMode) "Flag: ON" else "Reveal",
                palette = palette,
                active = flagMode,
                onClick = { flagMode = !flagMode }
            )
        }

        // LED Header bar
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
                Text(
                    text = minesLeft.toString().padStart(3, '0'),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFFE02255)
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .win98Bevel(sunken = false, bg = Color(0xFFFFD6E6))
                    .clickable { reset(diff) },
                contentAlignment = Alignment.Center
            ) {
                PixelIcon(
                    when (gameState) {
                        "won" -> "heart"
                        "lost" -> "mine_heart"
                        else -> "sweeper"
                    },
                    18.dp
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
                    color = Color(0xFFE02255)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Board
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .win98Bevel(sunken = true, bg = Color(0xFFFFE5F0))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
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
                                Box(
                                    modifier = Modifier
                                        .size(cellSize)
                                        .then(
                                            if (cell.revealed) {
                                                Modifier
                                                    .background(Color(0xFFFFEEF5))
                                                    .border(0.5.dp, Color(0xFFE8A4BF))
                                            } else {
                                                Modifier.win98Bevel(sunken = false, bg = Color(0xFFFFAEC6))
                                            }
                                        )
                                        .clickable { revealAt(cell.r, cell.c) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        cell.flagged && !cell.revealed -> PixelIcon("flag_heart", (cellSize.value * 0.72f).dp)
                                        cell.revealed && cell.isMine -> PixelIcon("mine_heart", (cellSize.value * 0.75f).dp)
                                        cell.revealed && cell.adj > 0 -> Text(
                                            text = cell.adj.toString(),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = if (diff == SweeperDiff.EASY) 12.sp else 10.sp,
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

        // Status footer
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = when (gameState) {
                    "won" -> "YOU WON! Tap heart to replay"
                    "lost" -> "BOOM! Tap heart to retry"
                    else -> "Tap cells to clear hearts"
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
