package com.retrovibe.os.ui.windows

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.theme.textDark
import com.retrovibe.os.ui.BevelBox

private const val TIKTOK_MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

private const val TIKTOK_HOME_URL = "https://www.tiktok.com/"
private const val TIKTOK_FOLLOWING_URL = "https://www.tiktok.com/following"
private const val TIKTOK_LIVE_URL = "https://www.tiktok.com/live"
private const val TIKTOK_FRIENDS_URL = "https://www.tiktok.com/friends"
private const val TIKTOK_UPLOAD_URL = "https://www.tiktok.com/upload"
private const val TIKTOK_SEARCH_URL = "https://www.tiktok.com/search"
private const val TIKTOK_LOGIN_URL = "https://www.tiktok.com/login"

private fun Color.toTikTokCssHex(): String {
    val argb = this.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

private fun buildWin98TikTokJs(
    palette: RetroPalette,
    skinEnabled: Boolean
): String {
    val bg = palette.windowBg.toTikTokCssHex()
    val surface = palette.windowSurface.toTikTokCssHex()
    val accent = palette.accentPink.toTikTokCssHex()
    val light = palette.borderLight.toTikTokCssHex()
    val dark = palette.borderDark.toTikTokCssHex()
    val deep = palette.borderDeep.toTikTokCssHex()
    val text = palette.textDark.toTikTokCssHex()
    val titleA = palette.titleBarActive.toTikTokCssHex()
    val titleB = palette.titleBarAccent.toTikTokCssHex()

    val skinCss = if (skinEnabled) """
        html, body, #app, #main-content-homepage_hot, button, input, textarea {
            font-family: "Tahoma", "MS Sans Serif", "Courier New", monospace !important;
        }
        body {
            background: $bg !important;
            color: $text !important;
        }
        /* Square retro Win98 geometry on UI chrome (keep video clean) */
        div, header, nav, footer, button, input, textarea, section, aside, a {
            border-radius: 0px !important;
        }
        /* Classic Win98 raised top header bar */
        header, [data-e2e="top-nav"], [class*="DivHeaderContainer"], [class*="HeaderContainer"] {
            background: linear-gradient(90deg, $titleA, $titleB) !important;
            border-bottom: 2px solid $deep !important;
            box-shadow: inset 1px 1px 0 $light, inset -1px -1px 0 $dark !important;
            color: #FFFFFF !important;
        }
        header *, [data-e2e="top-nav"] * {
            color: #FFFFFF !important;
        }
        /* Sunken Win98 inputs & search boxes */
        input, textarea, [contenteditable="true"] {
            background: #FFFFFF !important;
            color: #111111 !important;
            border: 1px solid $deep !important;
            box-shadow: inset 1px 1px 0 $dark, inset -1px -1px 0 $light !important;
        }
        /* Raised Win98 buttons & creator pills */
        button, [role="button"] {
            border: 1px solid $deep !important;
            box-shadow: inset 1px 1px 0 $light, inset -1px -1px 0 $dark !important;
        }
        /* Win98 comment drawer & dialogs */
        [class*="Comment"], [class*="Modal"], [role="dialog"] {
            background-color: $surface !important;
            color: $text !important;
            border: 2px solid $deep !important;
            box-shadow: inset 1px 1px 0 $light, inset -1px -1px 0 $dark !important;
        }
        /* Frame videos cleanly without altering video colors */
        video {
            border: 2px solid $deep !important;
            box-sizing: border-box !important;
        }
        /* Suppress intrusive "Open in TikTok App" banners */
        [class*="DownloadApp"], [class*="OpenApp"], [class*="AppBanner"], [data-e2e="open-app-banner"] {
            display: none !important;
        }
        /* Classic Win98 scrollbars */
        ::-webkit-scrollbar {
            width: 14px !important;
            height: 14px !important;
            background: $surface !important;
        }
        ::-webkit-scrollbar-thumb {
            background: $bg !important;
            border: 1px solid $deep !important;
            box-shadow: inset 1px 1px 0 $light, inset -1px -1px 0 $dark !important;
        }
        [aria-selected="true"] {
            border-bottom: 2px solid $accent !important;
        }
    """.trimIndent() else """
        [class*="DownloadApp"], [class*="OpenApp"], [class*="AppBanner"], [data-e2e="open-app-banner"] {
            display: none !important;
        }
    """.trimIndent()

    val escapedCss = skinCss
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("$", "\\$")

    return """
        (function() {
            try {
                var id = 'retrovibe-win98-tt-css';
                var el = document.getElementById(id);
                if (!el) {
                    el = document.createElement('style');
                    el.id = id;
                    (document.head || document.documentElement).appendChild(el);
                }
                el.textContent = `$escapedCss`;
            } catch (_) {}
        })();
    """.trimIndent()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TikTokWindow(vm: OSViewModel, palette: RetroPalette) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var win98Skin by remember { mutableStateOf(true) }
    var activeTab by remember { mutableStateOf("foryou") } // foryou | following | live | friends
    var statusText by remember { mutableStateOf("TikTok | Connecting...") }
    var loadProgress by remember { mutableStateOf(0) }
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        pendingFileCallback?.onReceiveValue(uris.toTypedArray())
        pendingFileCallback = null
    }

    fun applySkin(wv: WebView?) {
        if (wv == null) return
        wv.evaluateJavascript(buildWin98TikTokJs(palette, win98Skin), null)
    }

    LaunchedEffect(win98Skin, palette, webViewRef) {
        applySkin(webViewRef)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Classic Win98 Menu Bar: File  Edit  View  Tools  Help
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.windowBg)
                .padding(horizontal = 2.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Win98MenuItem("File", palette, vm.soundEnabled) {
                statusText = "TikTok | Reloading..."
                webViewRef?.reload()
            }
            Win98MenuItem("Edit", palette, vm.soundEnabled) {
                webViewRef?.goBack()
            }
            Win98MenuItem("View", palette, vm.soundEnabled) {
                win98Skin = !win98Skin
                statusText = if (win98Skin) "TikTok | Win98 Skin ON" else "TikTok | Win98 Skin OFF"
            }
            Win98MenuItem("Tools", palette, vm.soundEnabled) {
                statusText = "TikTok | Opening Login..."
                webViewRef?.loadUrl(TIKTOK_LOGIN_URL)
            }
            Win98MenuItem("Help", palette, vm.soundEnabled) {
                statusText = "TikTok | Tap Profile to sign in"
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderDark))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderLight))

        // Classic Win98 Top Icon Toolbar (Upload | Search | Profile | 98 Skin)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.windowBg)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Win98IconToolButton(
                icon = "tbUpload",
                label = "Upload",
                palette = palette,
                soundEnabled = vm.soundEnabled,
                onClick = {
                    statusText = "TikTok | Upload"
                    webViewRef?.loadUrl(TIKTOK_UPLOAD_URL)
                }
            )
            Win98IconToolButton(
                icon = "tbSearch",
                label = "Search",
                palette = palette,
                soundEnabled = vm.soundEnabled,
                onClick = {
                    statusText = "TikTok | Search"
                    webViewRef?.loadUrl(TIKTOK_SEARCH_URL)
                }
            )
            Win98IconToolButton(
                icon = "tbProfile",
                label = "Profile",
                palette = palette,
                soundEnabled = vm.soundEnabled,
                onClick = {
                    statusText = "TikTok | Login & Profile"
                    webViewRef?.loadUrl(TIKTOK_LOGIN_URL)
                }
            )
            Win98IconToolButton(
                icon = "tbSettings",
                label = if (win98Skin) "98 Skin" else "Classic",
                palette = palette,
                soundEnabled = vm.soundEnabled,
                active = win98Skin,
                onClick = { win98Skin = !win98Skin }
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderDark))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderLight))

        // Main TikTok Viewport inside sunken Win98 bevel
        BevelBox(
            theme = palette,
            raised = false,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.windowSurface)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                mediaPlaybackRequiresUserGesture = false
                                userAgentString = TIKTOK_MOBILE_UA
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val uri = request?.url ?: return false
                                    val scheme = uri.scheme?.lowercase() ?: ""
                                    val host = uri.host?.lowercase() ?: ""

                                    if (scheme == "intent" || scheme == "market" || scheme.startsWith("snssdk") || scheme == "tiktok") {
                                        return true
                                    }
                                    if (host.contains("play.google.com") || host.contains("onelink.me")) {
                                        return true
                                    }
                                    if (
                                        host.endsWith("tiktok.com") ||
                                        host.endsWith("tiktokv.com") ||
                                        host.endsWith("tiktokcdn.com") ||
                                        host.endsWith("byteoversea.com") ||
                                        host.endsWith("google.com") ||
                                        host.endsWith("apple.com") ||
                                        host.endsWith("facebook.com")
                                    ) {
                                        return false
                                    }
                                    if (scheme == "http" || scheme == "https") {
                                        vm.openBrowserUrl(uri.toString())
                                        return true
                                    }
                                    return true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    statusText = "TikTok | Online"
                                    applySkin(view)
                                    view?.postDelayed({ applySkin(view) }, 1200L)
                                    view?.postDelayed({ applySkin(view) }, 2800L)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                    if (newProgress in 1..99) {
                                        statusText = "TikTok | Loading $newProgress%..."
                                    }
                                }

                                override fun onPermissionRequest(request: PermissionRequest?) {
                                    request?.grant(request.resources)
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    pendingFileCallback?.onReceiveValue(null)
                                    pendingFileCallback = filePathCallback
                                    return try {
                                        filePickerLauncher.launch(arrayOf("*/*"))
                                        true
                                    } catch (_: Throwable) {
                                        pendingFileCallback = null
                                        false
                                    }
                                }
                            }

                            loadUrl(TIKTOK_HOME_URL)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Classic Win98 Bottom Navigation Bar (For You | Following | LIVE | Friends)
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderDark))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderLight))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.windowBg)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Win98IconToolButton(
                icon = "tbStar",
                label = "For You",
                badge = "4You",
                palette = palette,
                soundEnabled = vm.soundEnabled,
                active = activeTab == "foryou",
                onClick = {
                    activeTab = "foryou"
                    statusText = "TikTok | For You"
                    webViewRef?.loadUrl(TIKTOK_HOME_URL)
                }
            )
            Win98IconToolButton(
                icon = "tbFriends",
                label = "Following",
                palette = palette,
                soundEnabled = vm.soundEnabled,
                active = activeTab == "following",
                onClick = {
                    activeTab = "following"
                    statusText = "TikTok | Following"
                    webViewRef?.loadUrl(TIKTOK_FOLLOWING_URL)
                }
            )
            Win98IconToolButton(
                icon = "tbLive",
                label = "LIVE",
                palette = palette,
                soundEnabled = vm.soundEnabled,
                active = activeTab == "live",
                onClick = {
                    activeTab = "live"
                    statusText = "TikTok | LIVE"
                    webViewRef?.loadUrl(TIKTOK_LIVE_URL)
                }
            )
            Win98IconToolButton(
                icon = "tbProfile",
                label = "Friends",
                palette = palette,
                soundEnabled = vm.soundEnabled,
                active = activeTab == "friends",
                onClick = {
                    activeTab = "friends"
                    statusText = "TikTok | Friends"
                    webViewRef?.loadUrl(TIKTOK_FRIENDS_URL)
                }
            )
        }

        // Classic Win98 Status Bar
        BevelBox(
            theme = palette,
            raised = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = palette.textDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (loadProgress in 1..99) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(8.dp)
                            .border(1.dp, palette.borderDeep)
                            .background(palette.windowSurface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(loadProgress / 100f)
                                .background(palette.accentPink)
                        )
                    }
                }
            }
        }
    }
}
