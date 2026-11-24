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
import androidx.compose.ui.graphics.StrokeCap
import com.retrovibe.os.model.ScreensaverId
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

private data class Star(var x: Float, var y: Float, var z: Float, var prevZ: Float, val tint: Color)
private data class ToasterObj(var x: Float, var y: Float, val speed: Float, val isToast: Boolean, val phase: Float)
private data class Pipe3DSeg(
    val x1: Float, val y1: Float, val z1: Float,
    val x2: Float, val y2: Float, val z2: Float,
    val color: Color,
    val isJoint: Boolean
)
private data class FloatHeart(var x: Float, var y: Float, val speed: Float, val size: Float, val color: Color, val hi: Color)

private fun darken(c: Color, f: Float) = Color(
    (c.red * f).coerceIn(0f, 1f),
    (c.green * f).coerceIn(0f, 1f),
    (c.blue * f).coerceIn(0f, 1f),
    c.alpha
)

private fun lighten(c: Color, f: Float) = Color(
    (c.red + (1f - c.red) * f).coerceIn(0f, 1f),
    (c.green + (1f - c.green) * f).coerceIn(0f, 1f),
    (c.blue + (1f - c.blue) * f).coerceIn(0f, 1f),
    c.alpha
)

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

    // True 3D Pipes state on a 3D lattice (-4..4, -4..4, 2.5..8.5)
    val pipeSegments = remember { mutableStateListOf<Pipe3DSeg>() }
    var px by remember { mutableStateOf(0) }
    var py by remember { mutableStateOf(0) }
    var pz by remember { mutableStateOf(5) }
    var pDir by remember { mutableStateOf(0) } // 0:+x, 1:-x, 2:+y, 3:-y, 4:+z, 5:-z
    var pipeColor by remember { mutableStateOf(Color(0xFF2288E8)) }
    val pipePalette = remember {
        listOf(
            Color(0xFF2488E8), // Cobalt
            Color(0xFFE84068), // Cherry
            Color(0xFF2EC866), // Emerald
            Color(0xFFF0BC28), // Gold
            Color(0xFFA078F0), // Violet
            Color(0xFFB8C6D8)  // Chrome Silver
        )
    }

    // Classic Win98 3D Brick Maze (1 = red brick, 2 = grey stone pillar)
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
        px = 0; py = 0; pz = 5; pDir = 0
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
                    if (tick % 2L == 0L) {
                        if (pipeSegments.size > 110) {
                            pipeSegments.clear()
                            px = Random.nextInt(-2, 3)
                            py = Random.nextInt(-2, 3)
                            pz = Random.nextInt(4, 8)
                            pipeColor = pipePalette.random()
                        }
                        val dirs = listOf(
                            Triple(1, 0, 0), Triple(-1, 0, 0),
                            Triple(0, 1, 0), Triple(0, -1, 0),
                            Triple(0, 0, 1), Triple(0, 0, -1)
                        )
                        val opposite = pDir xor 1
                        var turned = false
                        // Pick new perpendicular direction ~38% of the time or if hitting bounds
                        val (cdx, cdy, cdz) = dirs[pDir]
                        val inBounds = (px + cdx) in -4..4 && (py + cdy) in -3..3 && (pz + cdz) in 3..9
                        if (!inBounds || Random.nextFloat() < 0.38f) {
                            val candidates = (0..5).filter { d ->
                                if (d == opposite) return@filter false
                                val (dx, dy, dz) = dirs[d]
                                (px + dx) in -4..4 && (py + dy) in -3..3 && (pz + dz) in 3..9
                            }
                            if (candidates.isNotEmpty()) {
                                val nextDir = candidates.random()
                                turned = nextDir != pDir
                                pDir = nextDir
                            } else {
                                // Start a fresh pipe in a new color
                                px = Random.nextInt(-3, 4)
                                py = Random.nextInt(-2, 3)
                                pz = Random.nextInt(4, 8)
                                pipeColor = pipePalette.random()
                                continue
                            }
                        }
                        val (dx, dy, dz) = dirs[pDir]
                        val nx = px + dx
                        val ny = py + dy
                        val nz = pz + dz
                        pipeSegments.add(
                            Pipe3DSeg(
                                px.toFloat(), py.toFloat(), pz.toFloat(),
                                nx.toFloat(), ny.toFloat(), nz.toFloat(),
                                pipeColor,
                                isJoint = turned || pipeSegments.isEmpty()
                            )
                        )
                        px = nx; py = ny; pz = nz
                        // Start a brand-new colored pipe every ~28 segments
                        if (pipeSegments.size % 28 == 0) {
                            px = Random.nextInt(-3, 4)
                            py = Random.nextInt(-2, 3)
                            pz = Random.nextInt(4, 8)
                            pipeColor = pipePalette.random()
                        }
                    }
                }
                ScreensaverId.MAZE -> {
                    val diff = targetAngle - camAngle
                    if (abs(diff) > 0.05f) {
                        camAngle += diff.coerceIn(-0.11f, 0.11f)
                    } else {
                        camAngle = targetAngle
                        val step = 0.045f
                        val lookAhead = 0.48f
                        val ax = camX + cos(camAngle) * lookAhead
                        val ay = camY + sin(camAngle) * lookAhead
                        if (isOpen(ax, ay)) {
                            camX += cos(camAngle) * step
                            camY += sin(camAngle) * step
                        } else {
                            val rightA = targetAngle + (PI / 2.0).toFloat()
                            val leftA = targetAngle - (PI / 2.0).toFloat()
                            val rx = camX + cos(rightA) * 0.72f
                            val ry = camY + sin(rightA) * 0.72f
                            val lx = camX + cos(leftA) * 0.72f
                            val ly = camY + sin(leftA) * 0.72f
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
                    val prevX = cx + (s.x / s.prevZ) * cx
                    val prevY = cy + (s.y / s.prevZ) * cy
                    if (sx in 0f..size.width && sy in 0f..size.height) {
                        val r = ((1f - s.z) * 3.6f).coerceAtLeast(1.2f)
                        val alpha = (1f - s.z).coerceIn(0.25f, 1f)
                        drawLine(s.tint.copy(alpha = alpha * 0.65f), Offset(prevX, prevY), Offset(sx, sy), strokeWidth = r * 0.65f)
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
                        drawRect(Color(0xFF14101C), Offset(px - u, py - u), Size(s + 2 * u, s * 0.9f + 2 * u))
                        drawRect(Color(0xFF9C5420), Offset(px, py), Size(s, s * 0.9f))
                        drawRect(Color(0xFFC87A38), Offset(px + u * 0.6f, py + u * 0.6f), Size(s - u * 1.2f, s * 0.9f - u * 1.2f))
                        drawRect(Color(0xFFF2C878), Offset(px + u * 1.4f, py + u * 1.4f), Size(s - u * 2.8f, s * 0.9f - u * 2.8f))
                        drawRect(Color(0xFFFFEE88), Offset(px + u * 3.4f, py + u * 3.0f), Size(u * 3.2f, u * 2.4f))
                        drawRect(Color(0xFFFFFFFF), Offset(px + u * 3.6f, py + u * 3.2f), Size(u * 1.2f, u * 0.8f))
                    } else {
                        val flap = sin(t * 0.36 + obj.phase).toFloat()
                        val wy = py + (-flap * s * 0.36f)
                        drawRect(Color(0xFF101224), Offset(px - s * 0.48f, wy - u), Size(s * 0.52f, s * 0.34f))
                        drawRect(Color.White, Offset(px - s * 0.44f, wy - u * 0.4f), Size(s * 0.44f, s * 0.22f))
                        drawRect(Color(0xFFCCD6E4), Offset(px - s * 0.42f, wy + u * 1.1f), Size(s * 0.38f, s * 0.08f))

                        drawRect(Color(0xFF101224), Offset(px + s * 0.96f, wy - u), Size(s * 0.52f, s * 0.34f))
                        drawRect(Color.White, Offset(px + s * 1.0f, wy - u * 0.4f), Size(s * 0.44f, s * 0.22f))
                        drawRect(Color(0xFFCCD6E4), Offset(px + s * 1.02f, wy + u * 1.1f), Size(s * 0.38f, s * 0.08f))

                        drawRect(Color(0xFF101224), Offset(px - u, py - u), Size(s + 2 * u, s * 0.86f + 2 * u))
                        drawRect(Color(0xFFB4C0CE), Offset(px, py), Size(s, s * 0.82f))
                        drawRect(Color(0xFFEAF0F8), Offset(px + u, py + u * 0.6f), Size(s * 0.36f, s * 0.64f))
                        drawRect(Color.White, Offset(px + u * 1.5f, py + u), Size(u * 1.2f, s * 0.56f))
                        drawRect(Color(0xFF728092), Offset(px + s * 0.72f, py + u * 0.6f), Size(s * 0.26f, s * 0.68f))
                        drawRect(Color(0xFF1C1E2C), Offset(px + s * 0.18f, py + u * 0.6f), Size(s * 0.62f, u * 0.9f))
                        drawRect(Color(0xFFE85C88), Offset(px + s * 0.84f, py + s * 0.42f), Size(u * 1.4f, u * 1.2f))
                        drawRect(Color(0xFF282C38), Offset(px, py + s * 0.72f), Size(s, s * 0.14f))
                    }
                }
            }

            ScreensaverId.PIPES -> {
                // Perspective 3D projection with subtle slow camera orbit + depth sorting!
                val cx = size.width / 2f
                val cy = size.height / 2f
                val focal = size.minDimension * 0.78f
                val orbit = sin(t * 0.008) .toFloat() * 0.32f
                val cosO = cos(orbit)
                val sinO = sin(orbit)

                fun project(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
                    val rz = z - 6f
                    val rx = x * cosO - rz * sinO
                    val nz = (x * sinO + rz * cosO) + 6.4f
                    val invZ = focal / nz.coerceAtLeast(1.2f)
                    return Triple(cx + rx * invZ, cy + y * invZ, nz)
                }

                // Painter's algorithm: sort farthest-to-nearest by average Z!
                val sorted = pipeSegments.sortedByDescending { (it.z1 + it.z2) / 2f }
                sorted.forEach { seg ->
                    val (sx1, sy1, z1) = project(seg.x1, seg.y1, seg.z1)
                    val (sx2, sy2, z2) = project(seg.x2, seg.y2, seg.z2)
                    val avgZ = ((z1 + z2) / 2f).coerceIn(2f, 11f)
                    val depthShade = (1.15f - avgZ / 13f).coerceIn(0.38f, 1f)
                    val radius = (focal * 0.24f / avgZ).coerceIn(3.5f, 22f)

                    val p1 = Offset(sx1, sy1)
                    val p2 = Offset(sx2, sy2)
                    val base = darken(seg.color, depthShade)
                    val shadow = darken(base, 0.42f)
                    val hi = lighten(base, 0.58f)

                    // 3D cylindrical multi-band shading (shadow -> base -> glossy specular)
                    drawLine(shadow, p1, p2, strokeWidth = radius * 2.15f, cap = StrokeCap.Round)
                    drawLine(base, p1, p2, strokeWidth = radius * 1.65f, cap = StrokeCap.Round)
                    // Specular highlight shifted slightly up-left for 3D roundness
                    val off = Offset(-radius * 0.28f, -radius * 0.28f)
                    drawLine(hi, p1 + off, p2 + off, strokeWidth = radius * 0.55f, cap = StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.65f * depthShade), p1 + off * 1.1f, p2 + off * 1.1f, strokeWidth = radius * 0.22f, cap = StrokeCap.Round)

                    // Glossy 3D spherical ball joint at corners!
                    if (seg.isJoint) {
                        val jr = radius * 1.28f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, hi, base, shadow),
                                center = Offset(p1.x - jr * 0.32f, p1.y - jr * 0.32f),
                                radius = jr * 1.35f
                            ),
                            radius = jr,
                            center = p1
                        )
                    }
                    // End cap sphere
                    val er = radius * 1.05f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(hi, base, shadow),
                            center = Offset(p2.x - er * 0.3f, p2.y - er * 0.3f),
                            radius = er * 1.3f
                        ),
                        radius = er,
                        center = p2
                    )
                }
            }

            ScreensaverId.MAZE -> {
                val halfH = size.height / 2f
                // Classic Win98 ceiling & wood-tile floor depth gradients
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF4C4664), Color(0xFF1A1826)), 0f, halfH),
                    topLeft = Offset.Zero,
                    size = Size(size.width, halfH)
                )
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF282018), Color(0xFF725E48)), halfH, size.height),
                    topLeft = Offset(0f, halfH),
                    size = Size(size.width, halfH)
                )
                // Perspective floor plank lines
                for (row in 1..6) {
                    val fy = halfH + (halfH * (row * row) / 36f)
                    drawLine(Color(0x36000000), Offset(0f, fy), Offset(size.width, fy), strokeWidth = 1.2f)
                }

                // True Distortion-Free Camera-Plane DDA Raycaster (NO fisheye / NO stretching!)
                val dirX = cos(camAngle)
                val dirY = sin(camAngle)
                // Keep FOV proportional to aspect ratio so walls look 1:1 in BOTH monitor preview & fullscreen!
                val aspect = (size.width / size.height.coerceAtLeast(1f)).coerceIn(0.55f, 1.8f)
                val planeMag = 0.62f * sqrt(aspect)
                val planeX = -dirY * planeMag
                val planeY = dirX * planeMag
                val wallScale = size.height * (0.88f / sqrt(aspect))

                val rays = 76
                val colW = size.width / rays
                val brickRows = 8

                for (i in 0 until rays) {
                    val cameraX = 2f * i / rays - 1f // -1..+1 across screen
                    val rayDirX = dirX + planeX * cameraX
                    val rayDirY = dirY + planeY * cameraX

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

                    // Exact perpendicular wall distance -> 100% straight, zero fisheye!
                    val perpDist = (if (side == 0) sideDistX - deltaDistX else sideDistY - deltaDistY).coerceAtLeast(0.18f)
                    val wallH = (wallScale / perpDist).coerceAtMost(size.height * 2.2f)
                    val wallTop = halfH - wallH / 2f

                    var wallX = if (side == 0) camY + perpDist * rayDirY else camX + perpDist * rayDirX
                    wallX -= floor(wallX)

                    val distShade = (1f - perpDist / 7.5f).coerceIn(0.24f, 1f)
                    val sideMul = if (side == 1) 0.74f else 1.0f
                    val shade = distShade * sideMul

                    val bandH = wallH / brickRows
                    val isStone = hitTile == 2
                    for (b in 0 until brickRows) {
                        val by = wallTop + b * bandH
                        if (by + bandH < 0f || by > size.height) continue
                        val staggeredU = (wallX * 2f + (if (b % 2 == 0) 0f else 0.5f)) % 1f
                        val isVerticalMortar = staggeredU < 0.08f
                        val brickTint = if (((b * 3 + (wallX * 4).toInt()) % 3) == 0) 0.90f else 1.0f
                        val baseCol = when {
                            isVerticalMortar -> Color((0xC8 * shade).toInt(), (0xB8 * shade).toInt(), (0xA4 * shade).toInt())
                            isStone -> Color((0x8A * shade * brickTint).toInt(), (0x98 * shade * brickTint).toInt(), (0xB4 * shade * brickTint).toInt())
                            else -> Color((0xC4 * shade * brickTint).toInt(), (0x46 * shade * brickTint).toInt(), (0x34 * shade * brickTint).toInt())
                        }
                        drawRect(baseCol, Offset(i * colW, by), Size(colW + 1f, bandH + 0.5f))
                        val mortarCol = Color((0xB4 * shade).toInt(), (0xA4 * shade).toInt(), (0x92 * shade).toInt())
                        drawRect(mortarCol, Offset(i * colW, by), Size(colW + 1f, (bandH * 0.13f).coerceAtLeast(1f)))
                    }
                    // Crisp top & bottom trim borders
                    drawRect(Color(0xFF181418).copy(alpha = 0.6f), Offset(i * colW, wallTop), Size(colW + 1f, 2f))
                    drawRect(Color(0xFF181418).copy(alpha = 0.6f), Offset(i * colW, wallTop + wallH - 2f), Size(colW + 1f, 2f))
                }
            }

            ScreensaverId.HEARTS -> {
                hearts.forEach { h ->
                    val px = h.x * size.width
                    val py = h.y * size.height
                    val u = h.size / 6f
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
