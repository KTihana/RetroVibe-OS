package com.retrovibe.os.ui.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.store.todayIso
import com.retrovibe.os.theme.RetroThemes
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton
import kotlin.math.roundToInt

@Composable
fun TodoListContent(vm: OSViewModel) {
    val theme = RetroThemes.of(vm.themeId)
    var newTask by remember { mutableStateOf("") }
    val today = todayIso()

    val doneCount = vm.todos.count { today in it.completedDates }
    val pct = if (vm.todos.isEmpty()) 0 else ((doneCount.toFloat() / vm.todos.size) * 100f).roundToInt()
    val filledBlocks = ((pct / 100f) * 12).roundToInt()

    Column(modifier = Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelIcon("heart", 24.dp)
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("To do list", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                    Text("$pct%", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                }
                Spacer(Modifier.height(3.dp))
                BevelBox(theme = theme, raised = false, modifier = Modifier.fillMaxWidth().height(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxSize().padding(2.dp)) {
                        for (i in 0 until 12) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(if (i < filledBlocks) theme.accentPink else Color.Transparent)
                            )
                        }
                    }
                }
            }
        }

        BevelBox(theme = theme, raised = false, modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (vm.todos.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("No tasks yet. Add one below!", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = theme.textSecondary)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(vm.todos, key = { it.id }) { h ->
                        val done = today in h.completedDates
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f).clickable { vm.toggleTodoToday(h.id) }.padding(vertical = 2.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(if (done) theme.accentPink else theme.windowSurface)
                                        .border(1.5.dp, theme.borderDeep)
                                ) {
                                    if (done) Text("X", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White)
                                }
                                Text(
                                    h.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                                    color = theme.textPrimary.copy(alpha = if (done) 0.6f else 1f),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            RetroButton(theme = theme, soundEnabled = vm.soundEnabled, onClick = { vm.deleteTodo(h.id) }) {
                                PixelIcon("trash", 12.dp)
                            }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = newTask,
                onValueChange = { newTask = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textPrimary),
                decorationBox = { inner ->
                    Box(Modifier.weight(1f).background(theme.windowSurface).border(1.dp, theme.borderDark).padding(5.dp)) {
                        if (newTask.isEmpty()) Text("Add to-do item...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = theme.textSecondary)
                        inner()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            RetroButton("Add", theme, vm.soundEnabled, onClick = {
                if (newTask.isNotBlank()) {
                    vm.addTodo(newTask)
                    newTask = ""
                }
            })
        }
    }
}
