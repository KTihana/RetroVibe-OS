package com.retrovibe.os.ui.windows

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.RetroButton
import com.retrovibe.os.ui.win98Bevel
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.sin
import kotlin.math.sqrt
import com.retrovibe.os.theme.textDark
import com.retrovibe.os.theme.accent

@Composable
fun SoundRecorderWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableStateOf(0) }
    var playingId by remember { mutableStateOf<String?>(null) }
    val ampHistory = remember { mutableStateListOf<Float>().apply { repeat(48) { add(0f) } } }
    var waveTick by remember { mutableIntStateOf(0) }
    var statusMsg by remember { mutableStateOf("Ready") }
    var currentFile by remember { mutableStateOf<File?>(null) }
    val recorderRef = remember { mutableStateOf<MediaRecorder?>(null) }
    val playerRef = remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPlayback() {
        try {
            playerRef.value?.stop()
            playerRef.value?.release()
        } catch (_: Throwable) {
        }
        playerRef.value = null
        playingId = null
    }

    fun startRecordingInternal() {
        stopPlayback()
        try {
            val dir = File(context.filesDir, "voice_memos").apply { mkdirs() }
            val outFile = File(dir, "memo_${System.currentTimeMillis()}.m4a")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setOutputFile(outFile.absolutePath)
            rec.prepare()
            rec.start()
            recorderRef.value = rec
            currentFile = outFile
            elapsedSec = 0
            isRecording = true
            statusMsg = "RECORDING..."
        } catch (e: Throwable) {
            statusMsg = "Mic error: ${e.message}"
            RetroSoundEngine.play(RetroSound.ERROR, vm.soundEnabled)
        }
    }

    fun stopRecordingInternal() {
        try {
            recorderRef.value?.stop()
            recorderRef.value?.release()
        } catch (_: Throwable) {
        }
        recorderRef.value = null
        isRecording = false
        val f = currentFile
        if (f != null && f.exists()) {
            vm.addVoiceMemo("Voice_${vm.voiceMemos.size + 1}.WAV", f.absolutePath, elapsedSec.coerceAtLeast(1))
            statusMsg = "Saved ${f.name}"
        } else {
            statusMsg = "Stopped"
        }
        currentFile = null
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecordingInternal() else statusMsg = "Microphone permission denied"
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) stopRecordingInternal()
            stopPlayback()
        }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            for (i in ampHistory.indices) ampHistory[i] = 0f
            waveTick++
            return@LaunchedEffect
        }
        while (isRecording) {
            delay(50)
            val raw = try { recorderRef.value?.maxAmplitude ?: 0 } catch (_: Throwable) { 0 }
            // Responsive sqrt curve so normal speaking voice clearly animates the oscilloscope
            val norm = sqrt((raw.coerceIn(0, 26000) / 26000f).toDouble()).toFloat().coerceIn(0.02f, 1f)
            if (ampHistory.size >= 48) ampHistory.removeAt(0)
            ampHistory.add(norm)
            waveTick++
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000)
            elapsedSec += 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(8.dp)
    ) {
        // Voice-Reactive Win98 Phosphor Oscilloscope
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .win98Bevel(sunken = true, bg = Color(0xFF061208))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val _t = waveTick
                val midY = size.height / 2f
                // Subtle phosphor grid lines
                drawLine(Color(0xFF103018), Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1f)
                drawLine(Color(0xFF0C2412), Offset(0f, midY * 0.5f), Offset(size.width, midY * 0.5f), strokeWidth = 1f)
                drawLine(Color(0xFF0C2412), Offset(0f, midY * 1.5f), Offset(size.width, midY * 1.5f), strokeWidth = 1f)

                if (isRecording && ampHistory.isNotEmpty()) {
                    val count = ampHistory.size
                    val dx = size.width / (count - 1).coerceAtLeast(1)
                    var prev = Offset(0f, midY)
                    for (i in 0 until count) {
                        val level = ampHistory[i]
                        // Alternate polarity + slight harmonic ripple for authentic audio waveform look
                        val sign = if (i % 2 == 0) 1f else -1f
                        val h = level * (size.height * 0.44f)
                        val x = i * dx
                        val next = Offset(x, midY + sign * h)
                        // Vertical bar + connected waveform trace
                        drawLine(Color(0xFF1E8A38), Offset(x, midY - h), Offset(x, midY + h), strokeWidth = (dx * 0.45f).coerceAtLeast(1.5f))
                        drawLine(Color(0xFF4CFF50), prev, next, strokeWidth = 2f)
                        prev = next
                    }
                } else {
                    drawLine(Color(0xFF39FF14), Offset(0f, midY), Offset(size.width, midY), strokeWidth = 2f)
                }
            }
            Text(
                text = if (isRecording) "REC 00:${elapsedSec.toString().padStart(2, '0')}" else statusMsg,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = if (isRecording) Color(0xFFFF4D6D) else Color(0xFF39FF14),
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RetroButton(
                label = if (isRecording) "[] Stop & Save" else "(*) Record",
                palette = palette,
                active = isRecording,
                onClick = {
                    if (isRecording) {
                        stopRecordingInternal()
                    } else {
                        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (ok) startRecordingInternal() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Saved Voice Memos (${vm.voiceMemos.size})",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = palette.textDark
        )
        Spacer(Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .win98Bevel(sunken = true, bg = Color.White)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(vm.voiceMemos, key = { it.id }) { memo ->
                val isThisPlaying = playingId == memo.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isThisPlaying) palette.windowBg else Color.Transparent)
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isThisPlaying) "[]" else ">",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = palette.accent,
                        modifier = Modifier
                            .clickable {
                                if (isThisPlaying) {
                                    stopPlayback()
                                } else {
                                    stopPlayback()
                                    try {
                                        val mp = MediaPlayer().apply {
                                            setDataSource(memo.path)
                                            prepare()
                                            setOnCompletionListener { stopPlayback() }
                                            start()
                                        }
                                        playerRef.value = mp
                                        playingId = memo.id
                                    } catch (_: Throwable) {
                                        statusMsg = "Could not play memo"
                                    }
                                }
                            }
                            .padding(end = 6.dp)
                    )
                    Text(
                        text = "${memo.title} (${memo.durationSec}s)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "[Del]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFFAA0000),
                        modifier = Modifier.clickable {
                            if (isThisPlaying) stopPlayback()
                            vm.deleteVoiceMemo(memo.id)
                        }
                    )
                }
            }
        }
    }
}
