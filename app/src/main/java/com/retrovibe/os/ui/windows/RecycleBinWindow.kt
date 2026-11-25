package com.retrovibe.os.ui.windows

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroThemes
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton

@Composable
fun RecycleBinContent(vm: OSViewModel) {
    val theme = RetroThemes.of(vm.themeId)

    Column(modifier = Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("${vm.trash.size} deleted item(s)", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
            RetroButton("Empty Recycle Bin", theme, vm.soundEnabled, enabled = vm.trash.isNotEmpty(), onClick = { vm.emptyRecycleBin() })
        }

        BevelBox(theme = theme, raised = false, modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (vm.trash.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    PixelIcon("recycle", 42.dp)
                    Spacer(Modifier.height(6.dp))
                    Text("Recycle Bin is empty.", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textSecondary)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(vm.trash, key = { it.id }) { item ->
                        val icon = when (item.type) {
                            "note" -> "note"
                            "artwork" -> "paint"
                            "memo" -> "mic"
                            "photo" -> "gallery"
                            "music" -> "music"
                            "todo" -> "habit"
                            "reminder" -> "reminder"
                            "favorite" -> "ie"
                            "contact" -> "phone"
                            "alarm" -> "clock"
                            "book" -> "book"
                            else -> "folder"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            PixelIcon(icon, 22.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                                Text(item.type.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = theme.textSecondary)
                            }
                            RetroButton("Restore", theme, vm.soundEnabled, onClick = { vm.restoreTrashItem(item.id) })
                            RetroButton("×", theme, vm.soundEnabled, onClick = { vm.deleteTrashItem(item.id) })
                        }
                        HorizontalDivider(color = theme.borderDark, thickness = 1.dp)
                    }
                }
            }
        }
    }
}
