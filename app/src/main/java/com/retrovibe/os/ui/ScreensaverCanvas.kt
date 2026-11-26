package com.retrovibe.os.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.retrovibe.os.model.ScreensaverId
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

private data class Star(var x: Float, var y: Float, var z: Float, var prevZ: Float, val tint: Color)
private data class ToasterObj(
    var x: Float,
    var y: Float,
    val depth: Float,      // 0.65 (far) .. 1.30 (near)
    val speed: Float,
    val isToast: Boolean,
    val toastiness: Int,   // 0 = light, 1 = golden, 2 = crispy
    val phase: Float
)
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
        List(13) { i ->
            val depth = 0.68f + (i % 5) * 0.14f + Random.nextFloat() * 0.08f
            ToasterObj(
                x = Random.nextFloat() * 1.35f - 0.15f,
                y = Random.nextFloat() * 1.25f - 0.15f,
                depth = depth,
                speed = (0.0034f + Random.nextFloat() * 0.0022f) * depth,
                isToast = i % 3 == 0,
                toastiness = i % 3,
                phase = Random.nextFloat() * 6.283f
            )
        }
    }
    val skyStars = remember {
        List(45) { i ->
            Triple(Random.nextFloat(), Random.nextFloat(), (i % 5) * 0.8f + Random.nextFloat())
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
                        t.y += t.speed * 0.58f
                        if (t.x < -0.22f || t.y > 1.22f) {
                            // Spawn evenly along right edge OR top edge so the flock never clumps
                            if (Random.nextBoolean()) {
                                t.x = 1.08f + Random.nextFloat() * 0.22f
                                t.y = -0.10f + Random.nextFloat() * 0.65f
                            } else {
                                t.x = 0.25f + Random.nextFloat() * 0.90f
                                t.y = -0.16f - Random.nextFloat() * 0.14f
                            }
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
                // Subtle twinkling deep-space stars behind the flock
                skyStars.forEach { (sx, sy, ph) ->
                    val tw = (0.35f + 0.65f * ((sin(t * 0.08 + ph).toFloat() + 1f) / 2f))
                    val starSize = (size.minDimension * 0.0055f).coerceIn(1.5f, 3.2f)
                    drawRect(
                        Color(0xFFDDE8FF).copy(alpha = tw * 0.55f),
                        Offset(sx * size.width, sy * size.height),
                        Size(starSize, starSize)
                    )
                }

                // Draw farthest-to-nearest for true parallax depth
                val sortedFlock = toasters.sortedBy { it.depth }
                sortedFlock.forEach { obj ->
                    val baseSize = (size.minDimension * 0.135f * obj.depth).coerceIn(18f, 68f)
                    val s = baseSize
                    val strokeW = (s * 0.045f).coerceIn(1.2f, 2.8f)
                    val px = obj.x * size.width
                    val bob = if (obj.isToast) sin(t * 0.14 + obj.phase).toFloat() * s * 0.06f
                              else sin(t * 0.28 + obj.phase).toFloat() * s * 0.09f
                    val py = obj.y * size.height + bob

                    if (obj.isToast) {
                        // Classic domed slice of buttered toast (3 toastiness shades)
                        val (crustCol, crumbTop, crumbBot) = when (obj.toastiness) {
                            0 -> Triple(Color(0xFF9C5E26), Color(0xFFFBE2A8), Color(0xFFE7BC72))
                            1 -> Triple(Color(0xFF7C4214), Color(0xFFE8B464), Color(0xFFC88636))
                            else -> Triple(Color(0xFF54280B), Color(0xFFC68036), Color(0xFF9E5A1E))
                        }
                        val tiltDeg = -14f + sin(t * 0.12 + obj.phase).toFloat() * 5f
                        val tw = s * 0.88f
                        val th = s * 0.86f
                        rotate(tiltDeg, pivot = Offset(px + tw / 2f, py + th / 2f)) {
                            fun toastPath(inset: Float): Path {
                                val l = px + inset
                                val r = px + tw - inset
                                val top = py + inset
                                val bot = py + th - inset
                                val w = r - l
                                val h = bot - top
                                val neckY = top + h * 0.34f
                                return Path().apply {
                                    // Domed bread top
                                    moveTo(l + w * 0.06f, neckY)
                                    cubicTo(l - w * 0.06f, top + h * 0.04f, l + w * 0.25f, top, l + w * 0.50f, top)
                                    cubicTo(l + w * 0.75f, top, r + w * 0.06f, top + h * 0.04f, r - w * 0.06f, neckY)
                                    // Tapered lower slice body
                                    lineTo(r - w * 0.09f, bot - h * 0.08f)
                                    quadraticTo(r - w * 0.09f, bot, r - w * 0.20f, bot)
                                    lineTo(l + w * 0.20f, bot)
                                    quadraticTo(l + w * 0.09f, bot, l + w * 0.09f, bot - h * 0.08f)
                                    close()
                                }
                            }

                            // Crust + dark outline
                            val outer = toastPath(0f)
                            drawPath(outer, crustCol)
                            drawPath(outer, Color(0xFF1A0E08), style = Stroke(width = strokeW))

                            // Golden crumb interior
                            val inner = toastPath(s * 0.085f)
                            drawPath(
                                inner,
                                Brush.verticalGradient(
                                    listOf(crumbTop, crumbBot),
                                    startY = py,
                                    endY = py + th
                                )
                            )

                            // Melting butter pat + glossy highlight
                            val bx = px + tw * 0.34f
                            val by = py + th * 0.36f
                            val bw = tw * 0.34f
                            val bh = th * 0.28f
                            drawRoundRect(
                                Color(0xFFE0A420),
                                Offset(bx + s * 0.02f, by + s * 0.03f),
                                Size(bw, bh),
                                CornerRadius(s * 0.07f, s * 0.07f)
                            )
                            drawRoundRect(
                                Color(0xFFFFF08A),
                                Offset(bx, by),
                                Size(bw, bh),
                                CornerRadius(s * 0.07f, s * 0.07f)
                            )
                            drawRoundRect(
                                Color.White.copy(alpha = 0.85f),
                                Offset(bx + bw * 0.14f, by + bh * 0.16f),
                                Size(bw * 0.42f, bh * 0.26f),
                                CornerRadius(s * 0.04f, s * 0.04f)
                            )
                        }
                    } else {
                        // Iconic After Dark Flying Chrome Toaster with articulated feathered wings!
                        val flap = sin(t * 0.28 + obj.phase).toFloat() // -1 (wings up) .. +1 (wings down)
                        val bodyW = s * 1.05f
                        val bodyH = s * 0.78f

                        fun drawFeatheredWing(rootX: Float, rootY: Float, dirX: Float, wingScale: Float, isBack: Boolean) {
                            val wingLen = s * 0.92f * wingScale
                            val tipLift = -flap * s * 0.62f * wingScale - s * 0.18f * wingScale
                            val midLift = tipLift * 0.55f - s * 0.14f * wingScale

                            val tipX = rootX + dirX * wingLen
                            val tipY = rootY + tipLift
                            val leadCtrlX = rootX + dirX * wingLen * 0.52f
                            val leadCtrlY = rootY + midLift - s * 0.16f * wingScale

                            val wingPath = Path().apply {
                                moveTo(rootX, rootY)
                                // Smooth arched leading edge
                                quadraticTo(leadCtrlX, leadCtrlY, tipX, tipY)
                                // 3 sculpted feather scallops along trailing edge
                                val f1x = rootX + dirX * wingLen * 0.76f
                                val f1y = tipY + s * 0.20f * wingScale + flap * s * 0.06f
                                val f2x = rootX + dirX * wingLen * 0.50f
                                val f2y = rootY + midLift + s * 0.26f * wingScale + flap * s * 0.08f
                                val f3x = rootX + dirX * wingLen * 0.24f
                                val f3y = rootY + s * 0.24f * wingScale
                                quadraticTo(tipX - dirX * s * 0.06f, f1y, f1x, f1y - s * 0.04f)
                                quadraticTo(f1x - dirX * s * 0.08f, f2y, f2x, f2y - s * 0.04f)
                                quadraticTo(f2x - dirX * s * 0.08f, f3y + s * 0.05f, f3x, f3y)
                                lineTo(rootX, rootY + s * 0.16f * wingScale)
                                close()
                            }

                            val topTint = if (isBack) Color(0xFFD6E0EE) else Color(0xFFFFFFFF)
                            val botTint = if (isBack) Color(0xFF8C9CB4) else Color(0xFFB8C8DE)
                            drawPath(
                                wingPath,
                                Brush.verticalGradient(
                                    listOf(topTint, botTint),
                                    startY = minOf(rootY, tipY) - s * 0.15f,
                                    endY = maxOf(rootY, tipY) + s * 0.35f
                                )
                            )
                            drawPath(wingPath, Color(0xFF141828), style = Stroke(width = strokeW * 0.9f))
                        }

                        // 1) Back wing (behind toaster body, slightly offset up-right)
                        drawFeatheredWing(
                            rootX = px + bodyW * 0.62f,
                            rootY = py + bodyH * 0.10f,
                            dirX = 1f,
                            wingScale = 0.86f,
                            isBack = true
                        )

                        // 2) Dark Bakelite base & feet
                        drawRoundRect(
                            Color(0xFF1B1E2A),
                            Offset(px + bodyW * 0.04f, py + bodyH * 0.80f),
                            Size(bodyW * 0.92f, bodyH * 0.22f),
                            CornerRadius(s * 0.08f, s * 0.08f)
                        )

                        // 3) Rounded shiny chrome toaster body with multi-stop specular reflection
                        val bodyCorner = CornerRadius(s * 0.22f, s * 0.22f)
                        drawRoundRect(
                            Brush.horizontalGradient(
                                0.00f to Color(0xFF546275),
                                0.18f to Color(0xFFDCE6F4),
                                0.34f to Color(0xFFFFFFFF),
                                0.52f to Color(0xFFA6B6CC),
                                0.78f to Color(0xFF6B7B92),
                                0.92f to Color(0xFFCFDCEE),
                                1.00f to Color(0xFF3E4858),
                                startX = px,
                                endX = px + bodyW
                            ),
                            Offset(px, py),
                            Size(bodyW, bodyH * 0.88f),
                            bodyCorner
                        )
                        // Subtle top chrome crown highlight
                        drawRoundRect(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.65f), Color.Transparent),
                                startY = py,
                                endY = py + bodyH * 0.36f
                            ),
                            Offset(px + strokeW, py + strokeW),
                            Size(bodyW - strokeW * 2f, bodyH * 0.34f),
                            bodyCorner
                        )
                        // Crisp toaster outline
                        drawRoundRect(
                            Color(0xFF101320),
                            Offset(px, py),
                            Size(bodyW, bodyH * 0.88f),
                            bodyCorner,
                            style = Stroke(width = strokeW)
                        )

                        // 4) Two angled dark toast slots on top deck
                        drawRoundRect(
                            Color(0xFF151722),
                            Offset(px + bodyW * 0.18f, py + bodyH * 0.11f),
                            Size(bodyW * 0.56f, bodyH * 0.10f),
                            CornerRadius(s * 0.04f, s * 0.04f)
                        )
                        drawRoundRect(
                            Color(0xFF151722),
                            Offset(px + bodyW * 0.24f, py + bodyH * 0.25f),
                            Size(bodyW * 0.54f, bodyH * 0.09f),
                            CornerRadius(s * 0.04f, s * 0.04f)
                        )
                        // Warm cozy golden toast peeking slightly from top slot
                        drawRoundRect(
                            Color(0xFFE6A44C),
                            Offset(px + bodyW * 0.23f, py + bodyH * 0.06f),
                            Size(bodyW * 0.44f, bodyH * 0.08f),
                            CornerRadius(s * 0.03f, s * 0.03f)
                        )

                        // 5) Classic front lever slot + pastel-pink retro knob
                        val leverX = px + bodyW * 0.14f
                        drawRoundRect(
                            Color(0xFF222634),
                            Offset(leverX, py + bodyH * 0.42f),
                            Size(bodyW * 0.06f, bodyH * 0.32f),
                            CornerRadius(s * 0.02f, s * 0.02f)
                        )
                        drawRoundRect(
                            Color(0xFFF06A96),
                            Offset(leverX - bodyW * 0.05f, py + bodyH * 0.52f),
                            Size(bodyW * 0.16f, bodyH * 0.12f),
                            CornerRadius(s * 0.04f, s * 0.04f)
                        )
                        drawRoundRect(
                            Color(0xFF101320),
                            Offset(leverX - bodyW * 0.05f, py + bodyH * 0.52f),
                            Size(bodyW * 0.16f, bodyH * 0.12f),
                            CornerRadius(s * 0.04f, s * 0.04f),
                            style = Stroke(width = strokeW * 0.7f)
                        )

                        // 6) Front wing (attached to near side of toaster, sweeping gracefully!)
                        drawFeatheredWing(
                            rootX = px + bodyW * 0.46f,
                            rootY = py + bodyH * 0.36f,
                            dirX = -1f,
                            wingScale = 1.0f,
                            isBack = false
                        )
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
                    for (b in 0 until brickRows) {
                        val by = wallTop + b * bandH
                        if (by + bandH < 0f || by > size.height) continue
                        val staggeredU = (wallX * 2f + (if (b % 2 == 0) 0f else 0.5f)) % 1f
                        val isVerticalMortar = staggeredU < 0.08f
                        val brickTint = if (((b * 3 + (wallX * 4).toInt()) % 3) == 0) 0.90f else 1.0f
                        val baseCol = if (isVerticalMortar) {
                            Color((0xC8 * shade).toInt(), (0xB8 * shade).toInt(), (0xA4 * shade).toInt())
                        } else {
                            Color((0xC4 * shade * brickTint).toInt(), (0x46 * shade * brickTint).toInt(), (0x34 * shade * brickTint).toInt())
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
