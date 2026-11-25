package com.retrovibe.os.ui.windows

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.scheduleReminderNotification
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.store.todayIso
import com.retrovibe.os.theme.RetroThemeColors
import com.retrovibe.os.theme.RetroThemes
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton
import java.time.LocalDate
import java.time.YearMonth

private val PICKER_MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
private val PICKER_SHORT_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val PICKER_WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")

@Composable
fun RemindersContent(vm: OSViewModel) {
    val context = LocalContext.current
    val theme = RetroThemes.of(vm.themeId)
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(todayIso()) }
    var notifyEnabled by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifyEnabled = granted
    }

    if (showDatePicker) {
        val initial = try { LocalDate.parse(dueDate) } catch (_: Throwable) { LocalDate.now() }
        RetroDatePickerDialog(
            initialDate = initial,
            theme = theme,
            soundEnabled = vm.soundEnabled,
            onDismiss = { showDatePicker = false },
            onSelect = { picked ->
                dueDate = picked.toString()
                showDatePicker = false
            }
        )
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
                    RetroButton(dueDate, theme, vm.soundEnabled, onClick = { showDatePicker = true })
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

@Composable
private fun RetroDatePickerDialog(
    initialDate: LocalDate,
    theme: RetroThemeColors,
    soundEnabled: Boolean,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    val today = remember { LocalDate.now() }
    var viewYear by remember { mutableIntStateOf(initialDate.year) }
    var viewMonth by remember { mutableIntStateOf(initialDate.monthValue) }
    var pickedDate by remember { mutableStateOf(initialDate) }
    var pickerMode by remember { mutableStateOf("none") } // none | month | year

    val ym = YearMonth.of(viewYear, viewMonth)
    val firstDow = ym.atDay(1).dayOfWeek.value % 7 // Sunday = 0
    val daysInMonth = ym.lengthOfMonth()
    val cells = List(firstDow) { null } + (1..daysInMonth).toList()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, theme.borderDeep)
                .background(theme.windowBg)
        ) {
            BevelBox(theme = theme, raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Win98 Gradient Title Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(theme.titleBarActive, theme.titleBarAccent)))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PixelIcon("calendar", 14.dp)
                        Text(
                            "Select Date",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.titleTextActive,
                            modifier = Modifier.weight(1f)
                        )
                        RetroButton("×", theme, soundEnabled, onClick = onDismiss)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Month & Year Navigation
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RetroButton("«", theme, soundEnabled, onClick = { viewYear-- })
                            RetroButton("◀", theme, soundEnabled, onClick = {
                                if (viewMonth == 1) { viewMonth = 12; viewYear-- } else viewMonth--
                            })
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RetroButton(
                                    PICKER_MONTHS[viewMonth - 1],
                                    theme,
                                    soundEnabled,
                                    active = pickerMode == "month",
                                    modifier = Modifier.weight(1f),
                                    onClick = { pickerMode = if (pickerMode == "month") "none" else "month" }
                                )
                                RetroButton(
                                    viewYear.toString(),
                                    theme,
                                    soundEnabled,
                                    active = pickerMode == "year",
                                    onClick = { pickerMode = if (pickerMode == "year") "none" else "year" }
                                )
                            }
                            RetroButton("▶", theme, soundEnabled, onClick = {
                                if (viewMonth == 12) { viewMonth = 1; viewYear++ } else viewMonth++
                            })
                            RetroButton("»", theme, soundEnabled, onClick = { viewYear++ })
                        }

                        // Quick Month Picker
                        if (pickerMode == "month") {
                            BevelBox(theme = theme, raised = true, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (row in 0 until 3) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                            for (col in 0 until 4) {
                                                val idx = row * 4 + col
                                                RetroButton(
                                                    PICKER_SHORT_MONTHS[idx],
                                                    theme,
                                                    soundEnabled,
                                                    active = viewMonth == idx + 1,
                                                    modifier = Modifier.weight(1f),
                                                    onClick = { viewMonth = idx + 1; pickerMode = "none" }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Quick Year Picker
                        if (pickerMode == "year") {
                            BevelBox(theme = theme, raised = true, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RetroButton("-10y", theme, soundEnabled, onClick = { viewYear -= 10 })
                                        Text(
                                            "${viewYear - 5} – ${viewYear + 6}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.textPrimary
                                        )
                                        RetroButton("+10y", theme, soundEnabled, onClick = { viewYear += 10 })
                                    }
                                    val startY = viewYear - 5
                                    for (row in 0 until 3) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                            for (col in 0 until 4) {
                                                val yr = startY + row * 4 + col
                                                RetroButton(
                                                    yr.toString(),
                                                    theme,
                                                    soundEnabled,
                                                    active = yr == viewYear,
                                                    modifier = Modifier.weight(1f),
                                                    onClick = { viewYear = yr; pickerMode = "none" }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Calendar Days Grid
                        BevelBox(theme = theme, raised = false, modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(theme.windowSurface)
                                    .padding(6.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    PICKER_WEEKDAYS.forEach { w ->
                                        Text(
                                            w,
                                            textAlign = TextAlign.Center,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.textSecondary,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                cells.chunked(7).forEach { week ->
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        for (i in 0 until 7) {
                                            val day = week.getOrNull(i)
                                            if (day == null) {
                                                Spacer(Modifier.weight(1f).height(30.dp))
                                            } else {
                                                val date = LocalDate.of(viewYear, viewMonth, day)
                                                val isSel = date == pickedDate
                                                val isToday = date == today
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(30.dp)
                                                        .background(
                                                            when {
                                                                isSel -> theme.titleBarActive
                                                                isToday -> theme.accentSoft
                                                                else -> Color.Transparent
                                                            }
                                                        )
                                                        .border(
                                                            width = if (isSel || isToday) 1.dp else 0.dp,
                                                            color = when {
                                                                isSel -> theme.borderDeep
                                                                isToday -> theme.borderDark
                                                                else -> Color.Transparent
                                                            }
                                                        )
                                                        .clickable {
                                                            RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
                                                            pickedDate = date
                                                        }
                                                ) {
                                                    Text(
                                                        day.toString(),
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSel || isToday) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSel) theme.titleTextActive else theme.textPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Footer: Selected preview + Today / Cancel / OK
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            RetroButton("Today", theme, soundEnabled, onClick = {
                                viewYear = today.year
                                viewMonth = today.monthValue
                                pickedDate = today
                                pickerMode = "none"
                            })
                            Text(
                                pickedDate.toString(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = theme.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                            RetroButton("Cancel", theme, soundEnabled, onClick = onDismiss)
                            RetroButton("OK", theme, soundEnabled, active = true, onClick = { onSelect(pickedDate) })
                        }
                    }
                }
            }
        }
    }
}
