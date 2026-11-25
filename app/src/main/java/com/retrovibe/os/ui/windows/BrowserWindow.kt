package com.retrovibe.os.ui.windows

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton
import com.retrovibe.os.ui.win98Bevel
import kotlinx.coroutines.delay
import com.retrovibe.os.theme.textDark
import com.retrovibe.os.theme.accent

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWindow(vm: OSViewModel, palette: RetroPalette) {
    var addressInput by remember { mutableStateOf("about:home") }
    var currentUrl by remember { mutableStateOf("about:home") }
    var searchQuery by remember { mutableStateOf("") }
    var showFavorites by remember { mutableStateOf(false) }
    var isDialing by remember { mutableStateOf(false) }
    var dialStep by remember { mutableStateOf("Dialing 555-0198...") }
    var dialDoneOnce by remember { mutableStateOf(false) }
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "about:home") return "about:home"
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.contains(".") && !trimmed.contains(" ")) return "https://$trimmed"
        return "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
    }

    fun navigate(raw: String) {
        val target = normalizeUrl(raw)
        addressInput = target
        showFavorites = false
        if (target == "about:home") {
            currentUrl = "about:home"
            return
        }
        if (!dialDoneOnce) {
            pendingUrl = target
            isDialing = true
            RetroSoundEngine.play(RetroSound.DIALUP, vm.soundEnabled)
        } else {
            currentUrl = target
            webViewRef?.loadUrl(target)
        }
    }

    LaunchedEffect(vm.pendingBrowserUrl) {
        val incoming = vm.pendingBrowserUrl
        if (!incoming.isNullOrBlank()) {
            vm.pendingBrowserUrl = null
            dialDoneOnce = true
            isDialing = false
            navigate(incoming)
        }
    }

    LaunchedEffect(isDialing) {
        if (isDialing) {
            dialStep = "Dialing 555-0198 (56k Modem)..."
            delay(500)
            dialStep = "Verifying username and password..."
            delay(600)
            dialStep = "Connected at 56,000 bps!"
            delay(400)
            isDialing = false
            dialDoneOnce = true
            pendingUrl?.let {
                currentUrl = it
                webViewRef?.loadUrl(it)
            }
            pendingUrl = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(palette.windowBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RetroButton(label = "<-", palette = palette, onClick = { webViewRef?.goBack() })
                RetroButton(label = "->", palette = palette, onClick = { webViewRef?.goForward() })
                RetroButton(label = "R", palette = palette, onClick = { webViewRef?.reload() })
                RetroButton(label = "Home", palette = palette, onClick = { navigate("about:home") })
                RetroButton(
                    label = "Favorites",
                    palette = palette,
                    active = showFavorites,
                    onClick = { showFavorites = !showFavorites }
                )
                RetroButton(
                    label = "+ *",
                    palette = palette,
                    onClick = {
                        if (currentUrl != "about:home") {
                            val title = webViewRef?.title?.take(28) ?: currentUrl.removePrefix("https://").take(24)
                            vm.addBrowserFavorite(title, currentUrl)
                        }
                    }
                )
            }

            // Address bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Address:", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = palette.textDark)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .win98Bevel(sunken = true, bg = Color.White)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    BasicTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                RetroButton(label = "Go", palette = palette, onClick = { navigate(addressInput) })
            }

            // Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(4.dp)
                    .win98Bevel(sunken = true, bg = Color.White)
            ) {
                if (currentUrl == "about:home") {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        PixelIcon("ie", 40.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "RetroVibe Internet Voyager",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = palette.accent
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Type a URL above or search the Web below:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF555555)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(0.92f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .win98Bevel(sunken = true, bg = Color.White)
                                    .padding(6.dp)
                            ) {
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.Black),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            RetroButton(label = "Search", palette = palette, active = true, onClick = {
                                if (searchQuery.isNotBlank()) navigate(searchQuery)
                            })
                        }
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        if (url != null) addressInput = url
                                    }
                                }
                                loadUrl(currentUrl)
                                webViewRef = this
                            }
                        },
                        update = { wv ->
                            webViewRef = wv
                        }
                    )
                }

                // Favorites dropdown
                if (showFavorites) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .width(220.dp)
                            .win98Bevel(sunken = false, bg = palette.windowBg)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "Favorites (${vm.browserFavorites.size})",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = palette.textDark,
                            modifier = Modifier.padding(4.dp)
                        )
                        if (vm.browserFavorites.isEmpty()) {
                            Text(
                                text = "Tap + * on any page to save.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF666666),
                                modifier = Modifier.padding(4.dp)
                            )
                        } else {
                            vm.browserFavorites.forEach { fav ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { navigate(fav.url) }
                                        .padding(horizontal = 4.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = fav.title,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "[x]",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFFAA0000),
                                        modifier = Modifier.clickable { vm.removeBrowserFavorite(fav.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 56k Dial-Up dialog
        if (isDialing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(250.dp)
                        .win98Bevel(sunken = false, bg = palette.windowBg)
                        .border(1.dp, Color.Black)
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Dial-Up Networking",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = palette.accent
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = dialStep,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = palette.textDark
                    )
                }
            }
        }
    }
}
