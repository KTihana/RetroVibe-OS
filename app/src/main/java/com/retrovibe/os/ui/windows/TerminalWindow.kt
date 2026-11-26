package com.retrovibe.os.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.RetroButton
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private fun parseArgs(raw: String): List<String> {
    val result = mutableListOf<String>()
    val cur = StringBuilder()
    var inQuotes = false
    for (ch in raw.trim()) {
        when {
            ch == '"' -> inQuotes = !inQuotes
            ch.isWhitespace() && !inQuotes -> {
                if (cur.isNotEmpty()) {
                    result.add(cur.toString())
                    cur.clear()
                }
            }
            else -> cur.append(ch)
        }
    }
    if (cur.isNotEmpty()) result.add(cur.toString())
    return result
}

@Composable
fun TerminalWindow(vm: OSViewModel, palette: RetroPalette) {
    var input by remember { mutableStateOf("") }
    var cwdFolderId by remember { mutableStateOf<String?>(null) }
    var lines by remember {
        mutableStateOf(
            listOf(
                "RetroVibe OS [Version 98.4.2026]",
                "(C) Copyright RetroVibe Corp 1998-2026.",
                "Type 'help' for available commands.",
                ""
            )
        )
    }
    val listState = rememberLazyListState()

    fun currentPath(): String {
        val parts = mutableListOf<String>()
        var id = cwdFolderId
        var guard = 0
        while (id != null && guard < 12) {
            val f = vm.folders.find { it.id == id } ?: break
            parts.add(0, f.name)
            id = f.parentId
            guard++
        }
        return if (parts.isEmpty()) "C:\\RETROVIBE" else "C:\\RETROVIBE\\${parts.joinToString("\\")}"
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    fun execute(raw: String) {
        val cmdLine = raw.trim()
        input = ""
        if (cmdLine.isEmpty()) return
        RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)

        val promptBefore = "${currentPath()}>"
        val args = parseArgs(cmdLine)
        val op = args.firstOrNull()?.lowercase() ?: return
        val rest = cmdLine.substringAfter(' ', "").trim()

        val output: List<String> = when (op) {
            "help" -> listOf(
                "File & Folder Commands:",
                "  DIR / LS              - List folders & files",
                "  CD <folder> | CD ..   - Change folder",
                "  PWD                   - Show current path",
                "  MKDIR / MD <name>     - Create a folder",
                "  RMDIR / RD <name>     - Delete a folder (to Bin)",
                "  TOUCH / NEW <file>    - Create a text file",
                "  TYPE <file>           - Read a text file",
                "  EDIT / OPEN <file>    - Open file in Notepad",
                "  ECHO <txt> [> file]   - Print or write to file",
                "  REN / MV <old> <new>  - Rename folder or file",
                "  DEL / RM <file>       - Delete a file (to Bin)",
                "System & Fun Commands:",
                "  START <app>           - Launch any app",
                "  HEART                 - Print an ASCII heart",
                "  CAT                   - Toggle Desk Cat pet",
                "  WHOAMI / VER          - User & OS version",
                "  DATE / TIME           - Show system clock",
                "  CLS / CLEAR           - Clear screen",
                "  EXIT                  - Close prompt"
            )
            "ver" -> listOf("RetroVibe OS 98 Native Kotlin Edition [Build 2026]")
            "whoami" -> listOf(vm.username)
            "pwd" -> listOf(currentPath())
            "date", "time" -> listOf(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            )
            "heart" -> listOf(
                "   **   **",
                "  **** ****",
                "  *********",
                "   *******",
                "    *****",
                "     ***",
                "      *"
            )
            "cat" -> {
                if (args.size >= 2) {
                    val targetName = rest.trim('"')
                    val activeFolder = cwdFolderId ?: "root-notes"
                    val note = vm.notes.find {
                        (cwdFolderId == null || it.folderId == activeFolder) &&
                            it.title.equals(targetName, ignoreCase = true)
                    } ?: vm.notes.find { it.title.equals(targetName, ignoreCase = true) }
                    if (note != null) {
                        if (note.content.isBlank()) listOf("(empty file)") else note.content.lines()
                    } else {
                        listOf("File not found: $targetName")
                    }
                } else {
                    vm.deskCatEnabled = !vm.deskCatEnabled
                    vm.persist()
                    listOf("Desk Cat is now ${if (vm.deskCatEnabled) "ENABLED" else "DISABLED"}.")
                }
            }
            "dir", "ls" -> {
                val subFolders = vm.folders.filter { it.parentId == cwdFolderId }
                val dirNotes = if (cwdFolderId == null) {
                    emptyList()
                } else {
                    vm.notes.filter { it.folderId == cwdFolderId }
                }
                val out = mutableListOf(" Directory of ${currentPath()}", "")
                subFolders.forEach { f ->
                    out.add("  <DIR>   ${f.name}")
                }
                dirNotes.forEach { n ->
                    out.add("  ${n.content.length.toString().padStart(5)} B  ${n.title}")
                }
                if (cwdFolderId == "root-art") {
                    vm.artworks.forEach { a -> out.add("  <BMP>   ${a.title}") }
                }
                if (cwdFolderId == "root-voice") {
                    vm.voiceMemos.forEach { m -> out.add("  <WAV>   ${m.title}") }
                }
                if (subFolders.isEmpty() && dirNotes.isEmpty() && cwdFolderId != "root-art" && cwdFolderId != "root-voice") {
                    out.add("  (empty)")
                }
                out.add("")
                out.add("  ${subFolders.size} Dir(s)   ${dirNotes.size} File(s)   ${vm.trash.size} in Bin")
                out
            }
            "cd" -> {
                val target = rest.trim('"')
                when {
                    target.isEmpty() -> listOf(currentPath())
                    target == "\\" || target == "/" -> {
                        cwdFolderId = null
                        emptyList()
                    }
                    target == ".." -> {
                        val cur = vm.folders.find { it.id == cwdFolderId }
                        cwdFolderId = cur?.parentId
                        emptyList()
                    }
                    else -> {
                        val match = vm.folders.find {
                            it.parentId == cwdFolderId && it.name.equals(target, ignoreCase = true)
                        } ?: vm.folders.find {
                            it.parentId == cwdFolderId && it.name.contains(target, ignoreCase = true)
                        } ?: vm.folders.find {
                            it.name.equals(target, ignoreCase = true)
                        }
                        if (match != null) {
                            cwdFolderId = match.id
                            emptyList()
                        } else {
                            listOf("The system cannot find the folder: $target")
                        }
                    }
                }
            }
            "mkdir", "md" -> {
                val name = rest.trim('"')
                if (name.isEmpty()) {
                    listOf("Usage: mkdir <folder_name>")
                } else if (vm.folders.any { it.parentId == cwdFolderId && it.name.equals(name, ignoreCase = true) }) {
                    listOf("Folder already exists: $name")
                } else {
                    vm.createFolder(name, cwdFolderId)
                    listOf("Created folder '$name'.")
                }
            }
            "rmdir", "rd" -> {
                val name = rest.trim('"')
                if (name.isEmpty()) {
                    listOf("Usage: rmdir <folder_name>")
                } else {
                    val match = vm.folders.find {
                        it.parentId == cwdFolderId && it.name.equals(name, ignoreCase = true)
                    } ?: vm.folders.find { it.name.equals(name, ignoreCase = true) }
                    when {
                        match == null -> listOf("Folder not found: $name")
                        match.id.startsWith("root-") -> listOf("Access denied: system folder cannot be removed.")
                        else -> {
                            vm.deleteFolder(match.id)
                            if (cwdFolderId == match.id) cwdFolderId = match.parentId
                            listOf("Moved folder '${match.name}' to Recycle Bin.")
                        }
                    }
                }
            }
            "touch", "new" -> {
                val rawName = rest.trim('"')
                if (rawName.isEmpty()) {
                    listOf("Usage: touch <filename.txt>")
                } else {
                    val fileName = if (rawName.contains('.')) rawName else "$rawName.txt"
                    val targetFolder = cwdFolderId ?: "root-notes"
                    val existing = vm.notes.find {
                        it.folderId == targetFolder && it.title.equals(fileName, ignoreCase = true)
                    }
                    if (existing != null) {
                        listOf("File already exists: ${existing.title}")
                    } else {
                        val created = vm.createTextFile(fileName, "", targetFolder)
                        listOf("Created file '${created.title}'.")
                    }
                }
            }
            "echo" -> {
                if (rest.contains(">")) {
                    val textPart = rest.substringBeforeLast(">").trim().trim('"')
                    val filePart = rest.substringAfterLast(">").trim().trim('"')
                    if (filePart.isEmpty()) {
                        listOf("Usage: echo <text> > <filename.txt>")
                    } else {
                        val fileName = if (filePart.contains('.')) filePart else "$filePart.txt"
                        val targetFolder = cwdFolderId ?: "root-notes"
                        val existing = vm.notes.find {
                            it.folderId == targetFolder && it.title.equals(fileName, ignoreCase = true)
                        }
                        if (existing != null) {
                            vm.updateNote(existing.id) { it.copy(content = textPart) }
                            listOf("Wrote to '${existing.title}'.")
                        } else {
                            val created = vm.createTextFile(fileName, textPart, targetFolder)
                            listOf("Created '${created.title}' with text.")
                        }
                    }
                } else {
                    listOf(rest)
                }
            }
            "type" -> {
                val targetName = rest.trim('"')
                if (targetName.isEmpty()) {
                    listOf("Usage: type <filename>")
                } else {
                    val note = vm.notes.find {
                        (cwdFolderId == null || it.folderId == cwdFolderId) &&
                            it.title.equals(targetName, ignoreCase = true)
                    } ?: vm.notes.find { it.title.equals(targetName, ignoreCase = true) }
                    if (note != null) {
                        if (note.content.isBlank()) listOf("(empty file)") else note.content.lines()
                    } else {
                        listOf("File not found: $targetName")
                    }
                }
            }
            "edit", "open" -> {
                val targetName = rest.trim('"')
                if (targetName.isEmpty()) {
                    listOf("Usage: edit <filename>")
                } else {
                    val note = vm.notes.find {
                        (cwdFolderId == null || it.folderId == cwdFolderId) &&
                            it.title.equals(targetName, ignoreCase = true)
                    } ?: vm.notes.find { it.title.equals(targetName, ignoreCase = true) }
                    if (note != null) {
                        vm.openWindow("note:${note.id}", note.title)
                        listOf("Opening '${note.title}' in Notepad...")
                    } else {
                        listOf("File not found: $targetName")
                    }
                }
            }
            "ren", "rename", "mv" -> {
                if (args.size < 3) {
                    listOf("Usage: ren \"old name\" \"new name\"")
                } else {
                    val oldName = args[1]
                    val newName = args[2]
                    val folder = vm.folders.find {
                        it.parentId == cwdFolderId && it.name.equals(oldName, ignoreCase = true)
                    } ?: vm.folders.find { it.name.equals(oldName, ignoreCase = true) }
                    val note = vm.notes.find {
                        (cwdFolderId == null || it.folderId == cwdFolderId) &&
                            it.title.equals(oldName, ignoreCase = true)
                    } ?: vm.notes.find { it.title.equals(oldName, ignoreCase = true) }

                    when {
                        note != null -> {
                            vm.updateNote(note.id) { it.copy(title = newName) }
                            listOf("Renamed file '${note.title}' -> '$newName'.")
                        }
                        folder != null -> {
                            vm.renameFolder(folder.id, newName)
                            listOf("Renamed folder '${folder.name}' -> '$newName'.")
                        }
                        else -> listOf("File or folder not found: $oldName")
                    }
                }
            }
            "del", "rm" -> {
                val targetName = rest.trim('"')
                if (targetName.isEmpty()) {
                    listOf("Usage: del <filename>")
                } else {
                    val note = vm.notes.find {
                        (cwdFolderId == null || it.folderId == cwdFolderId) &&
                            it.title.equals(targetName, ignoreCase = true)
                    } ?: vm.notes.find { it.title.equals(targetName, ignoreCase = true) }
                    if (note != null) {
                        vm.deleteNote(note.id)
                        listOf("Moved '${note.title}' to Recycle Bin.")
                    } else {
                        listOf("File not found: $targetName")
                    }
                }
            }
            "cls", "clear" -> {
                lines = emptyList()
                return
            }
            "exit" -> {
                vm.closeWindow("terminal")
                return
            }
            "start" -> {
                val target = rest.lowercase()
                val winId = when (target) {
                    "calc" -> "calc"
                    "paint", "mspaint" -> "paint"
                    "sweeper", "heartsweeper" -> "sweeper"
                    "explorer" -> "explorer"
                    "mail" -> "mail"
                    "voyager", "ie", "browser" -> "browser"
                    "whatsapp", "wa" -> "whatsapp"
                    "camera", "cam" -> "camera"
                    "qr", "qrscanner", "scanner" -> "qrscanner"
                    "phone", "dialer" -> "phone"
                    "clock", "alarm", "timer" -> "clock"
                    "control" -> "control"
                    "weather" -> "weather"
                    "reader", "books" -> "reader"
                    "music" -> "music"
                    "gallery", "pictures" -> "gallery"
                    "calendar" -> "calendar"
                    "reminders" -> "reminders"
                    "todo" -> "habits"
                    "recorder" -> "recorder"
                    "settings", "display" -> "settings"
                    "updates" -> "status"
                    "taskmgr" -> "taskmgr"
                    else -> null
                }
                if (winId != null) {
                    vm.openWindow(winId)
                    listOf("Starting $target...")
                } else {
                    listOf("Bad command or file name: $target")
                }
            }
            else -> listOf("'$cmdLine' is not recognized. Type 'help'.")
        }
        lines = lines + "$promptBefore$cmdLine" + output
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(6.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFCCCCCC)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${currentPath()}>",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFF39FF14)
            )
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White),
                cursorBrush = SolidColor(Color(0xFF39FF14)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { execute(input) }),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )
            RetroButton(label = "Run", palette = palette, onClick = { execute(input) })
        }
    }
}
