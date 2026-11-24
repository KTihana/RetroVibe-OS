package com.retrovibe.os.ui.windows

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.retrovibe.os.scheduleReminderNotification
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.store.todayIso
import com.retrovibe.os.theme.RetroThemes
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.RetroButton
import java.time.LocalDate

@Composable
fun RemindersContent(vm: OSViewModel) {
    val context = LocalContext.current
    val theme = RetroThemes.of(vm.themeId)
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(todayIso()) }
    var notifyEnabled by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifyEnabled = granted
    }

    fun openDatePicker() {
        val current = try { LocalDate.parse(dueDate) } catch (_: Throwable) { LocalDate.now() }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                dueDate = LocalDate.of(year, month + 1, dayOfMonth).toString()
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth
        ).show()
    }

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
                            if (message.isEmpty()) Text("Reminder note...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textSecondary)
                            inner()
                        }
                    }
                )

                // Date selection row
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Date:", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = theme.textSecondary)
                    RetroButton(dueDate, theme, vm.soundEnabled, onClick = { openDatePicker() })
                    RetroButton("Today", theme, vm.soundEnabled, onClick = { dueDate = LocalDate.now().toString() })
                    RetroButton("Tmrw", theme, vm.soundEnabled, onClick = { dueDate = LocalDate.now().plusDays(1).toString() })
                }

                // Notification option + Add button
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RetroButton(
                        label = if (notifyEnabled) "[x] Phone Alert" else "[ ] Phone Alert",
                        theme = theme,
                        soundEnabled = vm.soundEnabled,
                        active = notifyEnabled,
                        onClick = {
                            if (!notifyEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                if (granted) notifyEnabled = true else notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                notifyEnabled = !notifyEnabled
                            }
                        }
                    )
                    RetroButton("+ Add", theme, vm.soundEnabled, active = true, modifier = Modifier.weight(1f), onClick = {
                        if (title.isNotBlank() || message.isNotBlank()) {
                            val finalTitle = title.ifBlank { "Reminder" }
                            val finalMsg = message.trim()
                            vm.addReminder(finalTitle, finalMsg, dueDate, notifyEnabled)
                            if (notifyEnabled) {
                                val newest = vm.reminders.firstOrNull()
                                if (newest != null) {
                                    scheduleReminderNotification(context, newest.id, finalTitle, finalMsg.ifBlank { finalTitle }, dueDate)
                                }
                            }
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
                    Text("No reminders yet. Set a date above!", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textSecondary)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(vm.reminders, key = { it.id }) { r ->
                        val dateBadge = r.dueDate.ifBlank { r.timeLabel }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            RetroButton(if (r.completed) "X" else " ", theme, vm.soundEnabled, active = r.completed, onClick = { vm.toggleReminder(r.id) })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${r.title} · $dateBadge${if (r.notifyEnabled) " [Bell]" else ""}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = if (r.completed) TextDecoration.LineThrough else TextDecoration.None,
                                    color = theme.textPrimary
                                )
                                if (r.message.isNotBlank()) {
                                    Text(r.message, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = theme.textSecondary)
                                }
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
