package com.retrovibe.os.ui.windows

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.ExifInterface
import android.media.ImageReader
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Range
import android.util.Size as AndroidSize
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.model.PermittedPhoto
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.theme.textDark
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class CamAspect(val label: String, val portraitRatio: Float) {
    FOUR_THREE("4:3", 3f / 4f),
    SQUARE("1:1", 1f),
    WIDE("16:9", 9f / 16f)
}

private enum class CamRes(val label: String, val maxSide: Int) {
    MAX("Max", 4096),
    MED("Med", 1600),
    RETRO("Retro", 640)
}

private enum class CamQuality(val label: String, val jpegQuality: Int) {
    HIGH("High", 95),
    MED("Med", 80),
    LOW("Low", 55)
}

private data class BackLensOption(
    val cameraId: String,
    val label: String,
    val presetZoom: Float = 1f
)

private fun discoverBackLenses(manager: CameraManager): List<BackLensOption> {
    data class RawBackCam(val id: String, val eqFocal: Float, val minZoom: Float, val maxZoom: Float)
    val rawBack = mutableListOf<RawBackCam>()

    try {
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
            if (map.getOutputSizes(ImageFormat.JPEG).isNullOrEmpty()) continue

            val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 4.5f
            val sensorW = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width ?: 6.0f
            val eqFocal = if (sensorW > 0f) focal * 36f / sensorW else 26f

            var minZ = 1f
            var maxZ = (chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f).coerceIn(1f, 8f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val zRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                if (zRange != null) {
                    minZ = zRange.lower.coerceIn(0.4f, 1f)
                    maxZ = zRange.upper.coerceIn(1f, 8f)
                }
            }
            rawBack.add(RawBackCam(id, eqFocal, minZ, maxZ))
        }
    } catch (_: Throwable) {}

    if (rawBack.isEmpty()) return emptyList()

    // Case 1: Multiple distinct back camera IDs exposed in cameraIdList
    if (rawBack.size > 1) {
        val mainEq = rawBack.first().eqFocal.coerceAtLeast(1f)
        val usedLabels = mutableSetOf<String>()
        val result = rawBack.mapIndexed { idx, cam ->
            val ratio = cam.eqFocal / mainEq
            val baseLabel = when {
                idx == 0 -> "1x"
                ratio < 0.78f -> "${"%.1f".format(ratio.coerceAtLeast(0.5f))}x"
                ratio > 1.35f -> "${ratio.roundToInt()}x"
                else -> "Cam ${idx + 1}"
            }
            val finalLabel = if (baseLabel in usedLabels) "$baseLabel*" else baseLabel
            usedLabels.add(finalLabel)
            BackLensOption(cameraId = cam.id, label = finalLabel, presetZoom = 1f)
        }.sortedBy { opt ->
            val raw = rawBack.first { it.id == opt.cameraId }
            raw.eqFocal
        }
        return result
    }

    // Case 2: Single logical multi-camera (e.g. Pixel / Samsung / Xiaomi) using CONTROL_ZOOM_RATIO
    val main = rawBack.first()
    val lenses = mutableListOf<BackLensOption>()
    if (main.minZoom < 0.92f) {
        val uwLabel = "${"%.1f".format(main.minZoom)}x"
        lenses.add(BackLensOption(main.id, uwLabel, presetZoom = main.minZoom))
    }
    lenses.add(BackLensOption(main.id, "1x", presetZoom = 1f))
    if (main.maxZoom >= 2f) {
        lenses.add(BackLensOption(main.id, "2x", presetZoom = 2f))
    }
    if (main.maxZoom >= 3f) {
        lenses.add(BackLensOption(main.id, "3x", presetZoom = 3f))
    }
    return lenses
}

private fun applyPureCameraSettings(
    builder: CaptureRequest.Builder,
    characteristics: CameraCharacteristics?,
    evSteps: Int,
    zoomRatio: Float,
    cropRegion: Rect?
) {
    builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
    builder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_OFF)
    builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
    builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

    val aeRange: Range<Int>? = characteristics?.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
    if (aeRange != null) {
        builder.set(
            CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
            evSteps.coerceIn(aeRange.lower, aeRange.upper)
        )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val zRange = characteristics?.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        if (zRange != null) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio.coerceIn(zRange.lower, zRange.upper))
        } else if (cropRegion != null) {
            builder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
        }
    } else if (cropRegion != null) {
        builder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
    }

    val afModes = characteristics?.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
    if (afModes != null && afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
    }

    val edgeModes = characteristics?.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
    if (edgeModes == null || edgeModes.contains(CaptureRequest.EDGE_MODE_OFF)) {
        builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
    }

    val nrModes = characteristics?.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
    when {
        nrModes == null || nrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_OFF) ->
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
        nrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL) ->
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL)
    }

    val tonemapModes = characteristics?.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)
    if (tonemapModes != null && tonemapModes.contains(CaptureRequest.TONEMAP_MODE_FAST)) {
        builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_FAST)
    }
}

