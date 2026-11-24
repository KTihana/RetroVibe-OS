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

@Composable
fun SoundRecorderWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableStateOf(0) }
    var playingId by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf(0f) }
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
        while (isRecording) {
            delay(100)
            phase += 0.55f
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
        // Oscilloscope
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .win98Bevel(sunken = true, bg = Color(0xFF061006))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val midY = size.height / 2f
                val step = 6f
                var x = 0f
                var prev = Offset(0f, midY)
                while (x < size.width) {
                    val amp = if (isRecording) (sin((x * 0.06f + phase).toDouble()) * (size.height * 0.34f)).toFloat() else 0f
                    val next = Offset(x, midY + amp)
                    drawLine(Color(0xFF39FF14), prev, next, strokeWidth = 2f)
                    prev = next
                    x += step
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
