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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.theme.textDark
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.PixelIcon

private const val WHATSAPP_DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private const val WHATSAPP_WEB_URL = "https://web.whatsapp.com/"

private fun Color.toCssHex(): String {
    val argb = this.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

@Composable
internal fun Win98MenuItem(
    text: String,
    palette: RetroPalette,
    soundEnabled: Boolean,
    onClick: () -> Unit
) {
    val annotated = remember(text) {
        buildAnnotatedString {
            if (text.isNotEmpty()) {
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(text.first())
                }
                append(text.drop(1))
            }
        }
    }
    Text(
        text = annotated,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        color = palette.textDark,
        modifier = Modifier
            .clickable {
                RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
                onClick()
            }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
internal fun Win98IconToolButton(
    icon: String,
    label: String,
    palette: RetroPalette,
    soundEnabled: Boolean,
    active: Boolean = false,
    badge: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (active) palette.accentSoft else Color.Transparent
    val topBorder = if (active) palette.borderDark else Color.Transparent
    val botBorder = if (active) palette.borderLight else Color.Transparent
    Box(
        modifier = modifier
            .background(bg)
            .then(
                if (active) {
                    Modifier.border(1.dp, palette.borderDeep)
                } else {
                    Modifier
                }
            )
            .clickable {
                RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
                onClick()
            }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                PixelIcon(icon, 20.dp)
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .offset(x = 6.dp, y = (-3).dp)
                            .background(palette.accentPink)
                            .border(1.dp, palette.borderDeep)
                            .padding(horizontal = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 8.sp
                        )
                    }
                }
            }
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = palette.textDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Suppress unused warnings
        if (topBorder == Color.Transparent && botBorder == Color.Transparent) Unit
    }
}