private fun chooseCaptureSize(sizes: Array<AndroidSize>?): AndroidSize {
    if (sizes.isNullOrEmpty()) return AndroidSize(1600, 1200)
    val capped = sizes.filter { maxOf(it.width, it.height) <= 4096 }.ifEmpty { sizes.toList() }
    val fourThree = capped.filter { abs(it.width.toFloat() / it.height.toFloat() - 4f / 3f) < 0.06f }
    val pool = fourThree.ifEmpty { capped }
    return pool.maxByOrNull { it.width.toLong() * it.height.toLong() } ?: sizes.first()
}

private fun choosePreviewSize(sizes: Array<AndroidSize>?, targetRatio: Float): AndroidSize {
    if (sizes.isNullOrEmpty()) return AndroidSize(1280, 960)
    val reasonable = sizes.filter { maxOf(it.width, it.height) in 640..1920 }.ifEmpty { sizes.toList() }
    val matching = reasonable.filter { abs(it.width.toFloat() / it.height.toFloat() - targetRatio) < 0.08f }
    val pool = matching.ifEmpty { reasonable }
    return pool.minByOrNull { abs(maxOf(it.width, it.height) - 1280) } ?: sizes.first()
}

private fun applyMirrorOnlyTransform(
    textureView: TextureView,
    isFront: Boolean,
    mirrorEnabled: Boolean
) {
    val w = textureView.width
    val h = textureView.height
    if (w <= 0 || h <= 0) return
    // Android TextureView mirrors front camera preview by default.
    // Never apply non-uniform scale here — TextureView is already sized to exact previewAspect!
    val flipHorizontal = if (isFront) !mirrorEnabled else mirrorEnabled
    val matrix = Matrix()
    if (flipHorizontal) {
        matrix.setScale(-1f, 1f, w / 2f, h / 2f)
    }
    textureView.setTransform(matrix)
    textureView.invalidate()
}

private fun drawRetroDateStamp(bitmap: Bitmap) {
    val c = AndroidCanvas(bitmap)
    val stamp = SimpleDateFormat("''yy  MM  dd", Locale.US).format(Date())
    val minSide = minOf(bitmap.width, bitmap.height).toFloat()
    val textSizePx = (minSide * 0.044f).coerceIn(18f, 120f)
    val margin = minSide * 0.045f

    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = textSizePx
        color = android.graphics.Color.argb(140, 255, 90, 15)
        textAlign = Paint.Align.RIGHT
        setShadowLayer(textSizePx * 0.25f, 0f, 0f, android.graphics.Color.rgb(255, 80, 0))
    }
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = textSizePx
        color = android.graphics.Color.rgb(255, 162, 54)
        textAlign = Paint.Align.RIGHT
    }

    val x = bitmap.width - margin
    val y = bitmap.height - margin
    c.drawText(stamp, x, y, glowPaint)
    c.drawText(stamp, x, y, corePaint)
}

