package com.retrovibe.os.ui.windows

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.core.content.FileProvider
import com.retrovibe.os.BuildConfig
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.theme.accent
import com.retrovibe.os.ui.RetroButton
import com.retrovibe.os.ui.win98Bevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private fun isNewerVersion(remote: String, local: String): Boolean {
    val rParts = remote.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val lParts = local.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val len = maxOf(rParts.size, lParts.size)
    for (i in 0 until len) {
        val r = rParts.getOrElse(i) { 0 }
        val l = lParts.getOrElse(i) { 0 }
        if (r != l) return r > l
    }
    return false
}

private fun openWithRedirects(urlStr: String): HttpURLConnection {
    var current = urlStr
    repeat(6) {
        val conn = URL(current).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", "RetroVibe-OS-Android")
        conn.connectTimeout = 12000
        conn.readTimeout = 20000
        val code = conn.responseCode
        if (code in 300..399) {
            val next = conn.getHeaderField("Location") ?: return conn
            conn.disconnect()
            current = next
        } else {
            return conn
        }
    }
    throw IllegalStateException("Too many redirects")
}

@Composable
fun SystemUpdatesWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installedVersion = BuildConfig.VERSION_NAME

    var repoInput by remember { mutableStateOf(vm.githubRepo.ifBlank { "KTihana/RetroVibe-OS" }) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Checking for updates...") }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var apkDownloadUrl by remember { mutableStateOf<String?>(null) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var releaseNotes by remember { mutableStateOf<String?>(null) }
    var updateAvailable by remember { mutableStateOf(false) }

    val bm = remember { context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager }
    val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 100

    fun installApkFile(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            statusText = "Allow 'Install unknown apps' for RetroVibe OS, then tap Install APK."
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permIntent)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        statusText = "Installer opened! Tap Update to finish."
    }

    fun downloadAndInstallUpdate(overrideUrl: String? = null) {
        val url = overrideUrl ?: apkDownloadUrl ?: return
        if (downloading) return
        downloading = true
        downloadProgress = 0f
        statusText = "Downloading update..."
        scope.launch {
            try {
                val outFile = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                    val file = File(dir, "RetroVibe-OS.apk")
                    val conn = openWithRedirects(url)
                    if (conn.responseCode !in 200..299) {
                        throw IllegalStateException("Download HTTP ${conn.responseCode}")
                    }
                    val total = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        FileOutputStream(file).use { output ->
                            val buf = ByteArray(16 * 1024)
                            var copied = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                output.write(buf, 0, n)
                                copied += n
                                if (total > 0L) {
                                    val pct = (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    withContext(Dispatchers.Main) {
                                        downloadProgress = pct
                                        statusText = "Downloading v${latestVersion ?: ""}... ${(pct * 100).toInt()}%"
                                    }
                                }
                            }
                        }
                    }
                    conn.disconnect()
                    file
                }
                downloadedApkFile = outFile
                downloadProgress = 1f
                RetroSoundEngine.play(RetroSound.SUCCESS, vm.soundEnabled)
                installApkFile(outFile)
            } catch (e: Throwable) {
                statusText = "Download failed: ${e.message}"
                RetroSoundEngine.play(RetroSound.ERROR, vm.soundEnabled)
            } finally {
                downloading = false
            }
        }
    }

    fun checkGitHubUpdates(autoDownload: Boolean = true) {
        val repo = repoInput.trim().removePrefix("https://github.com/").trim('/')
        if (!repo.contains("/")) {
            statusText = "Format must be: username/repository"
            RetroSoundEngine.play(RetroSound.ERROR, vm.soundEnabled)
            return
        }
        vm.githubRepo = repo
        vm.persist()
        checking = true
        statusText = "Checking for updates..."
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    val conn = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.setRequestProperty("User-Agent", "RetroVibe-OS-Android")
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    if (conn.responseCode == 404) throw IllegalStateException("No published releases yet.")
                    if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
                    JSONObject(conn.inputStream.bufferedReader().readText())
                }
                val tag = res.optString("tag_name").removePrefix("v")
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
                apkDownloadUrl = apkUrl
                releaseNotes = res.optString("body").take(320)
                val newer = isNewerVersion(tag, installedVersion)
                updateAvailable = newer
                checking = false

                if (newer && apkUrl != null && autoDownload) {
                    statusText = "Found v$tag! Starting download..."
                    downloadAndInstallUpdate(apkUrl)
                } else if (newer) {
                    statusText = "Update available: v$tag (installed: v$installedVersion)"
                    RetroSoundEngine.play(RetroSound.SUCCESS, vm.soundEnabled)
                } else {
                    statusText = "You are up to date! (v$installedVersion)"
                    RetroSoundEngine.play(RetroSound.SUCCESS, vm.soundEnabled)
                }
            } catch (e: Throwable) {
                checking = false
                statusText = "Update check failed: ${e.message}"
                RetroSoundEngine.play(RetroSound.ERROR, vm.soundEnabled)
            }
        }
    }

    // Auto-check and auto-download if a newer version is available
    LaunchedEffect(Unit) {
        checkGitHubUpdates(autoDownload = true)
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
            Text("Installed Version: v$installedVersion", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Black)
            Text("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Black)
            Text("Battery: $batteryPct%  |  Storage: %.1f KB".format(vm.storageSizeKb()), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Black)
        }

        // GitHub Updater
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .win98Bevel(sunken = true, bg = Color.White)
                .verticalScroll(rememberScrollState())
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
                    label = if (checking) "Checking..." else "Check & Update",
                    palette = palette,
                    active = !updateAvailable && downloadedApkFile == null,
                    onClick = { if (!checking && !downloading) checkGitHubUpdates(autoDownload = true) }
                )
                val readyFile = downloadedApkFile
                if (readyFile != null && readyFile.exists() && !downloading) {
                    RetroButton(
                        label = "Install APK",
                        palette = palette,
                        active = true,
                        onClick = { installApkFile(readyFile) }
                    )
                } else if (apkDownloadUrl != null) {
                    RetroButton(
                        label = when {
                            downloading -> "Downloading..."
                            updateAvailable -> "Download v${latestVersion ?: ""}"
                            else -> "Reinstall v${latestVersion ?: ""}"
                        },
                        palette = palette,
                        active = updateAvailable,
                        onClick = { if (!downloading) downloadAndInstallUpdate() }
                    )
                }
            }

            // Win98 chunky progress bar while downloading
            if (downloading || downloadProgress > 0f) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .win98Bevel(sunken = true, bg = Color(0xFFEAEAEA))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val blocks = 14
                    val filled = (downloadProgress * blocks).toInt()
                    for (i in 0 until blocks) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (i < filled) palette.titleBarActive else Color.Transparent)
                        )
                    }
                }
            }

            Text(statusText, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accent)

            if (!releaseNotes.isNullOrBlank()) {
                Text(releaseNotes!!, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF333333))
            }
        }
    }
}
