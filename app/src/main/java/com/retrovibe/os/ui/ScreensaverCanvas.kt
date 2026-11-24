package com.retrovibe.os.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.retrovibe.os.model.ScreensaverId
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

private data class Star(var x: Float, var y: Float, var z: Float)
private data class ToasterObj(var x: Float, var y: Float, val speed: Float, val isToast: Boolean, val phase: Float)
private data class PipeSeg(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Color)
private data class FloatHeart(var x: Float, var y: Float, val speed: Float, val size: Float, val color: Color)

@Composable
fun ScreensaverCanvas(
    screensaverId: ScreensaverId,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableStateOf(0L) }

    val stars = remember { List(90) { Star(Random.nextFloat() * 2f - 1f, Random.nextFloat() * 2f - 1f, Random.nextFloat().coerceAtLeast(0.05f)) } }
    val toasters = remember {
        List(11) { i ->
            ToasterObj(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speed = 0.004f + Random.nextFloat() * 0.004f,
                isToast = i % 3 == 0,
                phase = Random.nextFloat() * 6.28f
            )
        }
    }
    val hearts = remember {
        val cols = listOf(Color(0xFFFF85B3), Color(0xFFFFB7D5), Color(0xFFFF5C93), Color(0xFFFFD1E3), Color(0xFFC084FC))
        List(28) { i ->
            FloatHeart(Random.nextFloat(), Random.nextFloat(), 0.003f + Random.nextFloat() * 0.005f, 8f + (i % 4) * 5f, cols[i % cols.size])
        }
    }
    val pipeSegments = remember { mutableStateListOf<PipeSeg>() }
    var pipeX by remember { mutableStateOf(0.5f) }
    var pipeY by remember { mutableStateOf(0.5f) }
    var pipeDir by remember { mutableStateOf(0) }
    var pipeColor by remember { mutableStateOf(Color(0xFF00E5FF)) }
    val pipePalette = remember { listOf(Color(0xFF00E5FF), Color(0xFFFF4D8D), Color(0xFF39FF14), Color(0xFFFFD60A), Color(0xFFB388FF), Color(0xFFFF7A00)) }

    // 3D Maze state
    val mazeGrid = remember {
        arrayOf(
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1),
            intArrayOf(1, 0, 0, 0, 1, 0, 0, 1),
            intArrayOf(1, 0, 1, 0, 1, 0, 1, 1),
            intArrayOf(1, 0, 1, 0, 0, 0, 0, 1),
            intArrayOf(1, 0, 1, 1, 1, 1, 0, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1)
        )
    }
    var camX by remember { mutableStateOf(1.5f) }
    var camY by remember { mutableStateOf(1.5f) }
    var camAngle by remember { mutableStateOf(0f) }

    LaunchedEffect(screensaverId) {
        pipeSegments.clear()
        while (true) {
            delay(33L)
            tick++
            when (screensaverId) {
                ScreensaverId.STARFIELD -> {
                    stars.forEach { s ->
                        s.z -= 0.022f
                        if (s.z <= 0.03f) {
                            s.x = Random.nextFloat() * 2f - 1f
                            s.y = Random.nextFloat() * 2f - 1f
                            s.z = 1f
                        }
                    }
                }
                ScreensaverId.TOASTERS -> {
                    toasters.forEach { t ->
                        t.x -= t.speed
                        t.y += t.speed * 0.65f
                        if (t.x < -0.15f || t.y > 1.15f) {
                            t.x = 1.05f + Random.nextFloat() * 0.25f
                            t.y = -0.1f + Random.nextFloat() * 0.55f
                        }
                    }
                }
                ScreensaverId.HEARTS -> {
                    hearts.forEach { h ->
                        h.y -= h.speed
                        if (h.y < -0.1f) {
                            h.y = 1.1f
                            h.x = Random.nextFloat()
                        }
                    }
                }
                ScreensaverId.PIPES -> {
                    if (tick % 3L == 0L) {
                        if (pipeSegments.size > 160) pipeSegments.clear()
                        if (Random.nextFloat() < 0.28f) pipeDir = (pipeDir + if (Random.nextBoolean()) 1 else 3) % 4
                        val step = 0.06f
                        val nx = (pipeX + when (pipeDir) { 0 -> step; 2 -> -step; else -> 0f })
                        val ny = (pipeY + when (pipeDir) { 1 -> step; 3 -> -step; else -> 0f })
                        if (nx !in 0.05f..0.95f || ny !in 0.05f..0.95f) {
                            pipeX = Random.nextFloat() * 0.8f + 0.1f
                            pipeY = Random.nextFloat() * 0.8f + 0.1f
                            pipeColor = pipePalette.random()
                        } else {
                            pipeSegments.add(PipeSeg(pipeX, pipeY, nx, ny, pipeColor))
                            pipeX = nx
                            pipeY = ny
                        }
                    }
                }
                ScreensaverId.MAZE -> {
                    val nx = camX + cos(camAngle) * 0.045f
                    val ny = camY + sin(camAngle) * 0.045f
                    val gx = nx.toInt().coerceIn(0, 7)
                    val gy = ny.toInt().coerceIn(0, 6)
                    if (mazeGrid[gy][gx] == 0) {
                        camX = nx
                        camY = ny
                        camAngle += 0.008f
                    } else {
                        camAngle += 0.18f
                    }
                }
                ScreensaverId.NONE -> Unit
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val t = tick // subscribe to recomposition
        when (screensaverId) {
            ScreensaverId.NONE -> Unit
            ScreensaverId.STARFIELD -> {
                val cx = size.width / 2f
                val cy = size.height / 2f
                stars.forEach { s ->
                    val sx = cx + (s.x / s.z) * cx
                    val sy = cy + (s.y / s.z) * cy
                    if (sx in 0f..size.width && sy in 0f..size.height) {
                        val r = ((1f - s.z) * 3.5f).coerceAtLeast(1f)
                        val alpha = (1f - s.z).coerceIn(0.2f, 1f)
                        drawRect(Color.White.copy(alpha = alpha), Offset(sx, sy), Size(r, r))
                    }
                }
            }
            ScreensaverId.TOASTERS -> {
                toasters.forEach { obj ->
                    val px = obj.x * size.width
                    val py = obj.y * size.height
                    val s = (size.minDimension * 0.09f).coerceIn(14f, 42f)
                    if (obj.isToast) {
                        drawRect(Color(0xFF9C5A21), Offset(px, py), Size(s, s * 0.85f))
                        drawRect(Color(0xFFE6B566), Offset(px + s * 0.12f, py + s * 0.12f), Size(s * 0.76f, s * 0.62f))
                        drawRect(Color(0xFFFFF099), Offset(px + s * 0.35f, py + s * 0.3f), Size(s * 0.28f, s * 0.22f))
                    } else {
                        val wingUp = sin(t * 0.35 + obj.phase) > 0
                        val wy = if (wingUp) py - s * 0.45f else py + s * 0.2f
                        // Wings
                        drawRect(Color.White, Offset(px - s * 0.38f, wy), Size(s * 0.42f, s * 0.24f))
                        drawRect(Color.White, Offset(px + s * 0.86f, wy), Size(s * 0.42f, s * 0.24f))
                        // Chrome body
                        drawRect(Color(0xFFB8C0CC), Offset(px, py), Size(s, s * 0.78f))
                        drawRect(Color(0xFFE6ECF5), Offset(px + s * 0.1f, py + s * 0.1f), Size(s * 0.3f, s * 0.52f))
                        // Slots
                        drawRect(Color(0xFF222222), Offset(px + s * 0.2f, py + s * 0.08f), Size(s * 0.6f, s * 0.1f))
                        drawRect(Color(0xFF444444), Offset(px, py + s * 0.74f), Size(s, s * 0.12f))
                    }
                }
            }
            ScreensaverId.PIPES -> {
                val stroke = (size.minDimension * 0.035f).coerceIn(4f, 14f)
                pipeSegments.forEach { seg ->
                    val p1 = Offset(seg.x1 * size.width, seg.y1 * size.height)
                    val p2 = Offset(seg.x2 * size.width, seg.y2 * size.height)
                    drawLine(seg.color.copy(alpha = 0.45f), p1, p2, strokeWidth = stroke * 1.4f)
                    drawLine(seg.color, p1, p2, strokeWidth = stroke)
                    drawLine(Color.White.copy(alpha = 0.55f), p1, p2, strokeWidth = stroke * 0.28f)
                    drawCircle(seg.color, radius = stroke * 0.65f, center = p2)
                }
            }
            ScreensaverId.MAZE -> {
                // Ceiling & floor
                drawRect(Color(0xFF3A3A50), Offset.Zero, Size(size.width, size.height / 2f))
                drawRect(Color(0xFF55483A), Offset(0f, size.height / 2f), Size(size.width, size.height / 2f))
                val rays = 48
                val colW = size.width / rays
                val fov = 1.1f
                for (i in 0 until rays) {
                    val rayAngle = camAngle - fov / 2f + (i.toFloat() / rays) * fov
                    val dx = cos(rayAngle)
                    val dy = sin(rayAngle)
                    var dist = 0.1f
                    var hit = false
                    while (dist < 7f && !hit) {
                        val rx = (camX + dx * dist).toInt()
                        val ry = (camY + dy * dist).toInt()
                        if (ry !in mazeGrid.indices || rx !in mazeGrid[0].indices || mazeGrid[ry][rx] == 1) {
                            hit = true
                        } else {
                            dist += 0.08f
                        }
                    }
                    val corrDist = (dist * cos(rayAngle - camAngle)).coerceAtLeast(0.2f)
                    val wallH = (size.height / corrDist).coerceAtMost(size.height)
                    val shade = (1f - corrDist / 6.5f).coerceIn(0.2f, 1f)
                    val wallCol = Color((0xB8 * shade).toInt(), (0x3D * shade).toInt(), (0x2D * shade).toInt())
                    drawRect(wallCol, Offset(i * colW, (size.height - wallH) / 2f), Size(colW + 1f, wallH))
                }
            }
            ScreensaverId.HEARTS -> {
                hearts.forEach { h ->
                    val px = h.x * size.width
                    val py = h.y * size.height
                    val u = h.size / 4f
                    drawRect(h.color, Offset(px + u, py), Size(u, u))
                    drawRect(h.color, Offset(px + 3 * u, py), Size(u, u))
                    drawRect(h.color, Offset(px, py + u), Size(5 * u, u))
                    drawRect(h.color, Offset(px + u, py + 2 * u), Size(3 * u, u))
                    drawRect(h.color, Offset(px + 2 * u, py + 3 * u), Size(u, u))
                }
            }
        }
    }
}