private fun buildWin98WhatsAppJs(
    palette: RetroPalette,
    skinEnabled: Boolean,
    phoneLayout: Boolean,
    showChatListPane: Boolean
): String {
    val bg = palette.windowBg.toCssHex()
    val surface = palette.windowSurface.toCssHex()
    val accent = palette.accentPink.toCssHex()
    val light = palette.borderLight.toCssHex()
    val dark = palette.borderDark.toCssHex()
    val deep = palette.borderDeep.toCssHex()
    val text = palette.textDark.toCssHex()
    val titleA = palette.titleBarActive.toCssHex()
    val titleB = palette.titleBarAccent.toCssHex()

    val skinCss = if (skinEnabled) """
        :root, body, .web, .dark, .light {
            --app-background: $bg !important;
            --app-background-deeper: $bg !important;
            --app-background-stripe: $titleA !important;
            --background-default: #FFFFFF !important;
            --background-default-active: $bg !important;
            --background-default-hover: $surface !important;
            --panel-background: $bg !important;
            --panel-background-colored: $titleA !important;
            --panel-background-colored-deeper: $titleB !important;
            --panel-background-deeper: $bg !important;
            --panel-background-hover: $surface !important;
            --panel-background-lighter: $surface !important;
            --panel-header-background: $bg !important;
            --conversation-panel-background: $bg !important;
            --conversation-panel-border: $dark !important;
            --intro-background: $bg !important;
            --intro-border: $accent !important;
            --compose-input-background: #FFFFFF !important;
            --compose-input-border: $deep !important;
            --compose-panel-background: $bg !important;
            --rich-text-panel-background: $bg !important;
            --search-container-background: $bg !important;
            --search-input-background: #FFFFFF !important;
            --search-input-container-background: $bg !important;
            --outgoing-background: $surface !important;
            --outgoing-background-deeper: $surface !important;
            --incoming-background: #FFFFFF !important;
            --incoming-background-deeper: #FFFFFF !important;
            --system-message-background: $bg !important;
            --system-message-text: $text !important;
            --primary: $text !important;
            --primary-strong: $text !important;
            --primary-stronger: $text !important;
            --primary-title: $text !important;
            --secondary: #444444 !important;
            --secondary-stronger: #222222 !important;
            --message-primary: $text !important;
            --bubble-meta: #555555 !important;
            --border-list: $dark !important;
            --border-panel: $dark !important;
            --border-strong: $deep !important;
            --border-stronger: $deep !important;
            --teal: $accent !important;
            --button-primary-background: $bg !important;
            --button-primary-background-hover: $surface !important;
            --button-primary: $text !important;
            --unread-marker-background: $accent !important;
            --unread-marker-text: #FFFFFF !important;
            --icon: $text !important;
            --icon-lighter: #555555 !important;
            --icon-strong: $text !important;
            --WDS-surface-default: #FFFFFF !important;
            --WDS-surface-subtle: $bg !important;
            --WDS-surface-elevated-default: $bg !important;
            --WDS-content-default: $text !important;
            --WDS-content-deemphasized: #555555 !important;
            --WDS-accent: $accent !important;
            --WDS-persistent-always-branded: $accent !important;
        }
        html, body, #app, button, input, textarea, [contenteditable="true"] {
            font-family: "Tahoma", "MS Sans Serif", "Courier New", monospace !important;
        }
        body {
            background: $bg !important;
            color: $text !important;
        }
        /* Classic square Win98 geometry everywhere */
        #app div, #app button, #app input, #app header, #app footer, #app section, #app img {
            border-radius: 0px !important;
        }
        /* Raised Win98 header bar */
        #app header {
            background: $bg !important;
            border-bottom: 2px solid $deep !important;
            box-shadow: inset 1px 1px 0 $light, inset -1px -1px 0 $dark !important;
        }
        /* Sunken Win98 input fields */
        #app [contenteditable="true"], #app input[type="text"], #app input[type="tel"] {
            background: #FFFFFF !important;
            color: #111111 !important;
            border: 1px solid $deep !important;
            box-shadow: inset 1px 1px 0 $dark, inset -1px -1px 0 $light !important;
        }
        /* Classic Win98 footer compose bar */
        #app footer {
            background: $bg !important;
            border-top: 2px solid $light !important;
            box-shadow: inset 0 1px 0 $light !important;
        }
        /* Classic Win98 3D beveled chat bubbles with crisp pixel drop shadow */
        #main .message-in > div, #main .message-out > div {
            border: 1px solid $deep !important;
            box-shadow: inset 1px 1px 0 $light, inset -1px -1px 0 $dark, 2px 2px 0 rgba(0,0,0,0.22) !important;
        }
        #main .message-in > div {
            background: #FFFFFF !important;
        }
        #main .message-out > div {
            background: $surface !important;
        }
        /* Hide modern doodle wallpaper so Win98 chat pane stays clean */
        [data-asset-chat-background-light], [data-asset-chat-background-dark] {
            opacity: 0.04 !important;
        }
        /* Chunky Win98 scrollbars */
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
    """.trimIndent() else ""

    val phoneCss = if (phoneLayout) {
        val paneRules = if (showChatListPane) """
            body.rv-logged-in #main {
                display: none !important;
            }
            body.rv-logged-in #side {
                width: 100vw !important;
                max-width: 100vw !important;
                min-width: 100vw !important;
                flex: 1 1 100% !important;
            }
        """.trimIndent() else """
            body.rv-logged-in #side {
                display: none !important;
            }
            body.rv-logged-in #main {
                width: 100vw !important;
                max-width: 100vw !important;
                min-width: 100vw !important;
                flex: 1 1 100% !important;
            }
        """.trimIndent()

        """
        html, body, #app, #app > div, #app .two, .app-wrapper-web {
            min-width: 0px !important;
            max-width: 100vw !important;
            width: 100vw !important;
            overflow-x: hidden !important;
        }
        .landing-wrapper, .landing-window {
            min-width: 0px !important;
            width: 100% !important;
            max-width: 100vw !important;
            margin: 0 !important;
            padding: 10px !important;
            box-sizing: border-box !important;
        }
        .landing-main {
            padding: 14px !important;
            flex-direction: column !important;
        }
        $paneRules
        """.trimIndent()
    } else ""

    val combinedCss = (skinCss + "\n" + phoneCss)
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("$", "\\$")

    return """
        (function() {
            try {
                var id = 'retrovibe-win98-wa-css';
                var el = document.getElementById(id);
                if (!el) {
                    el = document.createElement('style');
                    el.id = id;
                    (document.head || document.documentElement).appendChild(el);
                }
                el.textContent = `$combinedCss`;

                function syncLoggedInClass() {
                    var hasSide = !!document.getElementById('side');
                    if (hasSide) {
                        document.body.classList.add('rv-logged-in');
                    } else {
                        document.body.classList.remove('rv-logged-in');
                    }
                }
                syncLoggedInClass();

                if (!window.__rvWaObserverInstalled) {
                    window.__rvWaObserverInstalled = true;
                    var obs = new MutationObserver(function() {
                        syncLoggedInClass();
                    });
                    obs.observe(document.documentElement, { childList: true, subtree: true });

                    document.addEventListener('click', function(e) {
                        try {
                            var side = document.getElementById('pane-side');
                            if (side && side.contains(e.target)) {
                                if (window.RetroWA && window.RetroWA.onChatSelected) {
                                    window.RetroWA.onChatSelected();
                                }
                            }
                        } catch (_) {}
                    }, true);
                }
            } catch (_) {}
        })();
    """.trimIndent()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WhatsAppWindow(vm: OSViewModel, palette: RetroPalette) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var win98Skin by remember { mutableStateOf(true) }
    var phoneLayout by remember { mutableStateOf(true) }
    var showChatListPane by remember { mutableStateOf(true) }
    var showLinkHelp by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("WhatsApp | Connecting...") }
    var loadProgress by remember { mutableStateOf(0) }
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        pendingFileCallback?.onReceiveValue(uris.toTypedArray())
        pendingFileCallback = null
    }

    fun applyThemeAndLayout(wv: WebView?) {
        if (wv == null) return
        val js = buildWin98WhatsAppJs(
            palette = palette,
            skinEnabled = win98Skin,
            phoneLayout = phoneLayout,
            showChatListPane = showChatListPane
        )
        wv.evaluateJavascript(js, null)
    }

    LaunchedEffect(win98Skin, phoneLayout, showChatListPane, palette, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        wv.settings.useWideViewPort = !phoneLayout
        wv.settings.loadWithOverviewMode = !phoneLayout
        wv.settings.setSupportZoom(!phoneLayout)
        wv.settings.builtInZoomControls = !phoneLayout
        wv.settings.displayZoomControls = false
        applyThemeAndLayout(wv)
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
                statusText = "WhatsApp | Reloading..."
                webViewRef?.reload()
            }
            Win98MenuItem("Edit", palette, vm.soundEnabled) {
                webViewRef?.evaluateJavascript(
                    "document.querySelector('[contenteditable=\"true\"]')?.focus();",
                    null
                )
            }
            Win98MenuItem("View", palette, vm.soundEnabled) {
                phoneLayout = !phoneLayout
                statusText = if (phoneLayout) "WhatsApp | Phone View" else "WhatsApp | Desktop View"
            }
            Win98MenuItem("Tools", palette, vm.soundEnabled) {
                win98Skin = !win98Skin
                statusText = if (win98Skin) "WhatsApp | Win98 Skin ON" else "WhatsApp | Win98 Skin OFF"
            }
            Win98MenuItem("Help", palette, vm.soundEnabled) {
                showLinkHelp = !showLinkHelp
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderDark))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.borderLight))

        // Classic Win98 Icon Toolbar (New Chat | Chats | Search | Skin)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.windowBg)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Win98IconToolButton(
                icon = "tbChat",
                label = if (phoneLayout && !showChatListPane) "< Chats" else "Chats",
                palette = palette,
                soundEnabled = vm.soundEnabled,
                active = phoneLayout && showChatListPane,
                onClick = {
                    if (phoneLayout) {
                        showChatListPane = !showChatListPane
                    } else {
                        phoneLayout = true
                        showChatListPane = true
                    }
                }
            )
            Win98IconToolButton(
                icon = "tbNewChat",
                label = "New Chat",
                palette = palette,
                soundEnabled = vm.soundEnabled,
                onClick = {
                    showChatListPane = true
                    webViewRef?.evaluateJavascript(
                        "(document.querySelector('[data-icon=\"new-chat-outline\"]') || document.querySelector('[aria-label*=\"New chat\"]'))?.click();",
                        null
                    )
                }
            )
            Win98IconToolButton(
                icon = "tbSearch",
                label = "Search",
                palette = palette,
                soundEnabled = vm.soundEnabled,
                onClick = {
                    showChatListPane = true
                    webViewRef?.evaluateJavascript(
                        "(document.querySelector('#side [contenteditable=\"true\"]') || document.querySelector('input[type=\"text\"]'))?.focus();",
                        null
                    )
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

        // Optional Login Help Tip (toggled via Help menu)
        if (showLinkHelp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 3.dp)
                    .background(palette.windowSurface)
                    .border(1.dp, palette.borderDeep)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Tip: Tap \"Log in with phone number\" to link WhatsApp on this same phone (8-digit code). Login stays saved!",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    color = palette.textDark,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "[x]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textDark,
                    modifier = Modifier.clickable { showLinkHelp = false }
                )
            }
        }

        // Main WhatsApp Web Viewport inside sunken Win98 bevel
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
                                userAgentString = WHATSAPP_DESKTOP_UA
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                useWideViewPort = !phoneLayout
                                loadWithOverviewMode = !phoneLayout
                                setSupportZoom(!phoneLayout)
                                builtInZoomControls = !phoneLayout
                                displayZoomControls = false
                            }

                            addJavascriptInterface(
                                object {
                                    @android.webkit.JavascriptInterface
                                    fun onChatSelected() {
                                        post {
                                            if (phoneLayout) {
                                                showChatListPane = false
                                            }
                                        }
                                    }
                                },
                                "RetroWA"
                            )

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    val host = request.url?.host?.lowercase() ?: ""
                                    if (host.endsWith("whatsapp.com") || host.endsWith("whatsapp.net")) {
                                        return false
                                    }
                                    if (url.startsWith("http://") || url.startsWith("https://")) {
                                        vm.openBrowserUrl(url)
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    statusText = "WhatsApp | Online"
                                    applyThemeAndLayout(view)
                                    view?.postDelayed({ applyThemeAndLayout(view) }, 1200L)
                                    view?.postDelayed({ applyThemeAndLayout(view) }, 3000L)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                    if (newProgress in 1..99) {
                                        statusText = "WhatsApp | Loading $newProgress%..."
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

                            loadUrl(WHATSAPP_WEB_URL)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Classic Win98 Status Bar
        BevelBox(
            theme = palette,
            raised = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 3.dp)
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
                } else {
                    Text(
                        text = if (phoneLayout) "Phone" else "Desktop",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        color = palette.textSecondary,
                        modifier = Modifier.clickable { phoneLayout = !phoneLayout }
                    )
                }
            }
        }
    }
}
