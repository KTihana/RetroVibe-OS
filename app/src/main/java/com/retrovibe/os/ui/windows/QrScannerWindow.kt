package com.retrovibe.os.ui.windows

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size as AndroidSize
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.theme.textDark
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private fun isLikelyUrl(text: String): Boolean {
    val t = text.trim()
    return t.startsWith("http://", ignoreCase = true) ||
        t.startsWith("https://", ignoreCase = true) ||
        t.startsWith("www.", ignoreCase = true)
}

private enum class FocusVisualState {
    IDLE,
    FOCUSING,
    LOCKED
}

private class QrCameraController(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onQrDetected: (String) -> Unit,
    private val onFlashAvailable: (Boolean) -> Unit,
    private val onPreviewAspectKnown: (Float) -> Unit,
    private val onZoomRangeKnown: (Float, Float) -> Unit,
    private val onFocusStateChanged: (FocusVisualState) -> Unit
) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private var characteristics: CameraCharacteristics? = null
    private var activeArraySize: Rect? = null
    private var previewSize: AndroidSize = AndroidSize(1280, 960)
    private var sensorOrientation: Int = 90
    private var textureView: TextureView? = null
    private var torchEnabled: Boolean = false
    private var minZoom: Float = 1f
    private var maxZoom: Float = 4f
    private var zoomRatio: Float = 1f
    private var afResetRunnable: Runnable? = null
    @Volatile private var waitingForAfLock: Boolean = false
    @Volatile private var paused: Boolean = false
    private var lastDecodeMs: Long = 0L

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    private val afCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            if (!waitingForAfLock) return
            val afState = result.get(CaptureResult.CONTROL_AF_STATE) ?: return
            when (afState) {
                CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> {
                    waitingForAfLock = false
                    onFocusStateChanged(FocusVisualState.LOCKED)
                    if (!paused) onStatus("Focused — point at QR code")
                }
                CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> {
                    waitingForAfLock = false
                    onFocusStateChanged(FocusVisualState.LOCKED)
                    if (!paused) onStatus("Tap again to refocus")
                }
            }
        }
    }

    fun setPaused(value: Boolean) {
        paused = value
    }

    fun setTorch(enabled: Boolean) {
        torchEnabled = enabled
        val session = captureSession ?: return
        val builder = previewBuilder ?: return
        val handler = bgHandler ?: return
        handler.post {
            try {
                builder.set(
                    CaptureRequest.FLASH_MODE,
                    if (enabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
                )
                session.setRepeatingRequest(builder.build(), afCaptureCallback, handler)
            } catch (_: Throwable) {}
        }
    }

    fun setZoom(zoom: Float) {
        val clamped = zoom.coerceIn(minZoom, maxZoom)
        if (abs(clamped - zoomRatio) < 0.01f) return
        zoomRatio = clamped
        val session = captureSession ?: return
        val builder = previewBuilder ?: return
        val handler = bgHandler ?: return
        handler.post {
            try {
                applyZoomToBuilder(builder)
                session.setRepeatingRequest(builder.build(), afCaptureCallback, handler)
            } catch (_: Throwable) {}
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

    private fun applyZoomToBuilder(builder: CaptureRequest.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val zRange = characteristics?.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            if (zRange != null) {
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio.coerceIn(zRange.lower, zRange.upper))
                return
            }
        }
        currentCropRegion()?.let { builder.set(CaptureRequest.SCALER_CROP_REGION, it) }
    }

    fun tapToFocus(normX: Float, normY: Float) {
        val session = captureSession ?: return
        val builder = previewBuilder ?: return
        val handler = bgHandler ?: return
        val full = activeArraySize ?: return
        val chars = characteristics ?: return

        handler.post {
            try {
                // Cancel any pending return to continuous AF
                afResetRunnable?.let { handler.removeCallbacks(it) }

                val (sxNorm, syNorm) = when (sensorOrientation) {
                    90 -> normY to (1f - normX)
                    270 -> (1f - normY) to normX
                    180 -> (1f - normX) to (1f - normY)
                    else -> normX to normY
                }
                val region = currentCropRegion() ?: full
                val cx = (region.left + sxNorm.coerceIn(0f, 1f) * region.width()).roundToInt()
                val cy = (region.top + syNorm.coerceIn(0f, 1f) * region.height()).roundToInt()
                val half = (minOf(region.width(), region.height()) * 0.085f).roundToInt().coerceAtLeast(48)
                val focusRect = Rect(
                    (cx - half).coerceIn(full.left, full.right - 1),
                    (cy - half).coerceIn(full.top, full.bottom - 1),
                    (cx + half).coerceIn(full.left + 1, full.right),
                    (cy + half).coerceIn(full.top + 1, full.bottom)
                )
                val metering = MeteringRectangle(focusRect, MeteringRectangle.METERING_WEIGHT_MAX - 1)

                val maxAf = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
                val maxAe = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0
                val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()

                // Step 1: Cancel any existing AF lock so the HAL actually runs a fresh focus sweep
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
                session.capture(builder.build(), null, handler)

                // Step 2: Set metering regions + AUTO AF mode + START trigger
                if (maxAf > 0) {
                    builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(metering))
                }
                if (maxAe > 0) {
                    builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(metering))
                }
                if (afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO)) {
                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                }
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)

                waitingForAfLock = true
                onFocusStateChanged(FocusVisualState.FOCUSING)
                if (!paused) onStatus("Focusing...")

                // Fire AF trigger capture, then hold repeating preview with IDLE trigger
                session.capture(builder.build(), afCaptureCallback, handler)
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                session.setRepeatingRequest(builder.build(), afCaptureCallback, handler)

                // Step 3: Automatically return to continuous autofocus after 4.5s so moving to a new QR works smoothly
                val resetTask = Runnable {
                    try {
                        val s = captureSession ?: return@Runnable
                        val b = previewBuilder ?: return@Runnable
                        waitingForAfLock = false
                        if (afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }
                        if (maxAf > 0) {
                            b.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(MeteringRectangle(0, 0, 0, 0, 0)))
                        }
                        if (maxAe > 0) {
                            b.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(MeteringRectangle(0, 0, 0, 0, 0)))
                        }
                        b.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                        s.setRepeatingRequest(b.build(), afCaptureCallback, handler)
                    } catch (_: Throwable) {}
                }
                afResetRunnable = resetTask
                handler.postDelayed(resetTask, 4500L)
            } catch (_: Throwable) {}
        }
    }

    fun start(view: TextureView) {
        stop()
        textureView = view
        paused = false
        val thread = HandlerThread("RetroQrScanner").apply { start() }
        bgThread = thread
        bgHandler = Handler(thread.looper)

        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                openCamera()
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }

        if (view.isAvailable) {
            openCamera()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        try {
            val camId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull()

            if (camId == null) {
                onStatus("No camera found")
                return
            }

            val chars = manager.getCameraCharacteristics(camId)
            characteristics = chars
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            activeArraySize = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            onFlashAvailable(chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true)

            minZoom = 1f
            maxZoom = (chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f).coerceIn(1f, 4f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val zRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                if (zRange != null) {
                    minZoom = zRange.lower.coerceIn(0.6f, 1f)
                    maxZoom = zRange.upper.coerceIn(1f, 4f)
                }
            }
            zoomRatio = zoomRatio.coerceIn(minZoom, maxZoom)
            onZoomRangeKnown(minZoom, maxZoom)

            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val prevSizes = map?.getOutputSizes(SurfaceTexture::class.java)
            previewSize = prevSizes
                ?.filter { maxOf(it.width, it.height) in 640..1920 }
                ?.minByOrNull { abs(maxOf(it.width, it.height) - 1280) }
                ?: prevSizes?.firstOrNull()
                ?: AndroidSize(1280, 960)

            val swapped = sensorOrientation == 90 || sensorOrientation == 270
            val bufW = if (swapped) previewSize.height.toFloat() else previewSize.width.toFloat()
            val bufH = if (swapped) previewSize.width.toFloat() else previewSize.height.toFloat()
            onPreviewAspectKnown((bufW / bufH).coerceIn(0.4f, 2.5f))

            val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888)
            val scanSize = yuvSizes
                ?.filter { maxOf(it.width, it.height) in 720..1440 }
                ?.minByOrNull { abs(maxOf(it.width, it.height) - 1280) }
                ?: yuvSizes?.filter { maxOf(it.width, it.height) in 480..1280 }?.maxByOrNull { it.width * it.height }
                ?: AndroidSize(960, 720)

            imageReader?.close()
            imageReader = ImageReader.newInstance(scanSize.width, scanSize.height, ImageFormat.YUV_420_888, 2).apply {
                setOnImageAvailableListener({ r ->
                    val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val now = System.currentTimeMillis()
                        if (paused || now - lastDecodeMs < 130L) return@setOnImageAvailableListener
                        lastDecodeMs = now

                        val plane = img.planes[0]
                        val w = img.width
                        val h = img.height
                        val rowStride = plane.rowStride
                        val buf = plane.buffer
                        val yData = ByteArray(w * h)
                        if (rowStride == w) {
                            buf.get(yData, 0, w * h)
                        } else {
                            val rowBuf = ByteArray(rowStride)
                            for (row in 0 until h) {
                                val remaining = buf.remaining()
                                if (remaining <= 0) break
                                val toRead = minOf(rowStride, remaining)
                                buf.get(rowBuf, 0, toRead)
                                System.arraycopy(rowBuf, 0, yData, row * w, minOf(w, toRead))
                            }
                        }

                        val source = PlanarYUVLuminanceSource(yData, w, h, 0, 0, w, h, false)
                        val result = try {
                            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
                        } catch (_: Throwable) {
                            try {
                                reader.reset()
                                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert())))
                            } catch (_: Throwable) {
                                null
                            }
                        } finally {
                            reader.reset()
                        }

                        val decoded = result?.text?.trim()
                        if (!decoded.isNullOrEmpty()) {
                            paused = true
                            onQrDetected(decoded)
                        }
                    } finally {
                        img.close()
                    }
                }, bgHandler)
            }

            manager.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createSession(chars)
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
            onStatus("Scanner unavailable: ${t.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun createSession(chars: CameraCharacteristics) {
        val device = cameraDevice ?: return
        val tv = textureView ?: return
        val st = tv.surfaceTexture ?: return
        val reader = imageReader ?: return

        try {
            st.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface = Surface(st)
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                if (afModes != null && afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }
                applyZoomToBuilder(this)
                if (torchEnabled) {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
            }
            previewBuilder = builder

            device.createCaptureSession(
                listOf(previewSurface, reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            session.setRepeatingRequest(builder.build(), afCaptureCallback, bgHandler)
                            onStatus("Point at QR code · Tap screen to focus")
                        } catch (t: Throwable) {
                            onStatus("Preview error: ${t.message}")
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onStatus("Failed to start QR scanner")
                    }
                },
                bgHandler
            )
        } catch (t: Throwable) {
            onStatus("Session error: ${t.message}")
        }
    }

    fun stop() {
        afResetRunnable?.let { bgHandler?.removeCallbacks(it) }
        afResetRunnable = null
        waitingForAfLock = false
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
    }
}

