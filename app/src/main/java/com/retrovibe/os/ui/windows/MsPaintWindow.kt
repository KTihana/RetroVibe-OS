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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.hypot
import kotlin.math.roundToInt
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

private val TOOL_NAMES = mapOf(
    "paint_freeselect" to "Free-Form Select: draw around an area",
    "paint_select" to "Select: drag a box, then drag it to move",
    "paint_eraser" to "Eraser",
    "paint_bucket" to "Fill With Color",
    "paint_picker" to "Pick Color",
    "paint_zoom" to "Magnifier: tap to zoom",
    "paint_pencil" to "Pencil",
    "paint_brush" to "Brush",
    "paint_airbrush" to "Airbrush",
    "paint_text" to "Text: tap where to type",
    "paint_line" to "Line",
    "paint_curve" to "Curve: drag a line, then drag to bend",
    "paint_rect" to "Rectangle",
    "paint_polygon" to "Polygon: tap points, tap start to close",
    "paint_ellipse" to "Ellipse",
    "paint_roundrect" to "Rounded Rectangle"
)

private val SHAPE_TOOLS = setOf("paint_rect", "paint_ellipse", "paint_roundrect", "paint_polygon")
private val STROKE_TOOLS = setOf("paint_eraser", "paint_brush", "paint_airbrush", "paint_line", "paint_curve", "paint_text")

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
        if (cx > 0 && pixels[idx - 1] == targetColor) { pixels[idx - 1] = replacementColor; q.add(idx - 1) }
        if (cx < w - 1 && pixels[idx + 1] == targetColor) { pixels[idx + 1] = replacementColor; q.add(idx + 1) }
        if (cy > 0 && pixels[idx - w] == targetColor) { pixels[idx - w] = replacementColor; q.add(idx - w) }
        if (cy < h - 1 && pixels[idx + w] == targetColor) { pixels[idx + w] = replacementColor; q.add(idx + w) }
    }
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
}

