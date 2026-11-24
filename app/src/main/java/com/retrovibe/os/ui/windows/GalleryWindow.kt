package com.retrovibe.os.ui.windows

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.model.PermittedPhoto
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton
import com.retrovibe.os.ui.win98Bevel
import com.retrovibe.os.theme.textDark

@Composable
fun GalleryWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    var selectedPhoto by remember { mutableStateOf<PermittedPhoto?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val picked = uris.mapIndexed { idx, uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) {
                }
                PermittedPhoto(
                    id = "photo-${System.currentTimeMillis()}-$idx",
                    uri = uri.toString(),
                    filename = uri.lastPathSegment?.substringAfterLast('/') ?: "IMG_${idx + 1}.JPG"
                )
            }
            vm.addPermittedPhotos(picked)
            RetroSoundEngine.play(RetroSound.SUCCESS, vm.soundEnabled)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RetroButton(
                label = "+ Grant Photos",
                palette = palette,
                onClick = {
                    RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                active = true
            )
            if (vm.permittedPhotos.isNotEmpty()) {
                RetroButton(
                    label = "Revoke All",
                    palette = palette,
                    onClick = {
                        RetroSoundEngine.play(RetroSound.TRASH, vm.soundEnabled)
                        vm.clearPermittedPhotos()
                        selectedPhoto = null
                    }
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${vm.permittedPhotos.size} file(s)",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = palette.textDark
            )
        }

        if (selectedPhoto != null) {
            val photo = selectedPhoto!!
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .win98Bevel(sunken = true, bg = Color(0xFF141414))
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RetroButton(label = "<- Back", palette = palette, onClick = { selectedPhoto = null })
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = photo.filename,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    RetroButton(label = "Remove", palette = palette, onClick = {
                        vm.removePermittedPhoto(photo.id)
                        selectedPhoto = null
                    })
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.filename,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .win98Bevel(sunken = true, bg = Color.White)
                    .padding(6.dp)
            ) {
                if (vm.permittedPhotos.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        PixelIcon("gallery", 36.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No permitted photos yet.",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.Black
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "RetroVibe OS only shows photos you\nexplicitly grant access to via Android Picker.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF666666)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(vm.permittedPhotos, key = { it.id }) { photo ->
                            Column(
                                modifier = Modifier
                                    .border(1.dp, Color(0xFF999999))
                                    .background(palette.windowBg)
                                    .clickable {
                                        RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                                        selectedPhoto = photo
                                    }
                                    .padding(3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                    AsyncImage(
                                        model = photo.uri,
                                        contentDescription = photo.filename,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF222222))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(18.dp)
                                            .win98Bevel(sunken = false, bg = palette.windowBg, strokeDp = 1.dp)
                                            .clickable { vm.removePermittedPhoto(photo.id) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("×", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFB01237))
                                    }
                                }
                                Text(
                                    text = photo.filename,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = palette.textDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
