package com.retrovibe.os.ui.windows

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.retrovibe.os.theme.textDark
import com.retrovibe.os.theme.accent

data class RetroEmail(
    val id: String,
    val from: String,
    val subject: String,
    val snippet: String,
    val date: String,
    val unread: Boolean = true
)

@Composable
fun EmailWindow(vm: OSViewModel, palette: RetroPalette) {
    val scope = rememberCoroutineScope()
    var emails by remember {
        mutableStateOf(
            listOf(
                RetroEmail(
                    "m-1",
                    "",
                    "Welcome to RetroVibe Express!",
                    "You can read demo mail here or paste a Gmail OAuth2 Access Token in Settings to sync your real inbox.",
                    "Today"
                )
            )
        )
    }
    var selectedEmail by remember { mutableStateOf<RetroEmail?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showCompose by remember { mutableStateOf(false) }
    var gmailToken by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("Demo Inbox Ready") }

    var composeTo by remember { mutableStateOf("") }
    var composeSubject by remember { mutableStateOf("") }
    var composeBody by remember { mutableStateOf("") }

    fun syncGmail() {
        val token = gmailToken.trim()
        if (token.isEmpty()) {
            statusMsg = "Paste a Gmail OAuth token first."
            return
        }
        statusMsg = "Syncing Gmail..."
        scope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) {
                    val conn = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=10").openConnection() as HttpURLConnection
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
                    val root = JSONObject(conn.inputStream.bufferedReader().readText())
                    val arr = root.optJSONArray("messages") ?: return@withContext emptyList<RetroEmail>()
                    val out = mutableListOf<RetroEmail>()
                    for (i in 0 until arr.length()) {
                        val id = arr.getJSONObject(i).getString("id")
                        val dConn = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date").openConnection() as HttpURLConnection
                        dConn.setRequestProperty("Authorization", "Bearer $token")
                        if (dConn.responseCode in 200..299) {
                            val detail = JSONObject(dConn.inputStream.bufferedReader().readText())
                            val headers = detail.optJSONObject("payload")?.optJSONArray("headers")
                            var from = "Unknown"
                            var subject = "(No Subject)"
                            var date = ""
                            if (headers != null) {
                                for (h in 0 until headers.length()) {
                                    val ho = headers.getJSONObject(h)
                                    when (ho.optString("name")) {
                                        "From" -> from = ho.optString("value")
                                        "Subject" -> subject = ho.optString("value")
                                        "Date" -> date = ho.optString("value").take(16)
                                    }
                                }
                            }
                            out.add(RetroEmail(id, from, subject, detail.optString("snippet"), date, unread = true))
                        }
                    }
                    out
                }
                if (fetched.isNotEmpty()) {
                    emails = fetched
                    statusMsg = "Synced ${fetched.size} messages from Gmail!"
                    vm.triggerMailPopup(fetched.first().from, fetched.first().subject)
                } else {
                    statusMsg = "Inbox empty."
                }
            } catch (e: Throwable) {
                statusMsg = "Gmail sync error: ${e.message}"
                RetroSoundEngine.play(RetroSound.ERROR, vm.soundEnabled)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RetroButton(label = "Compose", palette = palette, active = showCompose, onClick = {
                showCompose = !showCompose
                showSettings = false
                selectedEmail = null
            })
            RetroButton(label = "Settings", palette = palette, active = showSettings, onClick = {
                showSettings = !showSettings
                showCompose = false
                selectedEmail = null
            })
            RetroButton(label = "Sync", palette = palette, onClick = { syncGmail() })
            Spacer(Modifier.weight(1f))
            Text(statusMsg, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = palette.textDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        when {
            showSettings -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .win98Bevel(sunken = true, bg = Color.White)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Gmail OAuth2 Access Token (Optional):", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .win98Bevel(sunken = true, bg = Color(0xFFFDFDFD))
                            .padding(6.dp)
                    ) {
                        BasicTextField(
                            value = gmailToken,
                            onValueChange = { gmailToken = it },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RetroButton(
                            label = if (vm.mailPopupsEnabled) "[x] You've Got Mail! Popups" else "[ ] You've Got Mail! Popups",
                            palette = palette,
                            onClick = {
                                vm.mailPopupsEnabled = !vm.mailPopupsEnabled
                                vm.persist()
                            }
                        )
                        RetroButton(
                            label = "Test Popup",
                            palette = palette,
                            onClick = {
                                vm.triggerMailPopup("RetroVibe Mail", "You've Got Mail!", force = true)
                            }
                        )
                    }
                }
            }
            showCompose -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .win98Bevel(sunken = true, bg = Color.White)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("To:", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Box(modifier = Modifier.fillMaxWidth().win98Bevel(sunken = true, bg = Color.White).padding(4.dp)) {
                        BasicTextField(value = composeTo, onValueChange = { composeTo = it }, singleLine = true, textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp), modifier = Modifier.fillMaxWidth())
                    }
                    Text("Subject:", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Box(modifier = Modifier.fillMaxWidth().win98Bevel(sunken = true, bg = Color.White).padding(4.dp)) {
                        BasicTextField(value = composeSubject, onValueChange = { composeSubject = it }, singleLine = true, textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp), modifier = Modifier.fillMaxWidth())
                    }
                    Text("Message:", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().win98Bevel(sunken = true, bg = Color.White).padding(4.dp)) {
                        BasicTextField(value = composeBody, onValueChange = { composeBody = it }, textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp), modifier = Modifier.fillMaxSize())
                    }
                    RetroButton(label = "Send Draft", palette = palette, active = true, onClick = {
                        emails = listOf(
                            RetroEmail("sent-${System.currentTimeMillis()}", "Me -> ${composeTo.ifBlank { "Friend" }}", composeSubject.ifBlank { "(No Subject)" }, composeBody, "Just now", unread = false)
                        ) + emails
                        composeTo = ""; composeSubject = ""; composeBody = ""
                        showCompose = false
                        statusMsg = "Message saved to list"
                        RetroSoundEngine.play(RetroSound.SUCCESS, vm.soundEnabled)
                    })
                }
            }
            selectedEmail != null -> {
                val mail = selectedEmail!!
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .win98Bevel(sunken = true, bg = Color.White)
                        .padding(8.dp)
                ) {
                    RetroButton(label = "<- Inbox", palette = palette, onClick = { selectedEmail = null })
                    Spacer(Modifier.height(6.dp))
                    Text(mail.subject, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                    if (mail.from.isNotBlank()) {
                        Text("From: ${mail.from}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF555555))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(mail.snippet, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.Black)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .win98Bevel(sunken = true, bg = Color.White)
                        .padding(3.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(emails, key = { it.id }) { mail ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (mail.unread) Color(0xFFFFF5FA) else Color.White)
                                .clickable {
                                    emails = emails.map { if (it.id == mail.id) it.copy(unread = false) else it }
                                    selectedEmail = mail
                                }
                                .padding(6.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = mail.subject,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (mail.unread) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                    color = Color.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(mail.date, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF666666))
                            }
                            if (mail.from.isNotBlank()) {
                                Text(mail.from, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = palette.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
