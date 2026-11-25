package com.retrovibe.os.ui.windows

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Size as AndroidSize
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.retrovibe.os.ui.win98Bevel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private fun applyPureCameraSettings(
    builder: CaptureRequest.Builder,
    characteristics: CameraCharacteristics?
) {
    // Natural auto-exposure & white balance with zero AI scene / filter / sharpening / smoothing
    builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
    builder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_OFF)
    builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
    builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

    // Continuous autofocus if supported
    val afModes = characteristics?.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
    if (afModes != null && afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
    }

    // Disable artificial edge sharpening
    val edgeModes = characteristics?.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
    if (edgeModes == null || edgeModes.contains(CaptureRequest.EDGE_MODE_OFF)) {
        builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
    }

    // Disable heavy AI / plastic noise reduction (prefer OFF, fallback to MINIMAL)
    val nrModes = characteristics?.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
    when {
        nrModes == null || nrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_OFF) ->
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
        nrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL) ->
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL)
    }

    // Keep natural tonemap without HDR / AI local contrast boosting
    val tonemapModes = characteristics?.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)
    if (tonemapModes != null && tonemapModes.contains(CaptureRequest.TONEMAP_MODE_FAST)) {
        builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_FAST)
    }
}

private fun chooseCaptureSize(sizes: Array<AndroidSize>?): AndroidSize {
    if (sizes.isNullOrEmpty()) return AndroidSize(1600, 1200)
    // Prefer full uncropped 4:3 sensor resolution up to 4096px wide
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

private fun applyCenterCropTransform(
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    previewSize: AndroidSize,
    sensorOrientation: Int
) {
    if (viewWidth <= 0 || viewHeight <= 0) return
    val swapped = sensorOrientation == 90 || sensorOrientation == 270
    val bufW = if (swapped) previewSize.height.toFloat() else previewSize.width.toFloat()
    val bufH = if (swapped) previewSize.width.toFloat() else previewSize.height.toFloat()
    val bufAspect = bufW / bufH
    val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()

    val scaleX: Float
    val scaleY: Float
    if (bufAspect > viewAspect) {
        scaleX = bufAspect / viewAspect
        scaleY = 1f
    } else {
        scaleX = 1f
        scaleY = viewAspect / bufAspect
    }
    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    textureView.setTransform(matrix)
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
    private val onFlashAvailable: (Boolean) -> Unit
) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var characteristics: CameraCharacteristics? = null
    private var previewSize: AndroidSize = AndroidSize(1280, 960)
    private var sensorOrientation: Int = 90
    private var isFront: Boolean = false
    private var textureView: TextureView? = null
    private var flashEnabled: Boolean = false
    private var isCapturing: Boolean = false

    fun setFlash(enabled: Boolean) {
        flashEnabled = enabled
    }

    fun start(view: TextureView, useFront: Boolean) {
        stop()
        textureView = view
        isFront = useFront
        val thread = HandlerThread("RetroPureCamera").apply { start() }
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
                    applyCenterCropTransform(view, w, h, previewSize, sensorOrientation)
                }
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(viewW: Int, viewH: Int) {
        try {
            val desiredFacing = if (isFront) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val camId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == desiredFacing
            } ?: manager.cameraIdList.firstOrNull()

            if (camId == null) {
                onStatus("No camera found")
                return
            }

            val chars = manager.getCameraCharacteristics(camId)
            characteristics = chars
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val hasFlash = !isFront && (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true)
            onFlashAvailable(hasFlash)

            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val captureSize = chooseCaptureSize(map?.getOutputSizes(ImageFormat.JPEG))
            val ratio = captureSize.width.toFloat() / captureSize.height.toFloat()
            previewSize = choosePreviewSize(map?.getOutputSizes(SurfaceTexture::class.java), ratio)

            textureView?.let { tv ->
                tv.post { applyCenterCropTransform(tv, viewW, viewH, previewSize, sensorOrientation) }
            }

            imageReader?.close()
            imageReader = ImageReader.newInstance(captureSize.width, captureSize.height, ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener({ reader ->
                    val img = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val buffer = img.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val filename = "RV_$stamp.jpg"
                        val savedUri = saveJpegToPhoneGallery(context, bytes, filename)
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
            val previewBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                applyPureCameraSettings(this, characteristics)
            }

            device.createCaptureSession(
                listOf(previewSurface, reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            session.setRepeatingRequest(previewBuilder.build(), null, bgHandler)
                            onStatus("Pure mode • No AI / No filters")
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
        onStatus("Capturing raw JPEG...")

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                applyPureCameraSettings(this, characteristics)
                set(CaptureRequest.JPEG_QUALITY, 95.toByte())
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
        try { captureSession?.close() } catch (_: Throwable) {}
        captureSession = null
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
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var useFront by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var flashAvailable by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Initializing camera...") }
    var lastPhotoUri by remember { mutableStateOf(vm.permittedPhotos.firstOrNull()?.uri) }
    var shutterFlash by remember { mutableStateOf(false) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) statusText = "Camera permission required"
    }

    val controller = remember {
        PureCameraController(
            context = context,
            onStatus = { msg -> statusText = msg },
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
            }
        )
    }

    LaunchedEffect(shutterFlash) {
        if (shutterFlash) {
            delay(110)
            shutterFlash = false
        }
    }

    LaunchedEffect(flashOn) {
        controller.setFlash(flashOn)
    }

    DisposableEffect(hasPermission, useFront, textureViewRef) {
        val tv = textureViewRef
        if (hasPermission && tv != null) {
            controller.start(tv, useFront)
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
        // Classic Win98 Camera Toolbar
        BevelBox(theme = palette, raised = true, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RetroButton(
                    label = if (useFront) "Back Cam" else "Front Cam",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    onClick = { useFront = !useFront }
                )
                if (flashAvailable && !useFront) {
                    RetroButton(
                        label = if (flashOn) "Flash: ON" else "Flash: OFF",
                        theme = palette,
                        soundEnabled = vm.soundEnabled,
                        active = flashOn,
                        onClick = { flashOn = !flashOn }
                    )
                }
                Spacer(Modifier.weight(1f))
                RetroButton(
                    label = "My Pictures",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    onClick = { vm.openWindow("gallery") }
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
                        PixelIcon("camera", 48.dp)
                        Text(
                            "RetroVibe Camera",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Text(
                            "Pure camera • No filters • No AI\nSaves to Phone Gallery & My Pictures",
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
                            TextureView(ctx).also { tv ->
                                textureViewRef = tv
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Subtle retro digicam viewfinder frame (HUD only — never saved onto photo)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.28f))
                    ) {
                        // Tiny top-left retro lens badge
                        Text(
                            text = if (useFront) "[FRONT]" else "[BACK]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                        )
                        Text(
                            text = "PURE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4AE87A).copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                        )
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

        // Bottom Controls: Last photo preview + Tactile Win98 Shutter Button
        BevelBox(theme = palette, raised = true, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Last photo preview box (tap to open My Pictures)
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

                // Responsive Win98 Shutter Button matching all app buttons
                RetroButton(
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    active = true,
                    enabled = hasPermission,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    onClick = {
                        shutterFlash = true
                        controller.takePhoto()
                    }
                ) {
                    PixelIcon("camera", 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "SNAP PHOTO",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textDark
                    )
                }
            }
        }

        // Classic Win98 Split Status Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BevelBox(theme = palette, raised = false, modifier = Modifier.weight(1f)) {
                Text(
                    statusText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = palette.textDark,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            BevelBox(theme = palette, raised = false) {
                Text(
                    "NO AI",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