@Composable
fun QrScannerWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var torchOn by remember { mutableStateOf(false) }
    var flashAvailable by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Initializing QR Scanner...") }
    var scannedResult by remember { mutableStateOf<String?>(null) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

    var previewAspect by remember { mutableStateOf(0.75f) }
    var minZoom by remember { mutableStateOf(1f) }
    var maxZoom by remember { mutableStateOf(4f) }
    var zoomRatio by remember { mutableStateOf(1f) }

    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusSeq by remember { mutableStateOf(0) }
    var focusState by remember { mutableStateOf(FocusVisualState.IDLE) }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) statusText = "Camera permission required"
    }

    val controller = remember {
        QrCameraController(
            context = context,
            onStatus = { msg ->
                android.os.Handler(android.os.Looper.getMainLooper()).post { statusText = msg }
            },
            onQrDetected = { code ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    RetroSoundEngine.play(RetroSound.SUCCESS, vm.soundEnabled)
                    scannedResult = code
                    if (isLikelyUrl(code)) {
                        statusText = "Opening in Internet Voyager..."
                        vm.openBrowserUrl(code)
                    } else {
                        statusText = "QR Code scanned!"
                    }
                }
            },
            onFlashAvailable = { avail ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    flashAvailable = avail
                    if (!avail) torchOn = false
                }
            },
            onPreviewAspectKnown = { asp ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    previewAspect = asp
                }
            },
            onZoomRangeKnown = { mn, mx ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    minZoom = mn
                    maxZoom = mx
                    zoomRatio = zoomRatio.coerceIn(mn, mx)
                }
            },
            onFocusStateChanged = { st ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    focusState = st
                }
            }
        )
    }

    LaunchedEffect(torchOn) {
        controller.setTorch(torchOn)
    }

    LaunchedEffect(zoomRatio) {
        controller.setZoom(zoomRatio)
    }

    LaunchedEffect(focusSeq) {
        if (focusSeq > 0) {
            delay(1600L)
            focusPoint = null
            focusState = FocusVisualState.IDLE
        }
    }

    DisposableEffect(hasPermission, textureViewRef) {
        val tv = textureViewRef
        if (hasPermission && tv != null) {
            controller.start(tv)
        }
        onDispose {
            controller.stop()
        }
    }

    val scanAnim = rememberInfiniteTransition(label = "qrLaser")
    val laserProgress by scanAnim.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "qrLaserY"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Classic Win98 Top Toolbar
        BevelBox(theme = palette, raised = true, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (flashAvailable) {
                    RetroButton(
                        label = if (torchOn) "Light: ON" else "Light: OFF",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        active = torchOn,
                        onClick = { torchOn = !torchOn }
                    )
                }
                if (abs(zoomRatio - 1f) > 0.05f) {
                    RetroButton(
                        label = "${"%.1f".format(zoomRatio)}x",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        active = true,
                        onClick = { zoomRatio = 1f }
                    )
                }
                if (scannedResult != null) {
                    RetroButton(
                        label = "Scan Again",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        active = true,
                        onClick = {
                            scannedResult = null
                            statusText = "Point at QR code · Tap screen to focus"
                            controller.setPaused(false)
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
                RetroButton(
                    label = "Voyager",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    onClick = { vm.openWindow("browser") }
                )
            }
        }

        // Viewfinder inside classic sunken Win98 bevel frame
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
                    .background(if (hasPermission) Color(0xFF141820) else palette.windowSurface)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                if (!hasPermission) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        PixelIcon("qr", 48.dp)
                        Text(
                            "RetroVibe QR Scanner",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            "Scanned links open directly inside\nInternet Voyager",
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
                    val boxW = maxWidth
                    val boxH = maxHeight
                    val boxWPx = with(density) { boxW.toPx() }
                    val boxHPx = with(density) { boxH.toPx() }
                    val boxAspect = if (boxHPx > 0f) boxWPx / boxHPx else previewAspect

                    // Size TextureView to exact previewAspect covering the viewport (zero warping!)
                    val tvW = if (previewAspect > boxAspect) boxH * previewAspect else boxW
                    val tvH = if (previewAspect > boxAspect) boxH else boxW / previewAspect
                    val tvWPx = with(density) { tvW.toPx() }
                    val tvHPx = with(density) { tvH.toPx() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .pointerInput(minZoom, maxZoom, boxWPx, boxHPx, tvWPx, tvHPx) {
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

                                    // Clean single tap -> Tap-to-Focus at exact sensor point
                                    if (!multiTouch && !moved && tvWPx > 0f && tvHPx > 0f) {
                                        focusPoint = startPos
                                        focusState = FocusVisualState.FOCUSING
                                        focusSeq++
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        val offsetX = (tvWPx - boxWPx) / 2f
                                        val offsetY = (tvHPx - boxHPx) / 2f
                                        val normX = ((startPos.x + offsetX) / tvWPx).coerceIn(0f, 1f)
                                        val normY = ((startPos.y + offsetY) / tvHPx).coerceIn(0f, 1f)
                                        controller.tapToFocus(normX, normY)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                TextureView(ctx).also { tv -> textureViewRef = tv }
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .requiredSize(tvW, tvH)
                        )

                        // Corner-bracket QR Target Reticle + animated laser scan line + tap-to-focus box
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val side = minOf(size.width, size.height) * 0.62f
                            val left = (size.width - side) / 2f
                            val top = (size.height - side) / 2f
                            val right = left + side
                            val bottom = top + side
                            val corner = side * 0.16f
                            val reticleColor = if (scannedResult != null) Color(0xFF4AE87A) else palette.accentPink
                            val strokeW = 3.dp.toPx()

                            // Top-left bracket
                            drawLine(reticleColor, Offset(left, top), Offset(left + corner, top), strokeW, StrokeCap.Square)
                            drawLine(reticleColor, Offset(left, top), Offset(left, top + corner), strokeW, StrokeCap.Square)
                            // Top-right bracket
                            drawLine(reticleColor, Offset(right - corner, top), Offset(right, top), strokeW, StrokeCap.Square)
                            drawLine(reticleColor, Offset(right, top), Offset(right, top + corner), strokeW, StrokeCap.Square)
                            // Bottom-left bracket
                            drawLine(reticleColor, Offset(left, bottom - corner), Offset(left, bottom), strokeW, StrokeCap.Square)
                            drawLine(reticleColor, Offset(left, bottom), Offset(left + corner, bottom), strokeW, StrokeCap.Square)
                            // Bottom-right bracket
                            drawLine(reticleColor, Offset(right - corner, bottom), Offset(right, bottom), strokeW, StrokeCap.Square)
                            drawLine(reticleColor, Offset(right, bottom - corner), Offset(right, bottom), strokeW, StrokeCap.Square)

                            // Animated laser line while scanning
                            if (scannedResult == null) {
                                val ly = top + side * laserProgress
                                drawLine(
                                    color = palette.accentPink.copy(alpha = 0.85f),
                                    start = Offset(left + 10.dp.toPx(), ly),
                                    end = Offset(right - 10.dp.toPx(), ly),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }

                            // Tap-to-focus visual square with crosshairs
                            val fp = focusPoint
                            if (fp != null) {
                                val half = 26.dp.toPx()
                                val tick = 6.dp.toPx()
                                val fColor = when (focusState) {
                                    FocusVisualState.LOCKED -> Color(0xFF4AE87A)
                                    FocusVisualState.FOCUSING -> Color(0xFFFFE57F)
                                    FocusVisualState.IDLE -> Color.White
                                }
                                val fw = 2.dp.toPx()
                                val fl = fp.x - half
                                val ft = fp.y - half
                                val fr = fp.x + half
                                val fb = fp.y + half
                                drawLine(fColor, Offset(fl, ft), Offset(fr, ft), fw)
                                drawLine(fColor, Offset(fr, ft), Offset(fr, fb), fw)
                                drawLine(fColor, Offset(fr, fb), Offset(fl, fb), fw)
                                drawLine(fColor, Offset(fl, fb), Offset(fl, ft), fw)
                                // Inner crosshair ticks
                                drawLine(fColor, Offset(fp.x, ft), Offset(fp.x, ft + tick), fw)
                                drawLine(fColor, Offset(fp.x, fb - tick), Offset(fp.x, fb), fw)
                                drawLine(fColor, Offset(fl, fp.y), Offset(fl + tick, fp.y), fw)
                                drawLine(fColor, Offset(fr - tick, fp.y), Offset(fr, fp.y), fw)
                            }
                        }
                    }
                }
            }
        }

        // Scanned result panel
        val result = scannedResult
        if (result != null) {
            BevelBox(theme = palette, raised = false, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        result,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RetroButton(
                            label = "Open in Voyager",
                            theme = palette,
                            soundEnabled = vm.soundEnabled,
                            active = true,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.openBrowserUrl(result) }
                        )
                        RetroButton(
                            label = "Copy",
                            theme = palette,
                            soundEnabled = vm.soundEnabled,
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("QR Code", result))
                                statusText = "Copied to clipboard!"
                            }
                        )
                    }
                }
            }
        }

        // Classic Win98 Status Bar
        BevelBox(theme = palette, raised = false, modifier = Modifier.fillMaxWidth()) {
            Text(
                statusText,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = palette.textDark,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
