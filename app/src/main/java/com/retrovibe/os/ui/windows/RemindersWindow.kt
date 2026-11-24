package com.retrovibe.os.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroThemes
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.RetroButton

@Composable
fun RemindersContent(vm: OSViewModel) {
    val theme = RetroThemes.of(vm.themeId)
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var mins by remember { mutableStateOf("15") }

    Column(modifier = Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BevelBox(theme = theme, raised = true, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("New Retro Reminder", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textPrimary),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().background(theme.windowSurface).border(1.dp, theme.borderDark).padding(5.dp)) {
                            if (title.isEmpty()) Text("Title...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textSecondary)
                            inner()
                        }
                    }
                )
                BasicTextField(
                    value = message,
                    onValueChange = { message = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textPrimary),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().background(theme.windowSurface).border(1.dp, theme.borderDark).padding(5.dp)) {
                            if (message.isEmpty()) Text("Reminder message...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textSecondary)
                            inner()
                        }
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("In (min):", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = theme.textSecondary)
                    BasicTextField(
                        value = mins,
                        onValueChange = { mins = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textPrimary),
                        modifier = Modifier.width(44.dp).background(theme.windowSurface).border(1.dp, theme.borderDark).padding(4.dp)
                    )
                    RetroButton("+ Add Reminder", theme, vm.soundEnabled, modifier = Modifier.weight(1f), onClick = {
                        if (message.isNotBlank()) {
                            vm.addReminder(title, message.trim(), "In ${mins.ifBlank { "15" }} min")
                            title = ""
                            message = ""
                        }
                    })
                }
            }
        }

        BevelBox(theme = theme, raised = false, modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (vm.reminders.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("No reminders yet. Add one above!", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textSecondary)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(vm.reminders, key = { it.id }) { r ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            RetroButton(if (r.completed) "X" else " ", theme, vm.soundEnabled, active = r.completed, onClick = { vm.toggleReminder(r.id) })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${r.title} · ${r.timeLabel}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = if (r.completed) TextDecoration.LineThrough else TextDecoration.None,
                                    color = theme.textPrimary
                                )
                                Text(r.message, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = theme.textSecondary)
                            }
                            RetroButton("×", theme, vm.soundEnabled, onClick = { vm.deleteReminder(r.id) })
                        }
                        HorizontalDivider(color = theme.borderDark, thickness = 1.dp)
                    }
                }
            }
        }
    }
}