private fun processCapturedJpeg(
    rawBytes: ByteArray,
    aspect: CamAspect,
    res: CamRes,
    quality: CamQuality,
    mirror: Boolean,
    dateStamp: Boolean,
    sensorOrientation: Int
): ByteArray {
    if (aspect == CamAspect.FOUR_THREE && res == CamRes.MAX && quality == CamQuality.HIGH && !mirror && !dateStamp) {
        return rawBytes
    }

    val exifRot = try {
        when (ExifInterface(ByteArrayInputStream(rawBytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (_: Throwable) {
        0f
    }

    val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return rawBytes
    val effectiveRot = if (exifRot == 0f && decoded.width > decoded.height && (sensorOrientation == 90 || sensorOrientation == 270)) {
        sensorOrientation.toFloat()
    } else {
        exifRot
    }
    var current = if (effectiveRot != 0f) {
        val m = Matrix().apply { postRotate(effectiveRot) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        if (rotated !== decoded) decoded.recycle()
        rotated
    } else {
        decoded
    }

    val targetRatio = aspect.portraitRatio
    val curRatio = current.width.toFloat() / current.height.toFloat()
    if (abs(curRatio - targetRatio) > 0.02f) {
        val cropW: Int
        val cropH: Int
        if (curRatio > targetRatio) {
            cropH = current.height
            cropW = (cropH * targetRatio).roundToInt().coerceIn(1, current.width)
        } else {
            cropW = current.width
            cropH = (cropW / targetRatio).roundToInt().coerceIn(1, current.height)
        }
        val left = (current.width - cropW) / 2
        val top = (current.height - cropH) / 2
        val cropped = Bitmap.createBitmap(current, left, top, cropW, cropH)
        if (cropped !== current) current.recycle()
        current = cropped
    }

    val maxCur = maxOf(current.width, current.height)
    val scaleFactor = if (maxCur > res.maxSide) res.maxSide.toFloat() / maxCur.toFloat() else 1f

    if (mirror || scaleFactor < 0.99f) {
        val m = Matrix().apply {
            postScale(
                if (mirror) -scaleFactor else scaleFactor,
                scaleFactor
            )
        }
        val transformed = Bitmap.createBitmap(current, 0, 0, current.width, current.height, m, true)
        if (transformed !== current) current.recycle()
        current = transformed
    }

    if (dateStamp) {
        val mutable = if (current.isMutable) current else {
            val copy = current.copy(Bitmap.Config.ARGB_8888, true)
            current.recycle()
            copy
        }
        drawRetroDateStamp(mutable)
        current = mutable
    }

    val out = ByteArrayOutputStream()
    current.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, out)
    current.recycle()
    return out.toByteArray()
}

private fun saveJpegToPhoneGallery(context: Context, bytes: ByteArray, filename: String): String? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RetroVibe Camera")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    return try {
        resolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
            out.flush()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
        uri.toString()
    } catch (_: Throwable) {
        try { resolver.delete(uri, null, null) } catch (_: Throwable) {}
        null
    }
}

private class PureCameraController(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onPhotoSaved: (uri: String, filename: String) -> Unit,
    private val onFlashAvailable: (Boolean) -> Unit,
    private val onZoomRangeKnown: (Float, Float) -> Unit,
    private val onPreviewAspectKnown: (Float) -> Unit
) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var characteristics: CameraCharacteristics? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private var activeArraySize: Rect? = null
    private var previewSize: AndroidSize = AndroidSize(1280, 960)
    private var sensorOrientation: Int = 90
    private var isFront: Boolean = false
    private var targetCameraId: String? = null
    private var textureView: TextureView? = null
    private var layoutListener: android.view.View.OnLayoutChangeListener? = null
    private var flashEnabled: Boolean = false
    private var mirrorEnabled: Boolean = false
    private var evSteps: Int = 0
    private var zoomRatio: Float = 1f
    private var minZoom: Float = 1f
    private var maxZoom: Float = 4f
    private var isCapturing: Boolean = false

    @Volatile private var optAspect: CamAspect = CamAspect.FOUR_THREE
    @Volatile private var optRes: CamRes = CamRes.MAX
    @Volatile private var optQuality: CamQuality = CamQuality.HIGH
    @Volatile private var optDateStamp: Boolean = false

    fun updateOptions(
        flash: Boolean,
        mirror: Boolean,
        aspect: CamAspect,
        res: CamRes,
        quality: CamQuality,
        dateStamp: Boolean,
        ev: Int,
        zoom: Float
    ) {
        val previewChanged = (ev != evSteps) || (abs(zoom - zoomRatio) > 0.01f)
        val mirrorChanged = (mirror != mirrorEnabled)
        flashEnabled = flash
        mirrorEnabled = mirror
        optAspect = aspect
        optRes = res
        optQuality = quality
        optDateStamp = dateStamp
        evSteps = ev
        zoomRatio = zoom.coerceIn(minZoom, maxZoom)

        if (mirrorChanged) {
            refreshPreviewTransform()
        }
        if (previewChanged) {
            updateRepeatingPreview()
        }
    }

    fun refreshPreviewTransform() {
        val tv = textureView ?: return
        tv.post {
            applyMirrorOnlyTransform(tv, isFront, mirrorEnabled)
        }
    }

    private fun currentCropRegion(): Rect? {
        val full = activeArraySize ?: return null
        if (zoomRatio <= 1.01f) return full
        val cropW = (full.width() / zoomRatio).roundToInt().coerceAtLeast(1)
        val cropH = (full.height() / zoomRatio).roundToInt().coerceAtLeast(1)
        val left = (full.width() - cropW) / 2
        val top = (full.height() - cropH) / 2
        return Rect(left, top, left + cropW, top + cropH)
    }

    private fun updateRepeatingPreview() {
        val session = captureSession ?: return
        val builder = previewBuilder ?: return
        val handler = bgHandler ?: return
        handler.post {
            try {
                applyPureCameraSettings(builder, characteristics, evSteps, zoomRatio, currentCropRegion())
                session.setRepeatingRequest(builder.build(), null, handler)
            } catch (_: Throwable) {}
        }
    }

    fun tapToFocus(normX: Float, normY: Float) {
        val session = captureSession ?: return
        val builder = previewBuilder ?: return
        val handler = bgHandler ?: return
        val full = activeArraySize ?: return
        val chars = characteristics ?: return

        handler.post {
            try {
                // Convert normalized portrait viewfinder tap (0..1, 0..1) into sensor array coordinates
                val adjX = if (mirrorEnabled) 1f - normX else normX
                val (sxNorm, syNorm) = when (sensorOrientation) {
                    90 -> normY to (1f - adjX)
                    270 -> (1f - normY) to adjX
                    180 -> (1f - adjX) to (1f - normY)
                    else -> adjX to normY
                }
                val region = currentCropRegion() ?: full
                val cx = (region.left + sxNorm.coerceIn(0f, 1f) * region.width()).roundToInt()
                val cy = (region.top + syNorm.coerceIn(0f, 1f) * region.height()).roundToInt()
                val half = (minOf(region.width(), region.height()) * 0.08f).roundToInt().coerceAtLeast(40)
                val focusRect = Rect(
                    (cx - half).coerceIn(full.left, full.right - 1),
                    (cy - half).coerceIn(full.top, full.bottom - 1),
                    (cx + half).coerceIn(full.left + 1, full.right),
                    (cy + half).coerceIn(full.top + 1, full.bottom)
                )
                val metering = MeteringRectangle(focusRect, MeteringRectangle.METERING_WEIGHT_MAX - 1)

                val maxAf = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
                val maxAe = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0

                if (maxAf > 0) {
                    builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(metering))
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                    builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                }
                if (maxAe > 0) {
                    builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(metering))
                }
                session.capture(builder.build(), null, handler)

                // Resume normal preview repeating request with focused region
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                session.setRepeatingRequest(builder.build(), null, handler)
                onStatus("Focused")
            } catch (_: Throwable) {}
        }
    }

    fun start(view: TextureView, useFront: Boolean, specificBackCameraId: String?) {
        stop()
        textureView = view
        isFront = useFront
        targetCameraId = specificBackCameraId
        val thread = HandlerThread("RetroPureCamera").apply { start() }
        bgThread = thread
        bgHandler = Handler(thread.looper)

        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                openCamera()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                applyMirrorOnlyTransform(view, isFront, mirrorEnabled)
            }
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }
        val listener = android.view.View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v === textureView && v.width > 0 && v.height > 0) {
                applyMirrorOnlyTransform(view, isFront, mirrorEnabled)
            }
        }
        layoutListener = listener
        view.addOnLayoutChangeListener(listener)

        if (view.isAvailable) {
            openCamera()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        try {
            val camId = if (!isFront && targetCameraId != null) {
                targetCameraId
            } else {
                val desiredFacing = if (isFront) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
                manager.cameraIdList.firstOrNull { id ->
                    manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == desiredFacing
                } ?: manager.cameraIdList.firstOrNull()
            }

            if (camId == null) {
                onStatus("No camera found")
                return
            }

            val chars = manager.getCameraCharacteristics(camId)
            characteristics = chars
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            activeArraySize = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

            minZoom = 1f
            maxZoom = (chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f).coerceIn(1f, 6f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val zRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                if (zRange != null) {
                    minZoom = zRange.lower.coerceIn(0.4f, 1f)
                    maxZoom = zRange.upper.coerceIn(1f, 6f)
                }
            }
            zoomRatio = zoomRatio.coerceIn(minZoom, maxZoom)
            onZoomRangeKnown(minZoom, maxZoom)

            val hasFlash = !isFront && (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true)
            onFlashAvailable(hasFlash)

            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val captureSize = chooseCaptureSize(map?.getOutputSizes(ImageFormat.JPEG))
            val ratio = captureSize.width.toFloat() / captureSize.height.toFloat()
            previewSize = choosePreviewSize(map?.getOutputSizes(SurfaceTexture::class.java), ratio)

            val swapped = sensorOrientation == 90 || sensorOrientation == 270
            val bufW = if (swapped) previewSize.height.toFloat() else previewSize.width.toFloat()
            val bufH = if (swapped) previewSize.width.toFloat() else previewSize.height.toFloat()
            onPreviewAspectKnown((bufW / bufH).coerceIn(0.4f, 2.5f))

            textureView?.let { tv ->
                tv.post {
                    applyMirrorOnlyTransform(tv, isFront, mirrorEnabled)
                }
            }

            imageReader?.close()
            imageReader = ImageReader.newInstance(captureSize.width, captureSize.height, ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener({ reader ->
                    val img = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val buffer = img.planes[0].buffer
                        val rawBytes = ByteArray(buffer.remaining())
                        buffer.get(rawBytes)
                        val finalBytes = processCapturedJpeg(
                            rawBytes = rawBytes,
                            aspect = optAspect,
                            res = optRes,
                            quality = optQuality,
                            mirror = mirrorEnabled,
                            dateStamp = optDateStamp,
                            sensorOrientation = sensorOrientation
                        )
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val filename = "RV_$stamp.jpg"
                        val savedUri = saveJpegToPhoneGallery(context, finalBytes, filename)
                        if (savedUri != null) {
                            onPhotoSaved(savedUri, filename)
                        } else {
                            onStatus("Could not save photo")
                        }
                    } catch (t: Throwable) {
                        onStatus("Capture error: ${t.message}")
                    } finally {
                        img.close()
                        isCapturing = false
                    }
                }, bgHandler)
            }

            manager.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createPreviewSession()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    onStatus("Camera error ($error)")
                }
            }, bgHandler)
        } catch (t: Throwable) {
            onStatus("Camera unavailable: ${t.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun createPreviewSession() {
        val device = cameraDevice ?: return
        val tv = textureView ?: return
        val st = tv.surfaceTexture ?: return
        val reader = imageReader ?: return

        try {
            st.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface = Surface(st)
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                applyPureCameraSettings(this, characteristics, evSteps, zoomRatio, currentCropRegion())
            }
            previewBuilder = builder

            device.createCaptureSession(
                listOf(previewSurface, reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            session.setRepeatingRequest(builder.build(), null, bgHandler)
                            onStatus("Ready")
                        } catch (t: Throwable) {
                            onStatus("Preview error: ${t.message}")
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onStatus("Failed to start camera preview")
                    }
                },
                bgHandler
            )
        } catch (t: Throwable) {
            onStatus("Session error: ${t.message}")
        }
    }

    fun takePhoto() {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val reader = imageReader ?: return
        if (isCapturing) return
        isCapturing = true
        onStatus("Capturing...")

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                applyPureCameraSettings(this, characteristics, evSteps, zoomRatio, currentCropRegion())
                set(CaptureRequest.JPEG_QUALITY, optQuality.jpegQuality.toByte())
                set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)
                if (flashEnabled && !isFront) {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                } else {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
            }
            session.capture(
                captureBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureFailed(
                        s: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                    ) {
                        isCapturing = false
                        onStatus("Capture failed")
                    }
                },
                bgHandler
            )
        } catch (t: Throwable) {
            isCapturing = false
            onStatus("Capture error: ${t.message}")
        }
    }

    fun stop() {
        layoutListener?.let { l -> textureView?.removeOnLayoutChangeListener(l) }
        layoutListener = null
        try { captureSession?.close() } catch (_: Throwable) {}
        captureSession = null
        previewBuilder = null
        try { cameraDevice?.close() } catch (_: Throwable) {}
        cameraDevice = null
        try { imageReader?.close() } catch (_: Throwable) {}
        imageReader = null
        bgThread?.quitSafely()
        bgThread = null
        bgHandler = null
        isCapturing = false
    }
}

