package com.retrovibe.os.ui.windows

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.util.Base64
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton
import com.retrovibe.os.ui.win98Bevel
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import kotlin.random.Random

private val WIN98_PAINT_COLORS = listOf(
    Color(0xFF000000), Color(0xFF808080), Color(0xFF800000), Color(0xFF808000),
    Color(0xFF008000), Color(0xFF008080), Color(0xFF000080), Color(0xFF800080),
    Color(0xFF808040), Color(0xFF004040), Color(0xFF0080FF), Color(0xFF004080),
    Color(0xFF8000FF), Color(0xFF804000),
    Color(0xFFFFFFFF), Color(0xFFC0C0C0), Color(0xFFFF0000), Color(0xFFFFFF00),
    Color(0xFF00FF00), Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF),
    Color(0xFFFFFF80), Color(0xFF00FF80), Color(0xFF80FFFF), Color(0xFF8080FF),
    Color(0xFFFF0080), Color(0xFFFF8040)
)

private val PAINT_TOOLS = listOf(
    "paint_freeselect", "paint_select",
    "paint_eraser", "paint_bucket",
    "paint_picker", "paint_zoom",
    "paint_pencil", "paint_brush",
    "paint_airbrush", "paint_text",
    "paint_line", "paint_curve",
    "paint_rect", "paint_polygon",
    "paint_ellipse", "paint_roundrect"
)

private fun floodFillBitmap(bmp: Bitmap, startX: Int, startY: Int, replacementColor: Int) {
    val w = bmp.width
    val h = bmp.height
    if (startX !in 0 until w || startY !in 0 until h) return
    val targetColor = bmp.getPixel(startX, startY)
    if (targetColor == replacementColor) return
    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    val q = ArrayDeque<Int>()
    q.add(startY * w + startX)
    pixels[startY * w + startX] = replacementColor
    while (q.isNotEmpty()) {
        val idx = q.removeFirst()
        val cx = idx % w
        val cy = idx / w
        if (cx > 0 && pixels[idx - 1] == targetColor) {
            pixels[idx - 1] = replacementColor; q.add(idx - 1)
        }
        if (cx < w - 1 && pixels[idx + 1] == targetColor) {
            pixels[idx + 1] = replacementColor; q.add(idx + 1)
        }
        if (cy > 0 && pixels[idx - w] == targetColor) {
            pixels[idx - w] = replacementColor; q.add(idx - w)
        }
        if (cy < h - 1 && pixels[idx + w] == targetColor) {
            pixels[idx + w] = replacementColor; q.add(idx + w)
        }
    }
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
}

