package com.retrovibe.os.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton
import com.retrovibe.os.ui.win98Bevel
import com.retrovibe.os.theme.textDark

@Composable
fun RunDialogWindow(vm: OSViewModel, palette: RetroPalette) {
    var command by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun execute() {
        val cmd = command.trim().lowercase()
        if (cmd.isEmpty()) return
        val map = mapOf(
            "cmd" to "terminal",
            "command" to "terminal",
            "dos" to "terminal",
            "calc" to "calc",
            "mspaint" to "paint",
            "paint" to "paint",
            "explorer" to "explorer",
            "iexplore" to "browser",
            "browser" to "browser",
            "mail" to "mail",
            "heartsweeper" to "sweeper",
            "sweeper" to "sweeper",
            "taskmgr" to "taskmgr",
            "display" to "settings",
            "settings" to "settings",
            "updates" to "status",
            "calendar" to "calendar",
            "reminders" to "reminders",
            "todo" to "habits",
            "music" to "music",
            "gallery" to "gallery",
            "recorder" to "recorder"
        )
        when {
            cmd == "notepad" -> {
                vm.createNote(pinned = true)
                vm.closeWindow("run")
            }
            cmd == "cat" -> {
                vm.deskCatEnabled = !vm.deskCatEnabled
                vm.persist()
                RetroSoundEngine.play(RetroSound.PURR, vm.soundEnabled)
                vm.closeWindow("run")
            }
            map.containsKey(cmd) -> {
                vm.openWindow(map.getValue(cmd))
                vm.closeWindow("run")
            }
            else -> {
                RetroSoundEngine.play(RetroSound.ERROR, vm.soundEnabled)
                errorMsg = "Cannot find '$cmd'. Try: calc, mspaint, notepad, cmd, cat"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelIcon("run", 28.dp)
            Text(
                text = "Type the name of a program, folder, or command, and RetroVibe OS will open it for you.",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = palette.textDark
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Open:", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = palette.textDark)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .win98Bevel(sunken = true, bg = Color.White)
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                BasicTextField(
                    value = command,
                    onValueChange = { command = it; errorMsg = null },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { execute() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (errorMsg != null) {
            Text(errorMsg!!, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFFAA0000))
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RetroButton(label = "OK", palette = palette, active = true, onClick = { execute() })
            Spacer(Modifier.width(6.dp))
            RetroButton(label = "Cancel", palette = palette, onClick = { vm.closeWindow("run") })
        }
    }
}
