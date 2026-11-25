package com.retrovibe.os.ui.windows

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size as AndroidSize
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.retrovibe.os.ui.win98Bevel
import kotlin.math.abs

private fun isLikelyUrl(text: String): Boolean {
    val t = text.trim()
    return t.startsWith("http://", ignoreCase = true) ||
        t.startsWith("https://", ignoreCase = true) ||
        t.startsWith("www.", ignoreCase = true)
}

private class QrCameraController(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onQrDetected: (String) -> Unit,
    private val onFlashAvailable: (Boolean) -> Unit
) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private var previewSize: AndroidSize = AndroidSize(1280, 960)
    private var sensorOrientation: Int = 90
    private var textureView: TextureView? = null
    private var torchEnabled: Boolean = false
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

    fun setPaused(value: Boolean) {
        paused = value
    }

    fun setTorch(enabled: Boolean) {
        torchEnabled = enabled
        val session = captureSession ?: return
        val builder = previewBuilder ?: return
        try {
            builder.set(
                CaptureRequest.FLASH_MODE,
                if (enabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
            )
            session.setRepeatingRequest(builder.build(), null, bgHandler)
        } catch (_: Throwable) {}
    }

    fun start(view: TextureView) {
        stop()
        textureView = view
        paused = false
        val thread = HandlerThread("RetroQrScanner").apply { start() }
        bgThread = thread
        bgHandler = Handler(thread.looper)

        if (view.isAvailable) {
            openCamera(view.width, view.height)
        } else {
            view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    openCamera(w, h)
                }
                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                    applyTransform(view, w, h)
                }
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
            }
        }
    }

    private fun applyTransform(view: TextureView, viewW: Int, viewH: Int) {
        if (viewW <= 0 || viewH <= 0) return
        val swapped = sensorOrientation == 90 || sensorOrientation == 270
        val bufW = if (swapped) previewSize.height.toFloat() else previewSize.width.toFloat()
        val bufH = if (swapped) previewSize.width.toFloat() else previewSize.height.toFloat()
        val bufAspect = bufW / bufH
        val viewAspect = viewW.toFloat() / viewH.toFloat()
        val scaleX = if (bufAspect > viewAspect) bufAspect / viewAspect else 1f
        val scaleY = if (bufAspect > viewAspect) 1f else viewAspect / bufAspect
        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
        view.setTransform(matrix)
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(viewW: Int, viewH: Int) {
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
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            onFlashAvailable(chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true)

            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val prevSizes = map?.getOutputSizes(SurfaceTexture::class.java)
            previewSize = prevSizes
                ?.filter { maxOf(it.width, it.height) in 640..1920 }
                ?.minByOrNull { abs(maxOf(it.width, it.height) - 1280) }
                ?: prevSizes?.firstOrNull()
                ?: AndroidSize(1280, 960)

            val yuvSizes = map?.getOutputSizes(ImageFormat.YUV_420_888)
            val scanSize = yuvSizes
                ?.filter { maxOf(it.width, it.height) in 480..960 }
                ?.minByOrNull { abs(maxOf(it.width, it.height) - 640) }
                ?: AndroidSize(640, 480)

            textureView?.let { tv -> tv.post { applyTransform(tv, viewW, viewH) } }

            imageReader?.close()
            imageReader = ImageReader.newInstance(scanSize.width, scanSize.height, ImageFormat.YUV_420_888, 2).apply {
                setOnImageAvailableListener({ r ->
                    val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val now = System.currentTimeMillis()
                        if (paused || now - lastDecodeMs < 140L) return@setOnImageAvailableListener
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
                        val bitmap = BinaryBitmap(HybridBinarizer(source))
                        val result = try {
                            reader.decodeWithState(bitmap)
                        } catch (_: Throwable) {
                            null
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
                val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                if (afModes != null && afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }
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
                            session.setRepeatingRequest(builder.build(), null, bgHandler)
                            onStatus("Point camera at any QR code...")
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
            }
        )
    }

    LaunchedEffect(torchOn) {
        controller.setTorch(torchOn)
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
                if (scannedResult != null) {
                    RetroButton(
                        label = "Scan Again",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        active = true,
                        onClick = {
                            scannedResult = null
                            statusText = "Point camera at any QR code..."
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
            Box(
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
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).also { tv -> textureViewRef = tv }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Classic QR Target Box + Pink Laser Line
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .border(2.dp, if (scannedResult != null) Color(0xFF4AE87A) else palette.accentPink),
                        contentAlignment = Alignment.Center
                    ) {
                        if (scannedResult == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .height(2.dp)
                                    .background(palette.accentPink.copy(alpha = 0.85f))
                            )
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
