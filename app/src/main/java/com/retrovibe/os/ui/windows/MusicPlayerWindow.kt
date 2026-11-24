package com.retrovibe.os.ui.windows

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.model.MusicTrack
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.RetroButton
import com.retrovibe.os.ui.win98Bevel
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun MusicPlayerWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    var currentIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("STOPPED") }
    var eqBars by remember { mutableStateOf(List(12) { 2 }) }
    val mediaPlayer = remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopAll() {
        RetroSoundEngine.stopDemoMelody()
        try {
            mediaPlayer.value?.stop()
            mediaPlayer.value?.release()
        } catch (_: Throwable) {
        }
        mediaPlayer.value = null
        isPlaying = false
        statusText = "STOPPED"
    }

    fun playTrackAt(idx: Int) {
        if (vm.tracks.isEmpty()) return
        val safeIdx = ((idx % vm.tracks.size) + vm.tracks.size) % vm.tracks.size
        stopAll()
        currentIndex = safeIdx
        val track = vm.tracks[safeIdx]
        if (track.isDemo || track.uri == null) {
            isPlaying = true
            statusText = "PLAYING SYNTH"
            RetroSoundEngine.startDemoMelody(track.id, vm.soundEnabled) { }
        } else {
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(context, Uri.parse(track.uri))
                    prepare()
                    setOnCompletionListener {
                        playTrackAt(safeIdx + 1)
                    }
                    start()
                }
                mediaPlayer.value = mp
                isPlaying = true
                statusText = "PLAYING AUDIO"
            } catch (_: Throwable) {
                statusText = "CANNOT OPEN FILE"
                RetroSoundEngine.play(RetroSound.ERROR, vm.soundEnabled)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopAll() }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            eqBars = List(12) { Random.nextInt(3, 18) }
            delay(130)
        }
        eqBars = List(12) { 2 }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val added = uris.mapIndexed { idx, uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) {
                }
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Track_${idx + 1}.mp3"
                MusicTrack(
                    id = "trk-${System.currentTimeMillis()}-$idx",
                    title = name,
                    artist = "Phone Audio",
                    uri = uri.toString(),
                    isDemo = false
                )
            }
            vm.addTracks(added)
        }
    }

    val activeTrack = vm.tracks.getOrNull(currentIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(6.dp)
    ) {
        // Winamp / WMP LCD display
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .win98Bevel(sunken = true, bg = Color(0xFF0C120C))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Color(0xFF39FF14)
                )
                Text(
                    text = "44kHz STEREO",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = Color(0xFF22AA22)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = activeTrack?.title ?: "No tracks loaded",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF88FF88),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(20.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                eqBars.forEach { h ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(h.dp)
                            .background(if (h > 13) Color(0xFFFF4D8D) else Color(0xFF39FF14))
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Transport controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RetroButton(label = "|<<", palette = palette, onClick = { playTrackAt(currentIndex - 1) })
            RetroButton(
                label = if (isPlaying) "|| Pause" else "> Play",
                palette = palette,
                active = isPlaying,
                onClick = {
                    if (isPlaying) stopAll() else playTrackAt(currentIndex)
                },
                modifier = Modifier.weight(1f)
            )
            RetroButton(label = "[]", palette = palette, onClick = { stopAll() })
            RetroButton(label = ">>|", palette = palette, onClick = { playTrackAt(currentIndex + 1) })
            RetroButton(
                label = "+ Add",
                palette = palette,
                onClick = { audioPicker.launch(arrayOf("audio/*")) }
            )
        }

        Spacer(Modifier.height(6.dp))

        // Playlist
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .win98Bevel(sunken = true, bg = Color.White)
                .padding(3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(vm.tracks, key = { _, t -> t.id }) { idx, track ->
                val selected = idx == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) palette.accent else Color.Transparent)
                        .clickable { playTrackAt(idx) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${idx + 1}. ${track.title}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (selected) Color.White else Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!track.isDemo) {
                        Text(
                            text = "[x]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = if (selected) Color.White else Color(0xFFAA0000),
                            modifier = Modifier.clickable {
                                if (selected) stopAll()
                                vm.removeTrack(track.id)
                            }
                        )
                    }
                }
            }
        }
    }
}
