package com.retrovibe.os.ui.windows

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retrovibe.os.model.BookItem
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.PixelIcon
import com.retrovibe.os.ui.RetroButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

private fun queryDisplayName(context: Context, uri: Uri): String {
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        }
    } catch (_: Throwable) {}
    return uri.lastPathSegment?.substringAfterLast('/') ?: "Book.txt"
}

private fun stripHtmlToText(html: String): String {
    return html
        .replace(Regex("(?is)<(script|style|head).*?</\\1>"), "")
        .replace(Regex("(?i)<(br|p|div|h[1-6]|li|tr)[^>]*>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private suspend fun loadBookPages(context: Context, uriStr: String, charsPerPage: Int = 1100): List<String> {
    return withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriStr)
            val name = queryDisplayName(context, uri).lowercase()
            val fullText = if (name.endsWith(".epub")) {
                val sb = StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { raw ->
                    ZipInputStream(raw).use { zis ->
                        val entries = mutableListOf<Pair<String, String>>()
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val en = entry.name.lowercase()
                            if (!entry.isDirectory && (en.endsWith(".xhtml") || en.endsWith(".html") || en.endsWith(".htm"))) {
                                val content = zis.bufferedReader().readText()
                                val cleaned = stripHtmlToText(content)
                                if (cleaned.isNotBlank()) entries.add(entry.name to cleaned)
                            }
                            entry = zis.nextEntry
                        }
                        entries.sortedBy { it.first }.forEach { (_, txt) ->
                            sb.append(txt).append("\n\n")
                        }
                    }
                }
                sb.toString().trim()
            } else {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (fullText.isBlank()) {
                listOf("(Empty or unreadable file. Try a .txt or DRM-free .epub file!)")
            } else {
                // Split cleanly on paragraph/word boundaries into cozy pages
                val pages = mutableListOf<String>()
                var i = 0
                while (i < fullText.length) {
                    var end = (i + charsPerPage).coerceAtMost(fullText.length)
                    if (end < fullText.length) {
                        val nl = fullText.lastIndexOf('\n', end)
                        val sp = fullText.lastIndexOf(' ', end)
                        if (nl > i + charsPerPage / 2) end = nl + 1
                        else if (sp > i + charsPerPage / 2) end = sp + 1
                    }
                    pages.add(fullText.substring(i, end).trim())
                    i = end
                }
                pages.ifEmpty { listOf(fullText) }
            }
        } catch (t: Throwable) {
            listOf("Could not open book: ${t.message}")
        }
    }
}

@Composable
fun BookReaderWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    var activeBook by remember { mutableStateOf<BookItem?>(null) }
    var pages by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingBook by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(0) }
    var fontSizeSp by remember { mutableIntStateOf(13) }
    var paperMode by remember { mutableIntStateOf(1) } // 0 = White, 1 = Warm Sepia, 2 = Cozy Night
    val scrollState = rememberScrollState()

    val bookPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Throwable) {}
            val title = queryDisplayName(context, uri)
            val saved = vm.addBook(title, uri.toString())
            activeBook = saved
            currentPage = saved.currentPage
        }
    }

    LaunchedEffect(activeBook?.id) {
        val book = activeBook
        if (book != null) {
            loadingBook = true
            pages = loadBookPages(context, book.uri)
            currentPage = book.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            loadingBook = false
        } else {
            pages = emptyList()
        }
    }

    LaunchedEffect(currentPage, activeBook?.id) {
        scrollState.scrollTo(0)
        activeBook?.let { b ->
            vm.updateBookPage(b.id, currentPage)
        }
    }

    val (paperBg, paperFg) = when (paperMode) {
        0 -> Color(0xFFFFFFFF) to Color(0xFF141414)
        1 -> Color(0xFFF6EEDB) to Color(0xFF2B2118)
        else -> Color(0xFF181A22) to Color(0xFFDCD8CC)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (activeBook == null) {
            // Library View
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RetroButton(
                    label = "+ Open Book (.txt / .epub)",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    active = true,
                    onClick = { bookPicker.launch(arrayOf("text/plain", "application/epub+zip", "*/*")) }
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${vm.books.size} book(s)",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = palette.textSecondary
                )
            }

            BevelBox(theme = palette, raised = false, modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (vm.books.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(palette.windowSurface)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        PixelIcon("book", 44.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "RetroVibe E-Book Reader",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Quiet offline reading for .txt & .epub books.\nTap + Open Book above to add one!",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().background(palette.windowSurface),
                        contentPadding = PaddingValues(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(vm.books, key = { it.id }) { book ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeBook = book
                                        currentPage = book.currentPage
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PixelIcon("book", 22.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        book.title,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Page ${book.currentPage + 1}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = palette.textSecondary
                                    )
                                }
                                RetroButton("Read", palette, active = true, onClick = {
                                    activeBook = book
                                    currentPage = book.currentPage
                                })
                                RetroButton("×", palette, onClick = { vm.deleteBook(book.id) })
                            }
                            HorizontalDivider(color = palette.borderDark, thickness = 1.dp)
                        }
                    }
                }
            }
        } else {
            // Reading View Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RetroButton("<- Books", palette, onClick = { activeBook = null })
                RetroButton("A-", palette, onClick = { fontSizeSp = (fontSizeSp - 1).coerceAtLeast(10) })
                RetroButton("A+", palette, onClick = { fontSizeSp = (fontSizeSp + 1).coerceAtMost(22) })
                RetroButton(
                    label = when (paperMode) { 0 -> "Paper"; 1 -> "Sepia"; else -> "Night" },
                    palette = palette,
                    onClick = { paperMode = (paperMode + 1) % 3 }
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${currentPage + 1}/${pages.size.coerceAtLeast(1)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
            }

            // Cozy Page Surface
            BevelBox(theme = palette, raised = false, modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(paperBg)
                        .verticalScroll(scrollState)
                        .padding(14.dp)
                ) {
                    if (loadingBook) {
                        Text("Opening book...", fontFamily = FontFamily.Serif, fontSize = 13.sp, color = paperFg)
                    } else {
                        Text(
                            text = pages.getOrNull(currentPage) ?: "",
                            fontFamily = FontFamily.Serif,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.55f).sp,
                            color = paperFg
                        )
                    }
                }
            }

            // Page Turn Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RetroButton(
                    label = "< Prev Page",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    enabled = currentPage > 0,
                    modifier = Modifier.weight(1f),
                    onClick = { if (currentPage > 0) currentPage-- }
                )
                RetroButton(
                    label = "Next Page >",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    active = true,
                    enabled = currentPage < pages.lastIndex,
                    modifier = Modifier.weight(1f),
                    onClick = { if (currentPage < pages.lastIndex) currentPage++ }
                )
            }
        }
    }
}