@Composable
fun CameraWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val camManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val backLenses = remember { discoverBackLenses(camManager) }
    val defaultLensIdx = remember(backLenses) {
        backLenses.indexOfFirst { it.label == "1x" }.takeIf { it >= 0 } ?: 0
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var useFront by remember { mutableStateOf(false) }
    var selectedBackLensIdx by remember { mutableIntStateOf(defaultLensIdx) }
    var flashOn by remember { mutableStateOf(false) }
    var flashAvailable by remember { mutableStateOf(false) }
    var mirrorEnabled by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(CamAspect.FOUR_THREE) }
    var res by remember { mutableStateOf(CamRes.MAX) }
    var quality by remember { mutableStateOf(CamQuality.HIGH) }
    var dateStamp by remember { mutableStateOf(false) }
    var timerSec by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var showGrid by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var evSteps by remember { mutableIntStateOf(0) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(4f) }
    var previewAspect by remember { mutableFloatStateOf(3f / 4f) }

    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusSeq by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("Ready") }
    var lastPhotoUri by remember { mutableStateOf(vm.permittedPhotos.firstOrNull()?.uri) }
    var shutterFlash by remember { mutableStateOf(false) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

    val activeBackLens = backLenses.getOrNull(selectedBackLensIdx)
    val activeBackCameraId = activeBackLens?.cameraId

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) statusText = "Camera permission required"
    }

    val controller = remember {
        PureCameraController(
            context = context,
            onStatus = { msg ->
                android.os.Handler(android.os.Looper.getMainLooper()).post { statusText = msg }
            },
            onPhotoSaved = { uri, filename ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    vm.addPermittedPhotos(
                        listOf(
                            PermittedPhoto(
                                id = "cam-${System.currentTimeMillis()}",
                                uri = uri,
                                filename = filename
                            )
                        )
                    )
                    lastPhotoUri = uri
                    statusText = "Saved $filename"
                    RetroSoundEngine.play(RetroSound.SUCCESS, vm.soundEnabled)
                }
            },
            onFlashAvailable = { avail ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    flashAvailable = avail
                    if (!avail) flashOn = false
                }
            },
            onZoomRangeKnown = { minZ, maxZ ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    minZoom = minZ
                    maxZoom = maxZ
                    zoomRatio = zoomRatio.coerceIn(minZ, maxZ)
                }
            },
            onPreviewAspectKnown = { asp ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    previewAspect = asp
                }
            }
        )
    }

    LaunchedEffect(shutterFlash) {
        if (shutterFlash) {
            delay(110)
            shutterFlash = false
        }
    }

    LaunchedEffect(focusSeq) {
        if (focusSeq > 0) {
            delay(1100)
            focusPoint = null
        }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
            delay(1000)
            if (countdown == 1) {
                countdown = 0
                shutterFlash = true
                controller.takePhoto()
            } else {
                countdown -= 1
            }
        }
    }

    LaunchedEffect(flashOn, mirrorEnabled, aspect, res, quality, dateStamp, evSteps, zoomRatio) {
        controller.updateOptions(flashOn, mirrorEnabled, aspect, res, quality, dateStamp, evSteps, zoomRatio)
    }

    LaunchedEffect(aspect) {
        controller.refreshPreviewTransform()
    }

    DisposableEffect(hasPermission, useFront, activeBackCameraId, textureViewRef) {
        val tv = textureViewRef
        if (hasPermission && tv != null) {
            controller.start(tv, useFront, activeBackCameraId)
        }
        onDispose {
            controller.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Classic Win98 Camera Top Toolbar
        BevelBox(theme = palette, raised = true, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RetroButton(
                    label = if (useFront) "Back" else "Front",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    onClick = {
                        useFront = !useFront
                        zoomRatio = 1f
                    }
                )
                RetroButton(
                    label = if (mirrorEnabled) "Mirror: ON" else "Mirror",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    active = mirrorEnabled,
                    onClick = { mirrorEnabled = !mirrorEnabled }
                )
                if (flashAvailable && !useFront) {
                    RetroButton(
                        label = if (flashOn) "Flash: ON" else "Flash",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        active = flashOn,
                        onClick = { flashOn = !flashOn }
                    )
                }
                RetroButton(
                    label = "Options",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    active = showSettings,
                    onClick = { showSettings = !showSettings }
                )
                RetroButton(
                    label = "Photos",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    onClick = { vm.openWindow("gallery") }
                )
            }
        }

        // Collapsible Win98 Camera Options Panel
        if (showSettings) {
            BevelBox(theme = palette, raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Aspect Ratio + Size
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Frame:", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = palette.textSecondary)
                        CamAspect.entries.forEach { a ->
                            RetroButton(
                                label = a.label,
                                theme = palette,
                                soundEnabled = vm.soundEnabled,
                                active = aspect == a,
                                onClick = { aspect = a }
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Size:", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = palette.textSecondary)
                        CamRes.entries.forEach { r ->
                            RetroButton(
                                label = r.label,
                                theme = palette,
                                soundEnabled = vm.soundEnabled,
                                active = res == r,
                                onClick = { res = r }
                            )
                        }
                    }

                    // Quality + Timer + Grid + Date Stamp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Qual:", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = palette.textSecondary)
                        CamQuality.entries.forEach { q ->
                            RetroButton(
                                label = q.label,
                                theme = palette,
                                soundEnabled = vm.soundEnabled,
                                active = quality == q,
                                onClick = { quality = q }
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        RetroButton(
                            label = if (timerSec == 0) "Timer" else "${timerSec}s",
                            theme = palette,
                            soundEnabled = vm.soundEnabled,
                            active = timerSec > 0,
                            onClick = {
                                timerSec = when (timerSec) {
                                    0 -> 3
                                    3 -> 10
                                    else -> 0
                                }
                            }
                        )
                        RetroButton(
                            label = "Grid",
                            theme = palette,
                            soundEnabled = vm.soundEnabled,
                            active = showGrid,
                            onClick = { showGrid = !showGrid }
                        )
                        RetroButton(
                            label = "Date",
                            theme = palette,
                            soundEnabled = vm.soundEnabled,
                            active = dateStamp,
                            onClick = { dateStamp = !dateStamp }
                        )
                    }

                    // Exposure (+/- Light) & Zoom Reset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Light:", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = palette.textSecondary)
                        RetroButton(
                            label = "-",
                            theme = palette,
                            soundEnabled = vm.soundEnabled,
                            onClick = { evSteps = (evSteps - 2).coerceAtLeast(-6) }
                        )
                        Text(
                            text = if (evSteps > 0) "+$evSteps" else "$evSteps",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            modifier = Modifier.width(24.dp),
                            textAlign = TextAlign.Center
                        )
                        RetroButton(
                            label = "+",
                            theme = palette,
                            soundEnabled = vm.soundEnabled,
                            onClick = { evSteps = (evSteps + 2).coerceAtMost(6) }
                        )
                        if (evSteps != 0) {
                            RetroButton(
                                label = "0",
                                theme = palette,
                                soundEnabled = vm.soundEnabled,
                                onClick = { evSteps = 0 }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (abs(zoomRatio - 1f) > 0.05f) {
                            RetroButton(
                                label = "1x",
                                theme = palette,
                                soundEnabled = vm.soundEnabled,
                                onClick = { zoomRatio = 1f }
                            )
                        }
                    }
                }
            }
        }

        // Full-Frame Exact-Aspect Viewfinder inside classic sunken Win98 bezel
        BevelBox(
            theme = palette,
            raised = false,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (hasPermission) palette.borderDeep.copy(alpha = 0.85f) else palette.windowSurface),
                contentAlignment = Alignment.Center
            ) {
                if (!hasPermission) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        PixelIcon("camera", 48.dp)
                        Text(
                            "RetroVibe Camera",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            "Saves directly to your Phone Gallery\nand My Pictures",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        RetroButton(
                            label = "Allow Camera",
                            theme = palette,
                            soundEnabled = vm.soundEnabled,
                            active = true,
                            onClick = { permLauncher.launch(Manifest.permission.CAMERA) }
                        )
                    }
                } else {
                    val targetRatio = aspect.portraitRatio
                    val boxAspect = if (maxHeight > 0.dp) maxWidth / maxHeight else targetRatio
                    val frameW = if (boxAspect > targetRatio) maxHeight * targetRatio else maxWidth
                    val frameH = if (boxAspect > targetRatio) maxHeight else maxWidth / targetRatio
                    val frameWPx = with(density) { frameW.toPx() }
                    val frameHPx = with(density) { frameH.toPx() }

                    // Size TextureView to EXACT previewAspect covering frameW x frameH (mathematically impossible to warp/squish!)
                    val tvW = if (previewAspect > targetRatio) frameH * previewAspect else frameW
                    val tvH = if (previewAspect > targetRatio) frameH else frameW / previewAspect

                    Box(
                        modifier = Modifier
                            .size(frameW, frameH)
                            .border(1.dp, palette.borderLight.copy(alpha = 0.65f))
                            .clipToBounds()
                            .pointerInput(minZoom, maxZoom, frameWPx, frameHPx) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val startPos = down.position
                                    var multiTouch = false
                                    var moved = false
                                    val slop = viewConfiguration.touchSlop

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) {
                                            event.changes.forEach { it.consume() }
                                            break
                                        }
                                        if (pressed.size >= 2) {
                                            multiTouch = true
                                            val z = event.calculateZoom()
                                            if (z != 1f) {
                                                zoomRatio = (zoomRatio * z).coerceIn(minZoom, maxZoom)
                                            }
                                            event.changes.forEach { it.consume() }
                                        } else if (!multiTouch) {
                                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if ((ch.position - startPos).getDistance() > slop) {
                                                moved = true
                                            }
                                            ch.consume()
                                        } else {
                                            event.changes.forEach { it.consume() }
                                        }
                                    }

                                    // Single clean tap -> Tap-to-Focus!
                                    if (!multiTouch && !moved && frameWPx > 0f && frameHPx > 0f) {
                                        focusPoint = startPos
                                        focusSeq++
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        controller.tapToFocus(
                                            (startPos.x / frameWPx).coerceIn(0f, 1f),
                                            (startPos.y / frameHPx).coerceIn(0f, 1f)
                                        )
                                    }
                                }
                            }
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                TextureView(ctx).also { tv ->
                                    textureViewRef = tv
                                }
                            },
                            update = { tv ->
                                tv.post { controller.refreshPreviewTransform() }
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .requiredSize(tvW, tvH)
                        )

                        // Optional Rule-of-Thirds Grid
                        if (showGrid) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val lineColor = Color.White.copy(alpha = 0.45f)
                                drawLine(lineColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 1.5f)
                                drawLine(lineColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 1.5f)
                                drawLine(lineColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 1.5f)
                                drawLine(lineColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 1.5f)
                            }
                        }

                        // Tap-to-Focus green retro square
                        val fp = focusPoint
                        if (fp != null) {
                            val halfPx = with(density) { 22.dp.toPx() }
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (fp.x - halfPx).roundToInt(),
                                            (fp.y - halfPx).roundToInt()
                                        )
                                    }
                                    .size(44.dp)
                                    .border(2.dp, Color(0xFF4AE87A))
                            )
                        }

                        // Subtle zoom readout when zoomed
                        if (abs(zoomRatio - 1f) > 0.05f) {
                            Text(
                                text = "${"%.1f".format(zoomRatio)}x",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        // Optional retro orange film date stamp preview
                        if (dateStamp) {
                            val stampPreview = remember { SimpleDateFormat("''yy  MM  dd", Locale.US).format(Date()) }
                            Text(
                                text = stampPreview,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFA236),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            )
                        }

                        // Self-timer big retro countdown
                        if (countdown > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = countdown.toString(),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 54.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }

                        // Shutter flash overlay
                        if (shutterFlash) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = 0.85f))
                            )
                        }
                    }
                }
            }
        }

        // Back Camera Lens Switcher Row (automatically adapts to all detected back lenses!)
        if (hasPermission && !useFront && backLenses.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                backLenses.forEachIndexed { idx, lens ->
                    val isSelected = if (backLenses.map { it.cameraId }.distinct().size > 1) {
                        idx == selectedBackLensIdx
                    } else {
                        abs(zoomRatio - lens.presetZoom) < 0.25f
                    }
                    RetroButton(
                        label = lens.label,
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        active = isSelected,
                        onClick = {
                            selectedBackLensIdx = idx
                            zoomRatio = lens.presetZoom.coerceIn(minZoom, maxZoom)
                        }
                    )
                    if (idx < backLenses.lastIndex) Spacer(Modifier.width(4.dp))
                }
            }
        }

        // Bottom Controls: Last photo preview + Responsive Win98 Shutter Button
        BevelBox(theme = palette, raised = true, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BevelBox(
                    theme = palette,
                    raised = false,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                            vm.openWindow("gallery")
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (lastPhotoUri != null) {
                            AsyncImage(
                                model = lastPhotoUri,
                                contentDescription = "Last photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            PixelIcon("gallery", 20.dp)
                        }
                    }
                }

                RetroButton(
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    active = true,
                    enabled = hasPermission,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    onClick = {
                        if (countdown > 0) {
                            countdown = 0
                            statusText = "Timer canceled"
                        } else if (timerSec > 0) {
                            countdown = timerSec
                            statusText = "Timer ${timerSec}s..."
                        } else {
                            shutterFlash = true
                            controller.takePhoto()
                        }
                    }
                ) {
                    PixelIcon("camera", 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            countdown > 0 -> "CANCEL (${countdown}s)"
                            timerSec > 0 -> "SNAP (${timerSec}s)"
                            else -> "SNAP PHOTO"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textDark
                    )
                }
            }
        }

        // Clean Win98 Status Bar
        BevelBox(theme = palette, raised = false, modifier = Modifier.fillMaxWidth()) {
            Text(
                "$statusText  •  ${aspect.label} ${res.label} ${quality.label}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = palette.textDark,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
