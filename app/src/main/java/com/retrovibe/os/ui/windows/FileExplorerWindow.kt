package com.retrovibe.os.ui.windows

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroThemes
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExplorerContent(vm: OSViewModel) {
    val theme = RetroThemes.of(vm.themeId)
    var currentFolderId by remember { mutableStateOf<String?>(null) }
    var newFolderMode by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var ctxTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) } // (type, id, title)
    var tip by remember { mutableStateOf("") }

    val curFolder = vm.folders.find { it.id == currentFolderId }
    val visibleFolders = vm.folders.filter { it.parentId == currentFolderId }
    val visibleNotes = if (currentFolderId != null) vm.notes.filter { it.folderId == currentFolderId } else emptyList()
    val visibleArt = if (currentFolderId != null) vm.artworks.filter { it.folderId == currentFolderId } else emptyList()
    val visibleMemos = if (currentFolderId != null) vm.voiceMemos.filter { it.folderId == currentFolderId } else emptyList()

    val address = if (curFolder != null) "C:\\RETROVIBE\\${curFolder.name}\\" else "C:\\RETROVIBE\\"

    Column(modifier = Modifier.fillMaxSize().padding(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RetroButton("← Back", theme, vm.soundEnabled, enabled = currentFolderId != null, onClick = {
                ctxTarget = null
                currentFolderId = curFolder?.parentId
            })
            RetroButton("+ Folder", theme, vm.soundEnabled, onClick = { newFolderMode = !newFolderMode })
            RetroButton("+ Note", theme, vm.soundEnabled, onClick = { vm.createNote(currentFolderId ?: "root-notes", true) })
            RetroButton("Paint", theme, vm.soundEnabled, onClick = { vm.editingArtworkId = null; vm.openWindow("paint") })
            if (vm.clipboard != null && currentFolderId != null) {
                RetroButton("Paste", theme, vm.soundEnabled, onClick = {
                    vm.pasteFromClipboard(currentFolderId!!, false)
                    tip = "Pasted into folder!"
                })
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Address", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = theme.textPrimary)
            BevelBox(theme = theme, raised = false, modifier = Modifier.weight(1f)) {
                Text(address, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textPrimary, modifier = Modifier.padding(4.dp))
            }
        }

        if (newFolderMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textPrimary),
                    modifier = Modifier.weight(1f).background(theme.windowSurface).border(1.dp, theme.borderDark).padding(5.dp)
                )
                RetroButton("Create", theme, vm.soundEnabled, onClick = {
                    vm.createFolder(folderName, currentFolderId)
                    folderName = ""
                    newFolderMode = false
                })
            }
        }

        BevelBox(theme = theme, raised = false, modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(86.dp),
                contentPadding = PaddingValues(6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visibleFolders.size) { i ->
                    val f = visibleFolders[i]
                    ExplorerCell("folder", f.name, "File Folder", theme.textPrimary, theme.textSecondary,
                        onClick = { currentFolderId = f.id },
                        onLongClick = { ctxTarget = Triple("folder", f.id, f.name) }
                    )
                }
                if (currentFolderId == null) {
                    item {
                        ExplorerCell("gallery", "03. MY PICTURES", ".JPG Gallery", theme.textPrimary, theme.textSecondary,
                            onClick = { vm.openWindow("gallery") }, onLongClick = {})
                    }
                    item {
                        ExplorerCell("music", "04. PLAYLIST", ".MP3 Player", theme.textPrimary, theme.textSecondary,
                            onClick = { vm.openWindow("music") }, onLongClick = {})
                    }
                }
                items(visibleNotes.size) { i ->
                    val n = visibleNotes[i]
                    ExplorerCell("note", n.title, ".TXT", theme.textPrimary, theme.textSecondary,
                        onClick = { vm.openWindow("note:${n.id}", n.title) },
                        onLongClick = { ctxTarget = Triple("note", n.id, n.title) }
                    )
                }
                items(visibleArt.size) { i ->
                    val a = visibleArt[i]
                    ExplorerCell("paint", a.title, ".BMP", theme.textPrimary, theme.textSecondary,
                        onClick = { vm.editingArtworkId = a.id; vm.openWindow("paint", "${a.title} - Paint") },
                        onLongClick = { ctxTarget = Triple("artwork", a.id, a.title) }
                    )
                }
                items(visibleMemos.size) { i ->
                    val m = visibleMemos[i]
                    ExplorerCell("mic", m.title, "${m.durationSec}s .WAV", theme.textPrimary, theme.textSecondary,
                        onClick = { vm.openWindow("recorder") },
                        onLongClick = { ctxTarget = Triple("memo", m.id, m.title) }
                    )
                }
            }

            ctxTarget?.let { (type, id, title) ->
                BevelBox(theme = theme, raised = true, modifier = Modifier.align(Alignment.Center).width(160.dp)) {
                    Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.titleBarActive, modifier = Modifier.padding(4.dp))
                        if (type != "folder") {
                            CtxBtn("Copy", theme.textPrimary) { vm.copyToClipboard(type, id, title); tip = "Copied $title"; ctxTarget = null }
                            CtxBtn("Cut (Move)", theme.textPrimary) { vm.cutToClipboard(type, id, title); tip = "Cut $title"; ctxTarget = null }
                        }
                        if (vm.clipboard != null && type == "folder") {
                            CtxBtn("Paste into Folder", theme.textPrimary) { vm.pasteFromClipboard(id, false); tip = "Pasted into $title"; ctxTarget = null }
                        }
                        if (type != "folder" || !id.startsWith("root-")) {
                            CtxBtn("Delete", Color(0xFFB01237)) {
                                when (type) {
                                    "note" -> vm.deleteNote(id)
                                    "artwork" -> vm.deleteArtwork(id)
                                    "memo" -> vm.deleteVoiceMemo(id)
                                    "folder" -> vm.deleteFolder(id)
                                }
                                ctxTarget = null
                            }
                        }
                        CtxBtn("Cancel", theme.textSecondary) { ctxTarget = null }
                    }
                }
            }
        }

        val count = visibleFolders.size + visibleNotes.size + visibleArt.size + visibleMemos.size + if (currentFolderId == null) 2 else 0
        Text(
            tip.ifBlank { "$count object(s) · Long-press item for Copy/Cut/Paste · RetroVibe OS" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.5.sp,
            color = theme.textSecondary
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExplorerCell(icon: String, name: String, sub: String, primary: Color, secondary: Color, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(4.dp)
    ) {
        PixelIcon(icon, 36.dp)
        Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = primary)
        Text(sub, maxLines = 1, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = secondary)
    }
}

@Composable
private fun CtxBtn(label: String, color: Color, onClick: () -> Unit) {
    Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(6.dp))
}
