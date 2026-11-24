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

@Composable
fun TerminalWindow(vm: OSViewModel, palette: RetroPalette) {
    var input by remember { mutableStateOf("") }
    var lines by remember {
        mutableStateOf(
            listOf(
                "RetroVibe OS [Version 98.4.2026]",
                "(C) Copyright RetroVibe Corp 1998-2026.",
                "Secret MS-DOS Prompt unlocked! Type 'help' for commands.",
                ""
            )
        )
    }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    fun execute(raw: String) {
        val cmd = raw.trim()
        input = ""
        if (cmd.isEmpty()) return
        RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
        val lower = cmd.lowercase()
        val output: List<String> = when {
            lower == "help" -> listOf(
                "Available commands:",
                "  HELP      - Show this help",
                "  VER       - Show RetroVibe OS version",
                "  DIR       - List C:\\RETROVIBE files",
                "  DATE/TIME - Show current system clock",
                "  HEARTS    - Print ASCII hearts",
                "  CAT       - Toggle Desk Cat pet",
                "  START app - Launch calc, paint, sweeper, explorer, mail, ie",
                "  CLS       - Clear screen",
                "  EXIT      - Close MS-DOS Prompt"
            )
            lower == "ver" -> listOf("RetroVibe OS 98 Native Kotlin Edition [Build 2026]")
            lower == "dir" -> listOf(
                " Volume in drive C is RETROVIBE",
                "  ${vm.notes.size} Note(s)   ${vm.artworks.size} Painting(s)   ${vm.voiceMemos.size} Memo(s)",
                "  ${vm.todos.size} To-Do(s)  ${vm.trash.size} Trash item(s)"
            )
            lower == "date" || lower == "time" -> listOf(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            )
            lower == "hearts" -> listOf(
                "  **   **    **   **",
                " **** ****  **** ****",
                "  *******    *******",
                "    ***        ***",
                "     *          *"
            )
            lower == "cat" -> {
                vm.deskCatEnabled = !vm.deskCatEnabled
                vm.persist()
                listOf("Desk Cat is now ${if (vm.deskCatEnabled) "ENABLED" else "DISABLED"}.")
            }
            lower == "cls" -> {
                lines = emptyList()
                return
            }
            lower == "exit" -> {
                vm.closeWindow("terminal")
                return
            }
            lower.startsWith("start ") -> {
                val target = lower.removePrefix("start ").trim()
                val winId = when (target) {
                    "calc" -> "calc"
                    "paint", "mspaint" -> "paint"
                    "sweeper", "heartsweeper" -> "sweeper"
                    "explorer" -> "explorer"
                    "mail" -> "mail"
                    "ie", "browser" -> "browser"
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
            else -> listOf("'$cmd' is not recognized. Type 'help'.")
        }
        lines = lines + "C:\\RETROVIBE>$cmd" + output
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
                text = "C:\\RETROVIBE>",
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