@Composable
fun MsPaintWindow(vm: OSViewModel, palette: RetroPalette) {
    val bmpW = 300
    val bmpH = 340

    val bitmap = remember(vm.editingArtworkId) {
        val existing = vm.editingArtworkId?.let { id -> vm.artworks.find { it.id == id } }
        val loaded = existing?.let {
            try {
                val bytes = Base64.decode(it.pngBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.copy(Bitmap.Config.ARGB_8888, true)
            } catch (_: Throwable) {
                null
            }
        }
        loaded ?: Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888).apply {
            android.graphics.Canvas(this).drawColor(android.graphics.Color.WHITE)
        }
    }

    var redrawTick by remember { mutableStateOf(0) }
    var activeTool by remember { mutableStateOf("paint_pencil") }
    var fgColor by remember { mutableStateOf(Color.Black) }
    var bgColor by remember { mutableStateOf(Color.White) }
    var brushSize by remember { mutableStateOf(4f) }
    var zoomLevel by remember { mutableStateOf(1) }
    var statusText by remember { mutableStateOf("Ready - 16 Tools") }
    val undoStack = remember { mutableStateListOf<Bitmap>() }

    var dragStartX by remember { mutableStateOf(0f) }
    var dragStartY by remember { mutableStateOf(0f) }
    var lastX by remember { mutableStateOf(0f) }
    var lastY by remember { mutableStateOf(0f) }

    fun pushUndo() {
        if (undoStack.size >= 15) undoStack.removeAt(0)
        undoStack.add(bitmap.copy(Bitmap.Config.ARGB_8888, true))
    }

    fun applyUndo() {
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeAt(undoStack.lastIndex)
        android.graphics.Canvas(bitmap).drawBitmap(prev, 0f, 0f, null)
        redrawTick++
    }

    fun saveDrawing() {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        val existing = vm.editingArtworkId?.let { id -> vm.artworks.find { it.id == id } }
        val title = existing?.title ?: "Painting_${vm.artworks.size + 1}.BMP"
        val saved = vm.saveArtwork(title, b64, existing?.id)
        vm.editingArtworkId = saved.id
        statusText = "Saved ${saved.title}!"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFC0C0C0))
    ) {
        // Menu bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFC0C0C0))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RetroButton(label = "New", palette = palette, onClick = {
                pushUndo()
                android.graphics.Canvas(bitmap).drawColor(android.graphics.Color.WHITE)
                vm.editingArtworkId = null
                redrawTick++
            })
            RetroButton(label = "Save", palette = palette, onClick = { saveDrawing() })
            RetroButton(label = "Undo", palette = palette, onClick = { applyUndo() })
            RetroButton(label = "${zoomLevel}x", palette = palette, onClick = { zoomLevel = if (zoomLevel == 1) 2 else 1 })
            Spacer(Modifier.weight(1f))
            Text(statusText, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color.Black)
        }

        // Main area: Left 2x8 Tool Box + Canvas
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            // Classic 2-column 16-tool box + 3x4 brush selector
            Column(
                modifier = Modifier
                    .width(58.dp)
                    .fillMaxHeight()
                    .padding(end = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in 0 until 8) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (col in 0 until 2) {
                            val toolId = PAINT_TOOLS[row * 2 + col]
                            val selected = activeTool == toolId
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .win98Bevel(sunken = selected, bg = if (selected) Color(0xFFEAEAEA) else Color(0xFFC0C0C0))
                                    .clickable {
                                        RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                                        activeTool = toolId
                                        statusText = toolId.removePrefix("paint_").uppercase()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                PixelIcon(toolId, 16.dp)
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }

                Spacer(Modifier.height(4.dp))

                // Brush size matrix
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .win98Bevel(sunken = true, bg = Color(0xFFC0C0C0))
                        .padding(3.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    listOf(2f, 5f, 9f, 14f).forEach { sz ->
                        val active = brushSize == sz
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .background(if (active) Color(0xFF000080) else Color.Transparent)
                                .clickable { brushSize = sz },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(sz.coerceAtMost(10f).dp)
                                    .background(if (active) Color.White else Color.Black)
                            )
                        }
                    }
                }
            }

            // Drawing Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .win98Bevel(sunken = true, bg = Color(0xFF808080))
                    .padding(3.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .pointerInput(activeTool, fgColor, bgColor, brushSize) {
                            detectTapGestures { offset ->
                                val bx = (offset.x / size.width * bmpW).toInt().coerceIn(0, bmpW - 1)
                                val by = (offset.y / size.height * bmpH).toInt().coerceIn(0, bmpH - 1)
                                when (activeTool) {
                                    "paint_bucket" -> {
                                        pushUndo()
                                        floodFillBitmap(bitmap, bx, by, fgColor.toArgb())
                                        redrawTick++
                                    }
                                    "paint_picker" -> {
                                        fgColor = Color(bitmap.getPixel(bx, by))
                                        statusText = "Picked color"
                                    }
                                    "paint_zoom" -> {
                                        zoomLevel = if (zoomLevel == 1) 2 else 1
                                    }
                                    "paint_text" -> {
                                        pushUndo()
                                        val c = android.graphics.Canvas(bitmap)
                                        val p = Paint().apply {
                                            color = fgColor.toArgb()
                                            textSize = 18f
                                            isAntiAlias = false
                                        }
                                        c.drawText("RetroVibe", bx.toFloat(), by.toFloat(), p)
                                        redrawTick++
                                    }
                                    else -> {
                                        pushUndo()
                                        val c = android.graphics.Canvas(bitmap)
                                        val p = Paint().apply {
                                            color = if (activeTool == "paint_eraser") bgColor.toArgb() else fgColor.toArgb()
                                            style = Paint.Style.FILL
                                        }
                                        val r = if (activeTool == "paint_pencil") 1.5f else brushSize / 2f
                                        c.drawCircle(bx.toFloat(), by.toFloat(), r, p)
                                        redrawTick++
                                    }
                                }
                            }
                        }
                        .pointerInput(activeTool, fgColor, bgColor, brushSize) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    pushUndo()
                                    dragStartX = (offset.x / size.width * bmpW).coerceIn(0f, bmpW - 1f)
                                    dragStartY = (offset.y / size.height * bmpH).coerceIn(0f, bmpH - 1f)
                                    lastX = dragStartX
                                    lastY = dragStartY
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val nx = (change.position.x / size.width * bmpW).coerceIn(0f, bmpW - 1f)
                                    val ny = (change.position.y / size.height * bmpH).coerceIn(0f, bmpH - 1f)
                                    val c = android.graphics.Canvas(bitmap)
                                    val p = Paint().apply {
                                        color = if (activeTool == "paint_eraser") bgColor.toArgb() else fgColor.toArgb()
                                        strokeWidth = when (activeTool) {
                                            "paint_pencil" -> 2f
                                            "paint_eraser" -> brushSize * 2f
                                            else -> brushSize
                                        }
                                        strokeCap = Paint.Cap.ROUND
                                        style = Paint.Style.STROKE
                                    }
                                    when (activeTool) {
                                        "paint_pencil", "paint_brush", "paint_eraser" -> {
                                            c.drawLine(lastX, lastY, nx, ny, p)
                                            redrawTick++
                                        }
                                        "paint_airbrush" -> {
                                            val dotP = Paint().apply { color = fgColor.toArgb() }
                                            repeat(16) {
                                                val rx = nx + (Random.nextFloat() * 2f - 1f) * brushSize * 2f
                                                val ry = ny + (Random.nextFloat() * 2f - 1f) * brushSize * 2f
                                                c.drawPoint(rx, ry, dotP)
                                            }
                                            redrawTick++
                                        }
                                    }
                                    lastX = nx
                                    lastY = ny
                                },
                                onDragEnd = {
                                    val c = android.graphics.Canvas(bitmap)
                                    val p = Paint().apply {
                                        color = fgColor.toArgb()
                                        strokeWidth = brushSize
                                        style = Paint.Style.STROKE
                                    }
                                    val left = minOf(dragStartX, lastX)
                                    val top = minOf(dragStartY, lastY)
                                    val right = maxOf(dragStartX, lastX)
                                    val bottom = maxOf(dragStartY, lastY)
                                    when (activeTool) {
                                        "paint_line", "paint_curve", "paint_polygon" -> {
                                            c.drawLine(dragStartX, dragStartY, lastX, lastY, p)
                                            redrawTick++
                                        }
                                        "paint_rect", "paint_select", "paint_freeselect" -> {
                                            c.drawRect(left, top, right, bottom, p)
                                            redrawTick++
                                        }
                                        "paint_roundrect" -> {
                                            c.drawRoundRect(RectF(left, top, right, bottom), 10f, 10f, p)
                                            redrawTick++
                                        }
                                        "paint_ellipse" -> {
                                            c.drawOval(RectF(left, top, right, bottom), p)
                                            redrawTick++
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    val _t = redrawTick
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                }
            }
        }

        // Authentic 28-color Win98 square color palette at bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Active FG / BG swatch box
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .win98Bevel(sunken = true, bg = Color(0xFFE0E0E0))
                    .clickable {
                        val tmp = fgColor
                        fgColor = bgColor
                        bgColor = tmp
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .background(bgColor)
                        .border(1.dp, Color.Black)
                )
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                        .background(fgColor)
                        .border(1.dp, Color.Black)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (row in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (col in 0 until 14) {
                            val swatch = WIN98_PAINT_COLORS[row * 14 + col]
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .background(swatch)
                                    .border(1.dp, Color(0xFF404040))
                                    .clickable { fgColor = swatch }
                            )
                        }
                    }
                }
            }
        }
    }
}
