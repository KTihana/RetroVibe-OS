package com.retrovibe.os.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.retrovibe.os.model.ScreensaverId
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

private data class Star(var x: Float, var y: Float, var z: Float, var prevZ: Float, val tint: Color)
private data class ToasterObj(var x: Float, var y: Float, val speed: Float, val isToast: Boolean, val phase: Float)
private data class PipeSeg(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Color)
private data class FloatHeart(var x: Float, var y: Float, val speed: Float, val size: Float, val color: Color, val hi: Color)

@Composable
fun ScreensaverCanvas(
    screensaverId: ScreensaverId,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableStateOf(0L) }

    val stars = remember {
        val tints = listOf(Color.White, Color(0xFFC8E4FF), Color(0xFFFFD4E6), Color(0xFFFFF4C8))
        List(100) { i ->
            val z = Random.nextFloat().coerceAtLeast(0.06f)
            Star(Random.nextFloat() * 2f - 1f, Random.nextFloat() * 2f - 1f, z, z, tints[i % tints.size])
        }
    }
    val toasters = remember {
        List(10) { i ->
            ToasterObj(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speed = 0.0042f + Random.nextFloat() * 0.0038f,
                isToast = i % 3 == 0,
                phase = Random.nextFloat() * 6.28f
            )
        }
    }
    val hearts = remember {
        val pairs = listOf(
            Color(0xFFF6729A) to Color(0xFFFFC6D8),
            Color(0xFFE04878) to Color(0xFFFFA8C2),
            Color(0xFFC084FC) to Color(0xFFEBD4FF),
            Color(0xFF64B5F6) to Color(0xFFBBDEFB)
        )
        List(24) { i ->
            val (main, hi) = pairs[i % pairs.size]
            FloatHeart(Random.nextFloat(), Random.nextFloat(), 0.003f + Random.nextFloat() * 0.0045f, 10f + (i % 4) * 5f, main, hi)
        }
    }
    val pipeSegments = remember { mutableStateListOf<PipeSeg>() }
    var pipeX by remember { mutableStateOf(0.5f) }
    var pipeY by remember { mutableStateOf(0.5f) }
    var pipeDir by remember { mutableStateOf(0) }
    var pipeColor by remember { mutableStateOf(Color(0xFF28D8F0)) }
    val pipePalette = remember {
        listOf(
            Color(0xFF28D8F0), Color(0xFFF46898), Color(0xFF4EE072),
            Color(0xFFF8D048), Color(0xFFA884F8), Color(0xFFFF8844)
        )
    }

    // Classic Win98 3D Brick Maze (1 = brick wall, 2 = stone accent pillar)
    val mazeGrid = remember {
        arrayOf(
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
            intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 0, 1),
            intArrayOf(1, 0, 2, 0, 1, 0, 1, 2, 0, 1),
            intArrayOf(1, 0, 1, 0, 0, 0, 1, 0, 0, 1),
            intArrayOf(1, 0, 1, 1, 2, 0, 1, 0, 1, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(1, 1, 2, 1, 1, 0, 1, 1, 0, 1),
            intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
        )
    }
    var camX by remember { mutableStateOf(1.5f) }
    var camY by remember { mutableStateOf(1.5f) }
    var camAngle by remember { mutableStateOf(0f) }
    var targetAngle by remember { mutableStateOf(0f) }

    fun isOpen(x: Float, y: Float): Boolean {
        val gx = x.toInt()
        val gy = y.toInt()
        return gy in mazeGrid.indices && gx in mazeGrid[0].indices && mazeGrid[gy][gx] == 0
    }

    LaunchedEffect(screensaverId) {
        pipeSegments.clear()
        while (true) {
            delay(33L)
            tick++
            when (screensaverId) {
                ScreensaverId.STARFIELD -> {
                    stars.forEach { s ->
                        s.prevZ = s.z
                        s.z -= 0.024f
                        if (s.z <= 0.04f) {
                            s.x = Random.nextFloat() * 2f - 1f
                            s.y = Random.nextFloat() * 2f - 1f
                            s.z = 1f
                            s.prevZ = 1f
                        }
                    }
                }
                ScreensaverId.TOASTERS -> {
                    toasters.forEach { t ->
                        t.x -= t.speed
                        t.y += t.speed * 0.62f
                        if (t.x < -0.18f || t.y > 1.18f) {
                            t.x = 1.06f + Random.nextFloat() * 0.25f
                            t.y = -0.12f + Random.nextFloat() * 0.55f
                        }
                    }
                }
                ScreensaverId.HEARTS -> {
                    hearts.forEach { h ->
                        h.y -= h.speed
                        if (h.y < -0.12f) {
                            h.y = 1.12f
                            h.x = Random.nextFloat()
                        }
                    }
                }
                ScreensaverId.PIPES -> {
                    if (tick % 3L == 0L) {
                        if (pipeSegments.size > 150) pipeSegments.clear()
                        if (Random.nextFloat() < 0.28f) pipeDir = (pipeDir + if (Random.nextBoolean()) 1 else 3) % 4
                        val step = 0.065f
                        val nx = pipeX + when (pipeDir) { 0 -> step; 2 -> -step; else -> 0f }
                        val ny = pipeY + when (pipeDir) { 1 -> step; 3 -> -step; else -> 0f }
                        if (nx !in 0.06f..0.94f || ny !in 0.06f..0.94f) {
                            pipeX = Random.nextFloat() * 0.76f + 0.12f
                            pipeY = Random.nextFloat() * 0.76f + 0.12f
                            pipeColor = pipePalette.random()
                        } else {
                            pipeSegments.add(PipeSeg(pipeX, pipeY, nx, ny, pipeColor))
                            pipeX = nx
                            pipeY = ny
                        }
                    }
                }
                ScreensaverId.MAZE -> {
                    // Smoothly turn toward targetAngle, then glide forward through corridors
                    val diff = targetAngle - camAngle
                    if (abs(diff) > 0.05f) {
                        camAngle += diff.coerceIn(-0.12f, 0.12f)
                    } else {
                        camAngle = targetAngle
                        val step = 0.048f
                        val lookAhead = 0.42f
                        val ax = camX + cos(camAngle) * lookAhead
                        val ay = camY + sin(camAngle) * lookAhead
                        if (isOpen(ax, ay)) {
                            camX += cos(camAngle) * step
                            camY += sin(camAngle) * step
                        } else {
                            // Choose an open 90-degree turn (prefer right, then left, else U-turn)
                            val rightA = targetAngle + (PI / 2.0).toFloat()
                            val leftA = targetAngle - (PI / 2.0).toFloat()
                            val rx = camX + cos(rightA) * 0.7f
                            val ry = camY + sin(rightA) * 0.7f
                            val lx = camX + cos(leftA) * 0.7f
                            val ly = camY + sin(leftA) * 0.7f
                            targetAngle = when {
                                isOpen(rx, ry) && (!isOpen(lx, ly) || Random.nextBoolean()) -> rightA
                                isOpen(lx, ly) -> leftA
                                else -> targetAngle + PI.toFloat()
                            }
                        }
                    }
                }
                ScreensaverId.NONE -> Unit
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF060812))
    ) {
        val t = tick
        when (screensaverId) {
            ScreensaverId.NONE -> Unit

            ScreensaverId.STARFIELD -> {
                val cx = size.width / 2f
                val cy = size.height / 2f
                stars.forEach { s ->
                    val sx = cx + (s.x / s.z) * cx
                    val sy = cy + (s.y / s.z) * cy
                    val px = cx + (s.x / s.prevZ) * cx
                    val py = cy + (s.y / s.prevZ) * cy
                    if (sx in 0f..size.width && sy in 0f..size.height) {
                        val r = ((1f - s.z) * 3.6f).coerceAtLeast(1.2f)
                        val alpha = (1f - s.z).coerceIn(0.25f, 1f)
                        drawLine(s.tint.copy(alpha = alpha * 0.65f), Offset(px, py), Offset(sx, sy), strokeWidth = r * 0.65f)
                        drawRect(s.tint.copy(alpha = alpha), Offset(sx - r / 2f, sy - r / 2f), Size(r, r))
                    }
                }
            }

            ScreensaverId.TOASTERS -> {
                toasters.forEach { obj ->
                    val px = obj.x * size.width
                    val py = obj.y * size.height
                    val s = (size.minDimension * 0.11f).coerceIn(14f, 44f)
                    val u = s / 10f
                    if (obj.isToast) {
                        // Crisp outlined pixel toast with crust, golden bread & melted butter!
                        drawRect(Color(0xFF14101C), Offset(px - u, py - u), Size(s + 2 * u, s * 0.9f + 2 * u))
                        drawRect(Color(0xFF9C5420), Offset(px, py), Size(s, s * 0.9f))
                        drawRect(Color(0xFFC87A38), Offset(px + u * 0.6f, py + u * 0.6f), Size(s - u * 1.2f, s * 0.9f - u * 1.2f))
                        drawRect(Color(0xFFF2C878), Offset(px + u * 1.4f, py + u * 1.4f), Size(s - u * 2.8f, s * 0.9f - u * 2.8f))
                        // Melted butter pat
                        drawRect(Color(0xFFFFEE88), Offset(px + u * 3.4f, py + u * 3.0f), Size(u * 3.2f, u * 2.4f))
                        drawRect(Color(0xFFFFFFFF), Offset(px + u * 3.6f, py + u * 3.2f), Size(u * 1.2f, u * 0.8f))
                    } else {
                        val flap = sin(t * 0.36 + obj.phase).toFloat()
                        val wy = py + (-flap * s * 0.36f)
                        // Feathered white pixel wings with dark outline & grey shadow
                        drawRect(Color(0xFF101224), Offset(px - s * 0.48f, wy - u), Size(s * 0.52f, s * 0.34f))
                        drawRect(Color.White, Offset(px - s * 0.44f, wy - u * 0.4f), Size(s * 0.44f, s * 0.22f))
                        drawRect(Color(0xFFCCD6E4), Offset(px - s * 0.42f, wy + u * 1.1f), Size(s * 0.38f, s * 0.08f))

                        drawRect(Color(0xFF101224), Offset(px + s * 0.96f, wy - u), Size(s * 0.52f, s * 0.34f))
                        drawRect(Color.White, Offset(px + s * 1.0f, wy - u * 0.4f), Size(s * 0.44f, s * 0.22f))
                        drawRect(Color(0xFFCCD6E4), Offset(px + s * 1.02f, wy + u * 1.1f), Size(s * 0.38f, s * 0.08f))

                        // Outlined chrome toaster body with metallic highlight + shadow
                        drawRect(Color(0xFF101224), Offset(px - u, py - u), Size(s + 2 * u, s * 0.86f + 2 * u))
                        drawRect(Color(0xFFB4C0CE), Offset(px, py), Size(s, s * 0.82f))
                        drawRect(Color(0xFFEAF0F8), Offset(px + u, py + u * 0.6f), Size(s * 0.36f, s * 0.64f))
                        drawRect(Color.White, Offset(px + u * 1.5f, py + u), Size(u * 1.2f, s * 0.56f))
                        drawRect(Color(0xFF728092), Offset(px + s * 0.72f, py + u * 0.6f), Size(s * 0.26f, s * 0.68f))
                        // Top toast slots + lever + black base
                        drawRect(Color(0xFF1C1E2C), Offset(px + s * 0.18f, py + u * 0.6f), Size(s * 0.62f, u * 0.9f))
                        drawRect(Color(0xFFE85C88), Offset(px + s * 0.84f, py + s * 0.42f), Size(u * 1.4f, u * 1.2f))
                        drawRect(Color(0xFF282C38), Offset(px, py + s * 0.72f), Size(s, s * 0.14f))
                    }
                }
            }

            ScreensaverId.PIPES -> {
                val stroke = (size.minDimension * 0.042f).coerceIn(5f, 16f)
                pipeSegments.forEach { seg ->
                    val p1 = Offset(seg.x1 * size.width, seg.y1 * size.height)
                    val p2 = Offset(seg.x2 * size.width, seg.y2 * size.height)
                    // Dark outline -> base pipe -> mid tint -> glossy specular stripe
                    drawLine(Color(0xFF0A0C18), p1, p2, strokeWidth = stroke * 1.38f)
                    drawLine(seg.color.copy(alpha = 0.75f), p1, p2, strokeWidth = stroke)
                    drawLine(seg.color, p1, p2, strokeWidth = stroke * 0.74f)
                    drawLine(Color.White.copy(alpha = 0.72f), p1, p2, strokeWidth = stroke * 0.24f)
                    // 3D elbow ball joint
                    drawCircle(Color(0xFF0A0C18), radius = stroke * 0.78f, center = p2)
                    drawCircle(seg.color, radius = stroke * 0.62f, center = p2)
                    drawCircle(Color.White.copy(alpha = 0.7f), radius = stroke * 0.24f, center = Offset(p2.x - stroke * 0.18f, p2.y - stroke * 0.18f))
                }
            }

            ScreensaverId.MAZE -> {
                val halfH = size.height / 2f
                // Classic Win98 ceiling & floor depth gradients
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF4A4260), Color(0xFF1C1A28)), 0f, halfH),
                    topLeft = Offset.Zero,
                    size = Size(size.width, halfH)
                )
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF2A221C), Color(0xFF6B5844)), halfH, size.height),
                    topLeft = Offset(0f, halfH),
                    size = Size(size.width, halfH)
                )
                // Perspective floor tile lines
                for (row in 1..6) {
                    val fy = halfH + (halfH * (row * row) / 36f)
                    drawLine(Color(0x38000000), Offset(0f, fy), Offset(size.width, fy), strokeWidth = 1.5f)
                }

                // True DDA Raycaster with procedural Win98 brick & stone wall textures!
                val rays = 72
                val colW = size.width / rays
                val fov = 1.12f
                val brickRows = 8

                for (i in 0 until rays) {
                    val rayAngle = camAngle - fov / 2f + (i.toFloat() / rays) * fov
                    val rayDirX = cos(rayAngle)
                    val rayDirY = sin(rayAngle)

                    var mapX = camX.toInt()
                    var mapY = camY.toInt()
                    val deltaDistX = if (abs(rayDirX) < 1e-6f) 1e6f else abs(1f / rayDirX)
                    val deltaDistY = if (abs(rayDirY) < 1e-6f) 1e6f else abs(1f / rayDirY)

                    val stepX: Int
                    var sideDistX: Float
                    if (rayDirX < 0f) {
                        stepX = -1; sideDistX = (camX - mapX) * deltaDistX
                    } else {
                        stepX = 1; sideDistX = (mapX + 1f - camX) * deltaDistX
                    }
                    val stepY: Int
                    var sideDistY: Float
                    if (rayDirY < 0f) {
                        stepY = -1; sideDistY = (camY - mapY) * deltaDistY
                    } else {
                        stepY = 1; sideDistY = (mapY + 1f - camY) * deltaDistY
                    }

                    var hitTile = 0
                    var side = 0
                    var steps = 0
                    while (hitTile == 0 && steps < 24) {
                        if (sideDistX < sideDistY) {
                            sideDistX += deltaDistX
                            mapX += stepX
                            side = 0
                        } else {
                            sideDistY += deltaDistY
                            mapY += stepY
                            side = 1
                        }
                        if (mapY !in mazeGrid.indices || mapX !in mazeGrid[0].indices) {
                            hitTile = 1
                        } else if (mazeGrid[mapY][mapX] > 0) {
                            hitTile = mazeGrid[mapY][mapX]
                        }
                        steps++
                    }

                    val perpDist = (if (side == 0) sideDistX - deltaDistX else sideDistY - deltaDistY).coerceAtLeast(0.15f)
                    val wallH = (size.height / perpDist).coerceAtMost(size.height * 1.4f)
                    val wallTop = (size.height - wallH) / 2f

                    // Texture U coordinate (0..1 along wall face)
                    var wallX = if (side == 0) camY + perpDist * rayDirY else camX + perpDist * rayDirX
                    wallX -= floor(wallX)

                    val distShade = (1f - perpDist / 7.5f).coerceIn(0.22f, 1f)
                    val sideMul = if (side == 1) 0.76f else 1.0f
                    val shade = distShade * sideMul

                    // Draw textured brick/stone bands vertically along this ray column!
                    val bandH = wallH / brickRows
                    val isStone = hitTile == 2
                    for (b in 0 until brickRows) {
                        val by = wallTop + b * bandH
                        val staggeredU = (wallX * 2f + (if (b % 2 == 0) 0f else 0.5f)) % 1f
                        val isVerticalMortar = staggeredU < 0.08f
                        val brickTint = if (((b * 3 + (wallX * 4).toInt()) % 3) == 0) 0.90f else 1.0f
                        val baseCol = when {
                            isVerticalMortar -> Color((0xC8 * shade).toInt(), (0xB8 * shade).toInt(), (0xA4 * shade).toInt())
                            isStone -> Color((0x8A * shade * brickTint).toInt(), (0x98 * shade * brickTint).toInt(), (0xB4 * shade * brickTint).toInt())
                            else -> Color((0xC4 * shade * brickTint).toInt(), (0x46 * shade * brickTint).toInt(), (0x34 * shade * brickTint).toInt())
                        }
                        drawRect(baseCol, Offset(i * colW, by), Size(colW + 1f, bandH + 0.5f))
                        // Horizontal mortar line between brick rows
                        val mortarCol = Color((0xB4 * shade).toInt(), (0xA4 * shade).toInt(), (0x92 * shade).toInt())
                        drawRect(mortarCol, Offset(i * colW, by), Size(colW + 1f, (bandH * 0.14f).coerceAtLeast(1f)))
                    }
                    // Crisp top & bottom trim border on wall
                    drawRect(Color(0xFF181418).copy(alpha = 0.65f), Offset(i * colW, wallTop), Size(colW + 1f, 2f))
                    drawRect(Color(0xFF181418).copy(alpha = 0.65f), Offset(i * colW, wallTop + wallH - 2f), Size(colW + 1f, 2f))
                }
            }

            ScreensaverId.HEARTS -> {
                hearts.forEach { h ->
                    val px = h.x * size.width
                    val py = h.y * size.height
                    val u = h.size / 6f
                    // Crisp dark outline + shaded pink heart + glossy highlight pixel
                    val pts = listOf(
                        1 to 0, 2 to 0, 4 to 0, 5 to 0,
                        0 to 1, 1 to 1, 2 to 1, 3 to 1, 4 to 1, 5 to 1, 6 to 1,
                        0 to 2, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 6 to 2,
                        1 to 3, 2 to 3, 3 to 3, 4 to 3, 5 to 3,
                        2 to 4, 3 to 4, 4 to 4,
                        3 to 5
                    )
                    pts.forEach { (gx, gy) ->
                        val col = when {
                            gx == 1 && gy == 1 -> Color.White
                            gx <= 2 && gy <= 2 -> h.hi
                            else -> h.color
                        }
                        drawRect(col, Offset(px + gx * u, py + gy * u), Size(u + 0.5f, u + 0.5f))
                    }
                }
            }
        }
    }
}
