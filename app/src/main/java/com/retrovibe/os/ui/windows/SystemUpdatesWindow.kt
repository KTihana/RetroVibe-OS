package com.retrovibe.os.ui.windows

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.RetroButton
import com.retrovibe.os.ui.win98Bevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val APP_VERSION = "2.0.0"

@Composable
fun SystemUpdatesWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repoInput by remember { mutableStateOf(vm.githubRepo) }
    var checking by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Enter owner/repo and tap Check for Updates.") }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var releaseUrl by remember { mutableStateOf<String?>(null) }
    var releaseNotes by remember { mutableStateOf<String?>(null) }

    val bm = remember { context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager }
    val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 100

    fun checkGitHubUpdates() {
        val repo = repoInput.trim().removePrefix("https://github.com/").trim('/')
        if (!repo.contains("/")) {
            statusText = "Format must be: username/repository"
            RetroSoundEngine.play(RetroSound.ERROR, vm.soundEnabled)
            return
        }
        vm.githubRepo = repo
        vm.persist()
        checking = true
        statusText = "Contacting GitHub Releases API..."
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    val conn = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.setRequestProperty("User-Agent", "RetroVibe-OS-Android")
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    if (conn.responseCode == 404) throw IllegalStateException("No published GitHub releases yet.")
                    if (conn.responseCode !in 200..299) throw IllegalStateException("GitHub HTTP ${conn.responseCode}")
                    JSONObject(conn.inputStream.bufferedReader().readText())
                }
                val tag = res.optString("tag_name").removePrefix("v")
                val htmlUrl = res.optString("html_url")
                val assets = res.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                            apkUrl = a.optString("browser_download_url")
                            break
                        }
                    }
                }
                latestVersion = tag
                releaseUrl = apkUrl ?: htmlUrl
                releaseNotes = res.optString("body").take(260)
                statusText = if (tag != APP_VERSION) "New version v$tag available!" else "You are up to date (v$APP_VERSION)."
                RetroSoundEngine.play(RetroSound.SUCCESS, vm.soundEnabled)
            } catch (e: Throwable) {
                statusText = "Update check failed: ${e.message}"
                RetroSoundEngine.play(RetroSound.ERROR, vm.soundEnabled)
            } finally {
                checking = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // System summary
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .win98Bevel(sunken = true, bg = Color.White)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("RetroVibe OS (Kotlin Native)", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = palette.accent)
            Text("Installed Version: v$APP_VERSION", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            Text("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            Text("Battery: $batteryPct%  |  Storage: %.1f KB".format(vm.storageSizeKb()), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }

        // GitHub Updater
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .win98Bevel(sunken = true, bg = Color.White)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("GitHub App Updater", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Black)
            Text("Repository (owner/repo):", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF555555))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .win98Bevel(sunken = true, bg = Color(0xFFFAFAFA))
                    .padding(6.dp)
            ) {
                BasicTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RetroButton(
                    label = if (checking) "Checking..." else "Check for Updates",
                    palette = palette,
                    active = true,
                    onClick = { if (!checking) checkGitHubUpdates() }
                )
                if (releaseUrl != null) {
                    RetroButton(
                        label = "Download v${latestVersion ?: ""}",
                        palette = palette,
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
                            } catch (_: Throwable) {
                            }
                        }
                    )
                }
            }
            Text(statusText, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = palette.accent)
            if (!releaseNotes.isNullOrBlank()) {
                Text(releaseNotes!!, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF333333))
            }
        }
    }
}