@Composable
fun MsPaintWindow(vm: OSViewModel, palette: RetroPalette) {
    var bitmap by remember(vm.editingArtworkId) {
        val existing = vm.editingArtworkId?.let { id -> vm.artworks.find { it.id == id } }
        val loaded = existing?.let {
            try {
                val bytes = Base64.decode(it.pngBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.copy(Bitmap.Config.ARGB_8888, true)
            } catch (_: Throwable) {
                null
            }
        }
        mutableStateOf(
            loaded ?: Bitmap.createBitmap(360, 540, Bitmap.Config.ARGB_8888).apply {
                android.graphics.Canvas(this).drawColor(android.graphics.Color.WHITE)
            }
        )
    }
    val bw = bitmap.width
    val bh = bitmap.height

    var canvasBoxPx by remember { mutableStateOf(IntSize.Zero) }
    var initializedFullCanvas by remember(vm.editingArtworkId) { mutableStateOf(vm.editingArtworkId != null) }
    var hasDrawn by remember(vm.editingArtworkId) { mutableStateOf(vm.editingArtworkId != null) }
    var showSizePanel by remember { mutableStateOf(false) }
    var customW by remember { mutableStateOf(bw.toString()) }
    var customH by remember { mutableStateOf(bh.toString()) }

    var redrawTick by remember { mutableStateOf(0) }
    var activeTool by remember { mutableStateOf("paint_pencil") }
    var previousTool by remember { mutableStateOf("paint_pencil") }
    var fgColor by remember { mutableStateOf(Color.Black) }
    var bgColor by remember { mutableStateOf(Color.White) }
    var brushSize by remember { mutableStateOf(5f) }
    var fillMode by remember { mutableStateOf(0) } // 0 outline, 1 outline + fill, 2 solid
    var statusText by remember { mutableStateOf("Ready • Pinch with 2 fingers to zoom & pan") }
    val undoStack = remember { mutableStateListOf<Bitmap>() }

    // Continuous zoom & pan viewport (zoom 1f = fit canvas cleanly, up to 8f; pan in screen px)
    var zoom by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

    fun computeFullCanvasSize(): Pair<Int, Int> {
        if (canvasBoxPx.width <= 0 || canvasBoxPx.height <= 0) return 360 to 540
        val aspect = canvasBoxPx.width.toFloat() / canvasBoxPx.height.toFloat()
        return if (aspect >= 1f) {
            val h = 420
            (h * aspect).roundToInt().coerceIn(200, 960) to h
        } else {
            val w = 420
            w to (w / aspect).roundToInt().coerceIn(200, 960)
        }
    }

    // Automatically fit initial blank canvas to fill 100% of the available white area
    LaunchedEffect(canvasBoxPx, initializedFullCanvas) {
        if (!initializedFullCanvas && !hasDrawn && canvasBoxPx.width > 0 && canvasBoxPx.height > 0) {
            val (tw, th) = computeFullCanvasSize()
            bitmap = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888).apply {
                android.graphics.Canvas(this).drawColor(android.graphics.Color.WHITE)
            }
            customW = tw.toString()
            customH = th.toString()
            initializedFullCanvas = true
            redrawTick++
        }
    }

    // Live previews
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCur by remember { mutableStateOf<Offset?>(null) }
    val freePath = remember { mutableStateListOf<Offset>() }

    // Curve tool: first drag = line, second drag = bend
    var curveA by remember { mutableStateOf<Offset?>(null) }
    var curveB by remember { mutableStateOf<Offset?>(null) }
    var curveCtrl by remember { mutableStateOf<Offset?>(null) }

    // Polygon tool
    val polyPts = remember { mutableStateListOf<Offset>() }

    // Floating selection
    var selBmp by remember { mutableStateOf<Bitmap?>(null) }
    var selPath by remember { mutableStateOf<android.graphics.Path?>(null) }
    var selX by remember { mutableStateOf(0f) }
    var selY by remember { mutableStateOf(0f) }
    var selLifted by remember { mutableStateOf(false) }
    var movingSel by remember { mutableStateOf(false) }
    var selTransparent by remember { mutableStateOf(false) } // false = Opaque, true = Transparent (drops bgColor)

    // Text tool
    var textAt by remember { mutableStateOf<Offset?>(null) }
    var textValue by remember { mutableStateOf("") }

    fun refresh() { redrawTick++ }

    fun pushUndo() {
        hasDrawn = true
        if (undoStack.size >= 20) undoStack.removeAt(0)
        undoStack.add(bitmap.copy(Bitmap.Config.ARGB_8888, true))
    }

    fun strokePaint(color: Color, width: Float) = Paint().apply {
        this.color = color.toArgb()
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
        isAntiAlias = false
    }

    fun fillPaint(color: Color) = Paint().apply {
        this.color = color.toArgb()
        style = Paint.Style.FILL
        isAntiAlias = false
    }

    fun shapeStroke(): Float = (brushSize / 2f).coerceAtLeast(1f)

    // ---------- Selection ----------
    fun renderSelBitmap(): Bitmap? {
        val raw = selBmp ?: return null
        if (!selTransparent) return raw
        val w = raw.width
        val h = raw.height
        val out = raw.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        val key = bgColor.toArgb()
        for (i in pixels.indices) {
            if (pixels[i] == key) pixels[i] = 0x00000000
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    fun commitSelection() {
        val b = renderSelBitmap() ?: return
        if (selLifted) {
            android.graphics.Canvas(bitmap).drawBitmap(b, selX, selY, null)
        }
        selBmp = null; selPath = null; selLifted = false; movingSel = false
        refresh()
    }

    fun liftSelection() {
        if (selLifted) return
        val path = selPath ?: return
        pushUndo()
        android.graphics.Canvas(bitmap).drawPath(path, fillPaint(bgColor))
        selLifted = true
        refresh()
    }

    fun makeSelection(path: android.graphics.Path) {
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val l = bounds.left.coerceIn(0f, bw.toFloat()).toInt()
        val t = bounds.top.coerceIn(0f, bh.toFloat()).toInt()
        val r = bounds.right.coerceIn(0f, bw.toFloat()).roundToInt()
        val b = bounds.bottom.coerceIn(0f, bh.toFloat()).roundToInt()
        if (r - l < 2 || b - t < 2) return
        val floating = Bitmap.createBitmap(r - l, b - t, Bitmap.Config.ARGB_8888)
        val fc = android.graphics.Canvas(floating)
        fc.translate(-l.toFloat(), -t.toFloat())
        fc.clipPath(path)
        fc.drawBitmap(bitmap, 0f, 0f, null)
        selBmp = floating
        selPath = path
        selX = l.toFloat(); selY = t.toFloat()
        selLifted = false
        statusText = "Drag selection to move • Del • Copy"
    }

    fun insideSelection(p: Offset): Boolean {
        val b = selBmp ?: return false
        return p.x >= selX && p.x <= selX + b.width && p.y >= selY && p.y <= selY + b.height
    }

    fun deleteSelection() {
        if (selBmp == null) return
        if (!selLifted) liftSelection()
        selBmp = null; selPath = null; selLifted = false
        statusText = "Selection cleared"
        refresh()
    }

    fun copySelection() {
        val b = renderSelBitmap() ?: return
        pushUndo()
        if (selLifted) android.graphics.Canvas(bitmap).drawBitmap(b, selX, selY, null)
        selLifted = true // source stays: moving now drags a duplicate
        statusText = "Copied - drag the copy"
        refresh()
    }

    // ---------- Polygon / Curve ----------
    fun drawShapeOnBitmap(tool: String, l: Float, t: Float, r: Float, b: Float, poly: List<Offset>? = null) {
        val c = android.graphics.Canvas(bitmap)
        val path = android.graphics.Path()
        if (poly != null) {
            path.moveTo(poly[0].x, poly[0].y)
            poly.drop(1).forEach { path.lineTo(it.x, it.y) }
            path.close()
        }
        fun draw(p: Paint) {
            when (tool) {
                "paint_rect" -> c.drawRect(l, t, r, b, p)
                "paint_ellipse" -> c.drawOval(RectF(l, t, r, b), p)
                "paint_roundrect" -> c.drawRoundRect(RectF(l, t, r, b), 12f, 12f, p)
                "paint_polygon" -> c.drawPath(path, p)
            }
        }
        when (fillMode) {
            1 -> { draw(fillPaint(bgColor)); draw(strokePaint(fgColor, shapeStroke())) }
            2 -> draw(fillPaint(fgColor))
            else -> draw(strokePaint(fgColor, shapeStroke()))
        }
        refresh()
    }

    fun commitPolygon() {
        if (polyPts.size >= 3) {
            pushUndo()
            drawShapeOnBitmap("paint_polygon", 0f, 0f, 0f, 0f, polyPts.toList())
        } else if (polyPts.size == 2) {
            pushUndo()
            android.graphics.Canvas(bitmap).drawLine(polyPts[0].x, polyPts[0].y, polyPts[1].x, polyPts[1].y, strokePaint(fgColor, shapeStroke()))
            refresh()
        }
        polyPts.clear()
    }

    fun commitCurve() {
        val a = curveA; val b = curveB
        if (a != null && b != null) {
            pushUndo()
            val path = android.graphics.Path().apply {
                moveTo(a.x, a.y)
                val ctrl = curveCtrl
                if (ctrl != null) {
                    // pass through the touched point: q = 2*ctrl - (a+b)/2
                    quadTo(2 * ctrl.x - (a.x + b.x) / 2f, 2 * ctrl.y - (a.y + b.y) / 2f, b.x, b.y)
                } else lineTo(b.x, b.y)
            }
            android.graphics.Canvas(bitmap).drawPath(path, strokePaint(fgColor, brushSize / 2f + 1f))
            refresh()
        }
        curveA = null; curveB = null; curveCtrl = null
    }

    fun commitText() {
        val at = textAt
        if (at != null && textValue.isNotBlank()) {
            pushUndo()
            val p = Paint().apply {
                color = fgColor.toArgb()
                textSize = 10f + brushSize * 1.6f
                typeface = android.graphics.Typeface.MONOSPACE
                isAntiAlias = false
            }
            android.graphics.Canvas(bitmap).drawText(textValue, at.x, at.y + p.textSize * 0.8f, p)
            refresh()
        }
        textAt = null
        textValue = ""
    }

    fun finishPending() {
        commitSelection()
        commitPolygon()
        commitCurve()
        commitText()
        freePath.clear()
        dragStart = null; dragCur = null
    }

    fun selectTool(toolId: String) {
        finishPending()
        if (toolId != "paint_picker") previousTool = toolId
        activeTool = toolId
        statusText = TOOL_NAMES[toolId] ?: toolId
    }

    fun formatZoom(z: Float): String {
        val rounded = z.roundToInt().toFloat()
        return if (kotlin.math.abs(z - rounded) < 0.05f) "${rounded.toInt()}x"
        else "${"%.1f".format(z)}x"
    }

    fun currentViewCenter(): Offset {
        if (canvasBoxPx.width <= 0 || canvasBoxPx.height <= 0) {
            return Offset(bitmap.width / 2f, bitmap.height / 2f)
        }
        val cw = canvasBoxPx.width.toFloat()
        val ch = canvasBoxPx.height.toFloat()
        val curBw = bitmap.width.toFloat()
        val curBh = bitmap.height.toFloat()
        val baseScale = minOf(cw / curBw, ch / curBh).coerceAtLeast(0.01f)
        val scale = baseScale * zoom
        val ox = (cw - curBw * scale) / 2f + panX
        val oy = (ch - curBh * scale) / 2f + panY
        return Offset(
            ((cw / 2f - ox) / scale).coerceIn(0f, curBw),
            ((ch / 2f - oy) / scale).coerceIn(0f, curBh)
        )
    }

    fun setZoom(level: Float, center: Offset) {
        val clampedZoom = level.coerceIn(1f, 8f)
        zoom = clampedZoom
        if (clampedZoom <= 1.01f || canvasBoxPx.width <= 0 || canvasBoxPx.height <= 0) {
            panX = 0f
            panY = 0f
        } else {
            val cw = canvasBoxPx.width.toFloat()
            val ch = canvasBoxPx.height.toFloat()
            val curBw = bitmap.width.toFloat()
            val curBh = bitmap.height.toFloat()
            val baseScale = minOf(cw / curBw, ch / curBh).coerceAtLeast(0.01f)
            val newScale = baseScale * clampedZoom
            val drawW = curBw * newScale
            val drawH = curBh * newScale
            val rawPanX = cw / 2f - center.x * newScale - (cw - drawW) / 2f
            val rawPanY = ch / 2f - center.y * newScale - (ch - drawH) / 2f
            val maxPanX = ((drawW - cw) / 2f).coerceAtLeast(0f)
            val maxPanY = ((drawH - ch) / 2f).coerceAtLeast(0f)
            panX = rawPanX.coerceIn(-maxPanX, maxPanX)
            panY = rawPanY.coerceIn(-maxPanY, maxPanY)
        }
        statusText = "Zoom ${formatZoom(clampedZoom)}"
    }

    fun resizeCanvas(newW: Int, newH: Int, clearCanvas: Boolean = false) {
        val w = newW.coerceIn(64, 1024)
        val h = newH.coerceIn(64, 1024)
        finishPending()
        pushUndo()
        val next = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(next)
        c.drawColor(if (clearCanvas) android.graphics.Color.WHITE else bgColor.toArgb())
        if (!clearCanvas) {
            c.drawBitmap(bitmap, 0f, 0f, null)
        }
        bitmap = next
        customW = w.toString()
        customH = h.toString()
        initializedFullCanvas = true
        zoom = 1f
        panX = 0f
        panY = 0f
        statusText = "Canvas ${w}×${h}"
        refresh()
    }

    fun applyUndo() {
        selBmp = null; selPath = null; selLifted = false
        polyPts.clear(); curveA = null; curveB = null; curveCtrl = null
        if (undoStack.isEmpty()) { refresh(); return }
        val prev = undoStack.removeAt(undoStack.lastIndex)
        bitmap = prev
        customW = prev.width.toString()
        customH = prev.height.toString()
        refresh()
    }

    fun saveDrawing() {
        finishPending()
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        val existing = vm.editingArtworkId?.let { id -> vm.artworks.find { it.id == id } }
        val title = existing?.title ?: "Painting_${vm.artworks.size + 1}.BMP"
        val saved = vm.saveArtwork(title, b64, existing?.id)
        vm.editingArtworkId = saved.id
        statusText = "Saved ${saved.title}!"
    }

    fun spray(at: Offset) {
        val c = android.graphics.Canvas(bitmap)
        val p = fillPaint(fgColor)
        val radius = brushSize * 2f
        repeat(18) {
            val ang = Random.nextFloat() * 6.2832f
            val dist = Random.nextFloat() * radius
            c.drawPoint(at.x + kotlin.math.cos(ang) * dist, at.y + kotlin.math.sin(ang) * dist, p)
        }
        refresh()
    }

    // ---------- Gesture handlers (bitmap coordinates) ----------
    fun onTap(p: Offset) {
        val px = p.x.toInt().coerceIn(0, bw - 1)
        val py = p.y.toInt().coerceIn(0, bh - 1)
        when (activeTool) {
            "paint_bucket" -> { pushUndo(); floodFillBitmap(bitmap, px, py, fgColor.toArgb()); refresh() }
            "paint_picker" -> {
                fgColor = Color(bitmap.getPixel(px, py))
                statusText = "Picked color"
                activeTool = previousTool
            }
            "paint_zoom" -> {
                val next = when {
                    zoom < 1.8f -> 2f
                    zoom < 3.8f -> 4f
                    zoom < 7.5f -> 8f
                    else -> 1f
                }
                setZoom(next, p)
            }
            "paint_text" -> { commitText(); textAt = p; statusText = "Type your text, then OK" }
            "paint_pencil" -> {
                pushUndo(); android.graphics.Canvas(bitmap).drawRect(px.toFloat(), py.toFloat(), px + 1f, py + 1f, fillPaint(fgColor)); refresh()
            }
            "paint_brush", "paint_eraser" -> {
                pushUndo()
                val col = if (activeTool == "paint_eraser") bgColor else fgColor
                val r = if (activeTool == "paint_eraser") brushSize else brushSize / 2f
                if (activeTool == "paint_eraser") android.graphics.Canvas(bitmap).drawRect(p.x - r, p.y - r, p.x + r, p.y + r, fillPaint(col))
                else android.graphics.Canvas(bitmap).drawCircle(p.x, p.y, r, fillPaint(col))
                refresh()
            }
            "paint_airbrush" -> { pushUndo(); repeat(3) { spray(p) } }
            "paint_select", "paint_freeselect" -> if (!insideSelection(p)) commitSelection()
            "paint_curve" -> if (curveB != null) commitCurve()
            "paint_polygon" -> {
                if (polyPts.size >= 3 && hypot(p.x - polyPts[0].x, p.y - polyPts[0].y) < 12f) commitPolygon()
                else polyPts.add(p)
            }
        }
    }

    fun onDragStart(p: Offset) {
        dragStart = p; dragCur = p
        when (activeTool) {
            "paint_pencil", "paint_brush", "paint_eraser", "paint_airbrush" -> pushUndo()
            "paint_select", "paint_freeselect" -> {
                if (insideSelection(p)) {
                    liftSelection(); movingSel = true
                } else {
                    commitSelection()
                    freePath.clear(); freePath.add(p)
                }
            }
        }
    }

    fun onDragMove(prev: Offset, p: Offset) {
        dragCur = p
        when (activeTool) {
            "paint_pencil", "paint_brush" -> {
                val w = if (activeTool == "paint_pencil") 1f else brushSize
                android.graphics.Canvas(bitmap).drawLine(prev.x, prev.y, p.x, p.y, strokePaint(fgColor, w))
                refresh()
            }
            "paint_eraser" -> {
                val e = strokePaint(bgColor, brushSize * 2f).apply { strokeCap = Paint.Cap.SQUARE }
                android.graphics.Canvas(bitmap).drawLine(prev.x, prev.y, p.x, p.y, e)
                refresh()
            }
            "paint_airbrush" -> spray(p)
            "paint_select", "paint_freeselect" -> {
                if (movingSel) { selX += p.x - prev.x; selY += p.y - prev.y }
                else if (activeTool == "paint_freeselect") freePath.add(p)
            }
            "paint_curve" -> if (curveB != null) curveCtrl = p
        }
    }

    fun onDragEnd(start: Offset, end: Offset) {
        val l = minOf(start.x, end.x); val t = minOf(start.y, end.y)
        val r = maxOf(start.x, end.x); val b = maxOf(start.y, end.y)
        when (activeTool) {
            "paint_line" -> {
                pushUndo()
                android.graphics.Canvas(bitmap).drawLine(start.x, start.y, end.x, end.y, strokePaint(fgColor, brushSize / 2f + 1f))
                refresh()
            }
            "paint_rect", "paint_ellipse", "paint_roundrect" -> { pushUndo(); drawShapeOnBitmap(activeTool, l, t, r, b) }
            "paint_polygon" -> {
                if (polyPts.isEmpty()) { polyPts.add(start); polyPts.add(end) }
                else if (polyPts.size >= 3 && hypot(end.x - polyPts[0].x, end.y - polyPts[0].y) < 12f) commitPolygon()
                else polyPts.add(end)
            }
            "paint_curve" -> {
                if (curveB == null) { curveA = start; curveB = end; statusText = "Now drag to bend the curve" }
                else { curveCtrl = end; commitCurve(); statusText = TOOL_NAMES["paint_curve"]!! }
            }
            "paint_select" -> {
                if (movingSel) movingSel = false
                else makeSelection(android.graphics.Path().apply { addRect(l, t, r, b, android.graphics.Path.Direction.CW) })
            }
            "paint_freeselect" -> {
                if (movingSel) movingSel = false
                else if (freePath.size > 2) {
                    val path = android.graphics.Path()
                    path.moveTo(freePath[0].x, freePath[0].y)
                    freePath.drop(1).forEach { path.lineTo(it.x, it.y) }
                    path.close()
                    makeSelection(path)
                }
                freePath.clear()
            }
            "paint_bucket", "paint_picker", "paint_zoom", "paint_text" -> onTap(end)
        }
        dragStart = null; dragCur = null
        refresh()
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
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RetroButton(label = "New", palette = palette, onClick = {
                finishPending()
                pushUndo()
                android.graphics.Canvas(bitmap).drawColor(android.graphics.Color.WHITE)
                vm.editingArtworkId = null
                zoom = 1f
                panX = 0f
                panY = 0f
                refresh()
            })
            RetroButton(label = "Save", palette = palette, onClick = { saveDrawing() })
            RetroButton(label = "Undo", palette = palette, onClick = { applyUndo() })
            RetroButton(label = "Size", palette = palette, onClick = {
                customW = bitmap.width.toString()
                customH = bitmap.height.toString()
                showSizePanel = !showSizePanel
            })
            RetroButton(label = formatZoom(zoom), palette = palette, onClick = {
                val next = when {
                    zoom < 1.8f -> 2f
                    zoom < 3.8f -> 4f
                    else -> 1f
                }
                setZoom(next, currentViewCenter())
            })
            if (selBmp != null) {
                RetroButton(label = "Del", palette = palette, onClick = { deleteSelection() })
                RetroButton(label = "Copy", palette = palette, onClick = { copySelection() })
            }
            if (activeTool == "paint_polygon" && polyPts.size >= 2) {
                RetroButton(label = "Close", palette = palette, onClick = { commitPolygon() })
            }
        }
        Text(
            "$statusText  (${bw}×${bh})",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = Color.Black,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        // Canvas Size Picker Panel (Full / Presets / Custom WxH)
        if (showSizePanel) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .win98Bevel(sunken = true, bg = Color(0xFFD4D0C8))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RetroButton(label = "Full", palette = palette, onClick = {
                        val (fw, fh) = computeFullCanvasSize()
                        resizeCanvas(fw, fh)
                        showSizePanel = false
                    })
                    RetroButton(label = "Square", palette = palette, onClick = {
                        resizeCanvas(400, 400)
                        showSizePanel = false
                    })
                    RetroButton(label = "Portrait", palette = palette, onClick = {
                        resizeCanvas(360, 540)
                        showSizePanel = false
                    })
                    RetroButton(label = "Wide", palette = palette, onClick = {
                        resizeCanvas(540, 360)
                        showSizePanel = false
                    })
                    RetroButton(label = "Pixel", palette = palette, onClick = {
                        resizeCanvas(128, 128)
                        showSizePanel = false
                    })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("W:", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Black)
                    BasicTextField(
                        value = customW,
                        onValueChange = { s -> customW = s.filter { it.isDigit() }.take(4) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black),
                        modifier = Modifier
                            .width(46.dp)
                            .win98Bevel(sunken = true, bg = Color.White)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text("H:", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Black)
                    BasicTextField(
                        value = customH,
                        onValueChange = { s -> customH = s.filter { it.isDigit() }.take(4) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black),
                        modifier = Modifier
                            .width(46.dp)
                            .win98Bevel(sunken = true, bg = Color.White)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    RetroButton(label = "Apply", palette = palette, onClick = {
                        val w = customW.toIntOrNull() ?: bw
                        val h = customH.toIntOrNull() ?: bh
                        resizeCanvas(w, h)
                        showSizePanel = false
                    })
                    Spacer(Modifier.weight(1f))
                    RetroButton(label = "X", palette = palette, onClick = { showSizePanel = false })
                }
            }
        }

        // Text tool input bar
        if (textAt != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("A", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.Black)
                BasicTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.Black),
                    modifier = Modifier
                        .weight(1f)
                        .win98Bevel(sunken = true, bg = Color.White)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
                RetroButton(label = "OK", palette = palette, onClick = { commitText() })
                RetroButton(label = "X", palette = palette, onClick = { textAt = null; textValue = "" })
            }
        }

        // Main area: Tool box + Canvas
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
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
                                        selectTool(toolId)
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

                // Tool options box (changes with the selected tool, like real Paint)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .win98Bevel(sunken = true, bg = Color(0xFFC0C0C0))
                        .padding(3.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        activeTool == "paint_select" || activeTool == "paint_freeselect" -> {
                            // Classic Win98 Opaque vs. Transparent Selection toggle!
                            listOf(false, true).forEach { isTransparent ->
                                val active = selTransparent == isTransparent
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(28.dp)
                                        .background(if (active) Color(0xFF000080) else Color.Transparent)
                                        .clickable {
                                            RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                                            selTransparent = isTransparent
                                            statusText = if (isTransparent) "Transparent selection" else "Opaque selection"
                                            refresh()
                                        }
                                        .padding(3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val w = size.width
                                        val h = size.height
                                        // Yellow canvas background square on left
                                        drawRect(Color(0xFFFFF48C), Offset(w * 0.06f, h * 0.12f), Size(w * 0.68f, h * 0.78f))
                                        drawRect(Color.Black, Offset(w * 0.06f, h * 0.12f), Size(w * 0.68f, h * 0.78f), style = Stroke(1.2f))
                                        // Blue cylinder + red sphere behind selection
                                        drawRect(Color(0xFF2838C8), Offset(w * 0.16f, h * 0.22f), Size(w * 0.18f, h * 0.50f))
                                        drawRect(Color(0xFF7898FF), Offset(w * 0.19f, h * 0.24f), Size(w * 0.05f, h * 0.44f))
                                        drawCircle(Color(0xFFD82848), radius = h * 0.20f, center = Offset(w * 0.42f, h * 0.68f))
                                        // Selection box on right: opaque white fill OR transparent!
                                        val boxL = w * 0.46f
                                        val boxT = h * 0.18f
                                        val boxW = w * 0.48f
                                        val boxH = h * 0.62f
                                        if (!isTransparent) {
                                            drawRect(Color.White, Offset(boxL, boxT), Size(boxW, boxH))
                                        }
                                        // Green cube inside selection
                                        drawRect(Color(0xFF22B838), Offset(boxL + boxW * 0.22f, boxT + boxH * 0.18f), Size(boxW * 0.56f, boxH * 0.64f))
                                        drawRect(Color(0xFF6CF078), Offset(boxL + boxW * 0.26f, boxT + boxH * 0.22f), Size(boxW * 0.24f, boxH * 0.52f))
                                        drawRect(Color.Black, Offset(boxL + boxW * 0.22f, boxT + boxH * 0.18f), Size(boxW * 0.56f, boxH * 0.64f), style = Stroke(1f))
                                        // Dashed selection border
                                        drawRect(
                                            Color.Black,
                                            Offset(boxL, boxT),
                                            Size(boxW, boxH),
                                            style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2f)))
                                        )
                                    }
                                }
                            }
                        }
                        activeTool in SHAPE_TOOLS -> {
                            for (mode in 0..2) {
                                val active = fillMode == mode
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(16.dp)
                                        .background(if (active) Color(0xFF000080) else Color.Transparent)
                                        .clickable { fillMode = mode },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val line = if (active) Color.White else Color.Black
                                    Box(
                                        modifier = Modifier
                                            .size(width = 28.dp, height = 10.dp)
                                            .background(
                                                when (mode) {
                                                    0 -> Color.Transparent
                                                    1 -> Color(0xFF808080)
                                                    else -> line
                                                }
                                            )
                                            .then(if (mode != 2) Modifier.border(1.dp, line) else Modifier)
                                    )
                                }
                            }
                        }
                        activeTool in STROKE_TOOLS || activeTool in SHAPE_TOOLS -> {
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
                        activeTool == "paint_zoom" -> {
                            listOf(1f, 2f, 4f, 8f).forEach { lvl ->
                                val active = kotlin.math.abs(zoom - lvl) < 0.2f
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(16.dp)
                                        .background(if (active) Color(0xFF000080) else Color.Transparent)
                                        .clickable { setZoom(lvl, currentViewCenter()) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${lvl.toInt()}x", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (active) Color.White else Color.Black)
                                }
                            }
                        }
                        else -> Spacer(Modifier.height(20.dp))
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
                    .clipToBounds()
                    .onSizeChanged { canvasBoxPx = it }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF808080))
                        .pointerInput(bitmap) {
                            fun toBmp(o: Offset): Offset {
                                val cw = size.width.toFloat()
                                val ch = size.height.toFloat()
                                val curBw = bitmap.width.toFloat()
                                val curBh = bitmap.height.toFloat()
                                val baseScale = minOf(cw / curBw, ch / curBh).coerceAtLeast(0.01f)
                                val scale = baseScale * zoom
                                val drawW = curBw * scale
                                val drawH = curBh * scale
                                val maxPanX = ((drawW - cw) / 2f).coerceAtLeast(0f)
                                val maxPanY = ((drawH - ch) / 2f).coerceAtLeast(0f)
                                val ox = (cw - drawW) / 2f + panX.coerceIn(-maxPanX, maxPanX)
                                val oy = (ch - drawH) / 2f + panY.coerceIn(-maxPanY, maxPanY)
                                return Offset(
                                    ((o.x - ox) / scale).coerceIn(0f, curBw - 1f),
                                    ((o.y - oy) / scale).coerceIn(0f, curBh - 1f)
                                )
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                val startScreen = down.position
                                var lastScreen = startScreen
                                val startBmp = toBmp(startScreen)
                                var lastBmp = startBmp
                                var dragging = false
                                var multiTouch = false
                                var panningWithZoomTool = false
                                var pushedUndoForStroke = false
                                val slop = viewConfiguration.touchSlop

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) {
                                        event.changes.forEach { it.consume() }
                                        break
                                    }

                                    if (pressed.size >= 2) {
                                        if (!multiTouch) {
                                            multiTouch = true
                                            // Cancel any accidental single-finger stroke that just began as finger 2 landed
                                            if (dragging && !panningWithZoomTool) {
                                                if (pushedUndoForStroke && undoStack.isNotEmpty()) {
                                                    val prev = undoStack.removeAt(undoStack.lastIndex)
                                                    if (prev.width == bitmap.width && prev.height == bitmap.height) {
                                                        android.graphics.Canvas(bitmap).drawBitmap(prev, 0f, 0f, null)
                                                    }
                                                    pushedUndoForStroke = false
                                                }
                                                dragging = false
                                                movingSel = false
                                                freePath.clear()
                                                dragStart = null
                                                dragCur = null
                                                refresh()
                                            }
                                        }
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val centroid = event.calculateCentroid(useCurrent = true)
                                        if (zoomChange != 1f || panChange != Offset.Zero) {
                                            val cw = size.width.toFloat()
                                            val ch = size.height.toFloat()
                                            val curBw = bitmap.width.toFloat()
                                            val curBh = bitmap.height.toFloat()
                                            val baseScale = minOf(cw / curBw, ch / curBh).coerceAtLeast(0.01f)
                                            val oldScale = baseScale * zoom
                                            val oldDrawW = curBw * oldScale
                                            val oldDrawH = curBh * oldScale
                                            val oldMaxPanX = ((oldDrawW - cw) / 2f).coerceAtLeast(0f)
                                            val oldMaxPanY = ((oldDrawH - ch) / 2f).coerceAtLeast(0f)
                                            val oldOx = (cw - oldDrawW) / 2f + panX.coerceIn(-oldMaxPanX, oldMaxPanX)
                                            val oldOy = (ch - oldDrawH) / 2f + panY.coerceIn(-oldMaxPanY, oldMaxPanY)

                                            val prevCentroid = centroid - panChange
                                            val bmpPtX = (prevCentroid.x - oldOx) / oldScale
                                            val bmpPtY = (prevCentroid.y - oldOy) / oldScale

                                            val newZoom = (zoom * zoomChange).coerceIn(1f, 8f)
                                            val newScale = baseScale * newZoom
                                            val newDrawW = curBw * newScale
                                            val newDrawH = curBh * newScale

                                            val rawPanX = centroid.x - bmpPtX * newScale - (cw - newDrawW) / 2f
                                            val rawPanY = centroid.y - bmpPtY * newScale - (ch - newDrawH) / 2f
                                            val maxPanX = ((newDrawW - cw) / 2f).coerceAtLeast(0f)
                                            val maxPanY = ((newDrawH - ch) / 2f).coerceAtLeast(0f)

                                            zoom = newZoom
                                            panX = rawPanX.coerceIn(-maxPanX, maxPanX)
                                            panY = rawPanY.coerceIn(-maxPanY, maxPanY)
                                            statusText = "Zoom ${formatZoom(newZoom)}"
                                        }
                                        event.changes.forEach { it.consume() }
                                    } else if (!multiTouch) {
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            change.consume()
                                            break
                                        }
                                        if (!dragging && (change.position - startScreen).getDistance() > slop) {
                                            dragging = true
                                            if (activeTool == "paint_zoom" && zoom > 1.01f) {
                                                panningWithZoomTool = true
                                            } else {
                                                pushedUndoForStroke = activeTool in setOf("paint_pencil", "paint_brush", "paint_eraser", "paint_airbrush")
                                                onDragStart(startBmp)
                                            }
                                        }
                                        if (dragging) {
                                            if (panningWithZoomTool) {
                                                val delta = change.position - lastScreen
                                                val cw = size.width.toFloat()
                                                val ch = size.height.toFloat()
                                                val curBw = bitmap.width.toFloat()
                                                val curBh = bitmap.height.toFloat()
                                                val scale = minOf(cw / curBw, ch / curBh).coerceAtLeast(0.01f) * zoom
                                                val maxPanX = ((curBw * scale - cw) / 2f).coerceAtLeast(0f)
                                                val maxPanY = ((curBh * scale - ch) / 2f).coerceAtLeast(0f)
                                                panX = (panX + delta.x).coerceIn(-maxPanX, maxPanX)
                                                panY = (panY + delta.y).coerceIn(-maxPanY, maxPanY)
                                            } else {
                                                val p = toBmp(change.position)
                                                onDragMove(lastBmp, p)
                                                lastBmp = p
                                            }
                                            lastScreen = change.position
                                        }
                                        change.consume()
                                    } else {
                                        // One finger remains briefly after 2-finger gesture: ignore until all lifted
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                if (!multiTouch && !panningWithZoomTool) {
                                    if (dragging) onDragEnd(startBmp, lastBmp) else onTap(startBmp)
                                }
                            }
                        }
                ) {
                    @Suppress("UNUSED_VARIABLE") val tick = redrawTick
                    val curBw = bitmap.width.toFloat()
                    val curBh = bitmap.height.toFloat()
                    // Uniform 1:1 square-pixel scale so drawings & letters NEVER squish or stretch!
                    val baseScale = minOf(size.width / curBw, size.height / curBh).coerceAtLeast(0.01f)
                    val scale = baseScale * zoom
                    val drawW = curBw * scale
                    val drawH = curBh * scale
                    val maxPanX = ((drawW - size.width) / 2f).coerceAtLeast(0f)
                    val maxPanY = ((drawH - size.height) / 2f).coerceAtLeast(0f)
                    val ox = (size.width - drawW) / 2f + panX.coerceIn(-maxPanX, maxPanX)
                    val oy = (size.height - drawH) / 2f + panY.coerceIn(-maxPanY, maxPanY)
                    val sx = scale
                    val sy = scale
                    fun scr(b: Offset) = Offset(ox + b.x * scale, oy + b.y * scale)

                    // White paper backing + subtle Win98 drop shadow inside grey workspace
                    drawRect(Color(0xFF404040), Offset(ox + 2f, oy + 2f), Size(drawW, drawH))
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                        dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1)),
                        filterQuality = FilterQuality.None
                    )

                    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    val s = dragStart
                    val e = dragCur
                    val stroke = Stroke(width = (shapeStroke() * sx).coerceAtLeast(1.5f))

                    fun previewShape(tool: String, a: Offset, b: Offset) {
                        val tl = scr(Offset(minOf(a.x, b.x), minOf(a.y, b.y)))
                        val br = scr(Offset(maxOf(a.x, b.x), maxOf(a.y, b.y)))
                        val sz = Size(br.x - tl.x, br.y - tl.y)
                        fun DrawScope.shape(fill: Boolean, col: Color) {
                            val style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else stroke
                            when (tool) {
                                "paint_rect" -> drawRect(col, tl, sz, style = style)
                                "paint_ellipse" -> drawOval(col, tl, sz, style = style)
                                "paint_roundrect" -> drawRoundRect(col, tl, sz, androidx.compose.ui.geometry.CornerRadius(12f * sx), style = style)
                            }
                        }
                        when (fillMode) {
                            1 -> { shape(true, bgColor); shape(false, fgColor) }
                            2 -> shape(true, fgColor)
                            else -> shape(false, fgColor)
                        }
                    }

                    // Shape / line previews while dragging
                    if (s != null && e != null) {
                        when (activeTool) {
                            "paint_line" -> drawLine(fgColor, scr(s), scr(e), strokeWidth = ((brushSize / 2f + 1f) * sx))
                            "paint_rect", "paint_ellipse", "paint_roundrect" -> previewShape(activeTool, s, e)
                            "paint_curve" -> if (curveB == null) drawLine(fgColor, scr(s), scr(e), strokeWidth = ((brushSize / 2f + 1f) * sx))
                            "paint_polygon" -> {
                                val from = polyPts.lastOrNull() ?: s
                                drawLine(fgColor, scr(from), scr(e), strokeWidth = (shapeStroke() * sx).coerceAtLeast(1.5f))
                            }
                            "paint_select" -> if (!movingSel) {
                                val tl = scr(Offset(minOf(s.x, e.x), minOf(s.y, e.y)))
                                val br = scr(Offset(maxOf(s.x, e.x), maxOf(s.y, e.y)))
                                drawRect(Color.Black, tl, Size(br.x - tl.x, br.y - tl.y), style = Stroke(2f, pathEffect = dash))
                            }
                        }
                    }

                    // Free-form lasso outline
                    if (freePath.size > 1) {
                        val p = androidx.compose.ui.graphics.Path()
                        val f = scr(freePath[0]); p.moveTo(f.x, f.y)
                        freePath.drop(1).forEach { val q = scr(it); p.lineTo(q.x, q.y) }
                        drawPath(p, Color.Black, style = Stroke(2f, pathEffect = dash))
                    }

                    // Pending curve
                    val ca = curveA; val cb = curveB
                    if (ca != null && cb != null) {
                        val p = androidx.compose.ui.graphics.Path()
                        val a = scr(ca); val b = scr(cb)
                        p.moveTo(a.x, a.y)
                        val ctrl = curveCtrl
                        if (ctrl != null) {
                            val q = scr(Offset(2 * ctrl.x - (ca.x + cb.x) / 2f, 2 * ctrl.y - (ca.y + cb.y) / 2f))
                            p.quadraticBezierTo(q.x, q.y, b.x, b.y)
                        } else p.lineTo(b.x, b.y)
                        drawPath(p, fgColor, style = Stroke((brushSize / 2f + 1f) * sx))
                    }

                    // Pending polygon
                    if (polyPts.isNotEmpty()) {
                        val p = androidx.compose.ui.graphics.Path()
                        val f = scr(polyPts[0]); p.moveTo(f.x, f.y)
                        polyPts.drop(1).forEach { val q = scr(it); p.lineTo(q.x, q.y) }
                        drawPath(p, fgColor, style = Stroke((shapeStroke() * sx).coerceAtLeast(1.5f)))
                        drawCircle(Color(0xFF000080), radius = 5f, center = f, style = Stroke(2f))
                    }

                    // Floating selection
                    val sb = renderSelBitmap()
                    if (sb != null) {
                        val tl = scr(Offset(selX, selY))
                        val dst = IntSize((sb.width * sx).roundToInt().coerceAtLeast(1), (sb.height * sy).roundToInt().coerceAtLeast(1))
                        if (selLifted) {
                            drawImage(
                                image = sb.asImageBitmap(),
                                dstOffset = IntOffset(tl.x.roundToInt(), tl.y.roundToInt()),
                                dstSize = dst,
                                filterQuality = FilterQuality.None
                            )
                        }
                        drawRect(Color.Black, tl, Size(dst.width.toFloat(), dst.height.toFloat()), style = Stroke(2f, pathEffect = dash))
                        drawRect(Color.White, tl, Size(dst.width.toFloat(), dst.height.toFloat()), style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 7f)))
                    }

                    // Text insertion caret
                    val ta = textAt
                    if (ta != null) {
                        val c = scr(ta)
                        drawLine(Color.Black, c, Offset(c.x, c.y + (10f + brushSize * 1.6f) * sy), strokeWidth = 2f)
                    }
                }
            }
        }

        // Authentic 28-color Win98 palette
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Classic Win98 overlapping FG / BG color swatch box (tap to swap)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .win98Bevel(sunken = true, bg = Color(0xFFDCDCDC))
                    .clickable {
                        RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                        val tmp = fgColor
                        fgColor = bgColor
                        bgColor = tmp
                    }
            ) {
                // Back (BG) swatch
                Box(
                    modifier = Modifier
                        .offset(x = 13.dp, y = 13.dp)
                        .size(16.dp)
                        .background(Color.White)
                        .border(1.dp, Color(0xFF404040))
                        .padding(1.5.dp)
                        .background(bgColor)
                )
                // Front (FG) swatch overlapping top-left
                Box(
                    modifier = Modifier
                        .offset(x = 5.dp, y = 5.dp)
                        .size(16.dp)
                        .background(Color.White)
                        .border(1.dp, Color.Black)
                        .padding(1.5.dp)
                        .background(fgColor)
                )
            }

            Column(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
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
