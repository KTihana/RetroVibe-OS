package com.retrovibe.os.ui.windows

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.RetroButton

private data class InstalledAppEntry(
    val label: String,
    val packageName: String
)

@Composable
fun PhoneAppsWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }

    val apps = remember {
        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty()
                    if (label.isEmpty()) null else InstalledAppEntry(label, pkg)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    val filtered = remember(apps, search) {
        val q = search.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BasicTextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = palette.textPrimary),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(palette.windowSurface)
                            .border(1.dp, palette.borderDark)
                            .padding(5.dp)
                    ) {
                        if (search.isEmpty()) {
                            Text("Search phone apps...", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = palette.textSecondary)
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            if (search.isNotEmpty()) {
                RetroButton("×", palette, onClick = { search = "" })
            }
        }

        BevelBox(theme = palette, raised = false, modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No apps found.", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = palette.textSecondary)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    RetroSoundEngine.play(RetroSound.CLICK, vm.soundEnabled)
                                    try {
                                        val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                        if (launch != null) context.startActivity(launch)
                                    } catch (_: Throwable) {}
                                }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "> ${app.label}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "OPEN",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = palette.textSecondary
                            )
                        }
                        HorizontalDivider(color = palette.borderDark, thickness = 1.dp)
                    }
                }
            }
        }
    }
}
