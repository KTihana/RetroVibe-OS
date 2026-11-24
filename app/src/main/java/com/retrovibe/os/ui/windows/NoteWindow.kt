package com.retrovibe.os.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.model.NoteItem
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroThemes
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton

@Composable
fun NoteWindowContent(vm: OSViewModel, note: NoteItem) {
    val theme = RetroThemes.of(vm.themeId)
    var editingTitle by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (editingTitle) {
                BasicTextField(
                    value = note.title,
                    onValueChange = { t -> vm.updateNote(note.id) { it.copy(title = t) } },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .background(theme.windowSurface)
                        .border(1.dp, theme.borderDark)
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                )
                RetroButton("OK", theme, vm.soundEnabled, onClick = { editingTitle = false })
            } else {
                RetroButton(
                    theme = theme,
                    soundEnabled = vm.soundEnabled,
                    modifier = Modifier.weight(1f),
                    onClick = { editingTitle = true }
                ) {
                    PixelIcon("edit", 11.dp)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        note.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )
                }
            }

            RetroButton(
                theme = theme,
                soundEnabled = vm.soundEnabled,
                active = note.pinnedToDesktop,
                onClick = { vm.updateNote(note.id) { it.copy(pinnedToDesktop = !it.pinnedToDesktop) } }
            ) {
                PixelIcon("pin", 11.dp)
                Spacer(Modifier.width(3.dp))
                Text(
                    if (note.pinnedToDesktop) "Pinned" else "Pin",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
            }

            RetroButton(theme = theme, soundEnabled = vm.soundEnabled, onClick = { vm.createNote(note.folderId, true) }) {
                PixelIcon("plus", 11.dp)
            }
            RetroButton(theme = theme, soundEnabled = vm.soundEnabled, onClick = { vm.deleteNote(note.id) }) {
                PixelIcon("trash", 12.dp)
            }
        }

        BevelBox(theme = theme, raised = false, modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                BasicTextField(
                    value = note.content,
                    onValueChange = { c -> vm.updateNote(note.id) { it.copy(content = c) } },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, color = theme.textPrimary),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PixelIcon(if (note.pinnedToDesktop) "pin" else "heart", 13.dp)
                        Text(
                            if (note.pinnedToDesktop) "Pinned to Desktop" else "In 00. MY NOTES",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            color = theme.textSecondary
                        )
                    }
                    Text("${note.content.length}c", fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, color = theme.textSecondary)
                }
            }
        }
    }
}
