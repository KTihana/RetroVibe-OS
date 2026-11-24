package com.retrovibe.os.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.store.todayIso
import com.retrovibe.os.theme.RetroThemes
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton
import java.time.LocalDate
import java.time.YearMonth

private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
private val SHORT_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")
private val STAMPS = listOf("heart", "star", "cake", "sparkle", "music")

@Composable
fun CalendarContent(vm: OSViewModel) {
    val theme = RetroThemes.of(vm.themeId)
    val now = LocalDate.now()
    var year by remember { mutableIntStateOf(now.year) }
    var month by remember { mutableIntStateOf(now.monthValue) } // 1..12
    var selectedDate by remember { mutableStateOf(todayIso()) }
    var pickerMode by remember { mutableStateOf("none") } // none | month | year

    val ym = YearMonth.of(year, month)
    val firstDow = ym.atDay(1).dayOfWeek.value % 7 // Sunday = 0
    val daysInMonth = ym.lengthOfMonth()
    val cells = List(firstDow) { null } + (1..daysInMonth).toList()

    val selectedEntry = vm.calendarEntries[selectedDate]

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Quick Year & Month Nav
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
            RetroButton("«", theme, vm.soundEnabled, onClick = { year-- })
            RetroButton("◀", theme, vm.soundEnabled, onClick = {
                if (month == 1) { month = 12; year-- } else month--
            })
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                RetroButton(MONTHS[month - 1], theme, vm.soundEnabled, active = pickerMode == "month", modifier = Modifier.weight(1f), onClick = {
                    pickerMode = if (pickerMode == "month") "none" else "month"
                })
                RetroButton(year.toString(), theme, vm.soundEnabled, active = pickerMode == "year", onClick = {
                    pickerMode = if (pickerMode == "year") "none" else "year"
                })
            }
            RetroButton("▶", theme, vm.soundEnabled, onClick = {
                if (month == 12) { month = 1; year++ } else month++
            })
            RetroButton("»", theme, vm.soundEnabled, onClick = { year++ })
        }

        if (pickerMode == "month") {
            BevelBox(theme = theme, raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in 0 until 3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 4) {
                                val idx = row * 4 + col
                                RetroButton(SHORT_MONTHS[idx], theme, vm.soundEnabled, active = month == idx + 1, modifier = Modifier.weight(1f), onClick = {
                                    month = idx + 1; pickerMode = "none"
                                })
                            }
                        }
                    }
                    RetroButton("Jump to Today", theme, vm.soundEnabled, modifier = Modifier.fillMaxWidth(), onClick = {
                        val d = LocalDate.now()
                        year = d.year; month = d.monthValue; selectedDate = todayIso(); pickerMode = "none"
                    })
                }
            }
        }

        if (pickerMode == "year") {
            BevelBox(theme = theme, raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RetroButton("-10y", theme, vm.soundEnabled, onClick = { year -= 10 })
                        Text("${year - 5} – ${year + 6}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                        RetroButton("+10y", theme, vm.soundEnabled, onClick = { year += 10 })
                    }
                    val startY = year - 5
                    for (row in 0 until 3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 4) {
                                val yr = startY + row * 4 + col
                                RetroButton(yr.toString(), theme, vm.soundEnabled, active = yr == year, modifier = Modifier.weight(1f), onClick = {
                                    year = yr; pickerMode = "none"
                                })
                            }
                        }
                    }
                }
            }
        }

        // Calendar Grid
        BevelBox(theme = theme, raised = false, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    WEEKDAYS.forEach { w ->
                        Text(w, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = theme.textSecondary, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(4.dp))
                cells.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (i in 0 until 7) {
                            val day = week.getOrNull(i)
                            if (day == null) {
                                Spacer(Modifier.weight(1f).height(34.dp))
                            } else {
                                val iso = "%04d-%02d-%02d".format(year, month, day)
                                val entry = vm.calendarEntries[iso]
                                val isSel = iso == selectedDate
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .background(if (isSel) theme.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                                        .border(if (isSel) 1.dp else 0.dp, if (isSel) theme.borderDeep else androidx.compose.ui.graphics.Color.Transparent)
                                        .clickable { selectedDate = iso }
                                ) {
                                    if (entry?.stamp != null) {
                                        PixelIcon(entry.stamp, 22.dp)
                                        Text(day.toString(), fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, color = theme.textPrimary)
                                    } else {
                                        Text(day.toString(), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected Day Inspector
        BevelBox(theme = theme, raised = true, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Selected: $selectedDate", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    STAMPS.forEach { s ->
                        RetroButton(theme = theme, soundEnabled = vm.soundEnabled, active = selectedEntry?.stamp == s, onClick = {
                            vm.setCalendarDay(selectedDate, stamp = s)
                        }) { PixelIcon(s, 16.dp) }
                    }
                    RetroButton("Clear", theme, vm.soundEnabled, onClick = { vm.setCalendarDay(selectedDate, clearStamp = true) })
                }
                BasicTextField(
                    value = selectedEntry?.note ?: "",
                    onValueChange = { vm.setCalendarDay(selectedDate, note = it) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textPrimary),
                    modifier = Modifier.fillMaxWidth().background(theme.windowSurface).border(1.dp, theme.borderDark).padding(6.dp)
                )
            }
        }
    }
}
