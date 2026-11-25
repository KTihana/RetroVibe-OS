package com.retrovibe.os.ui.windows

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.retrovibe.os.store.OSViewModel
import com.retrovibe.os.theme.RetroPalette
import com.retrovibe.os.ui.BevelBox
import com.retrovibe.os.ui.RetroButton

private data class PhoneBookEntry(
    val name: String,
    val phone: String
)

private fun loadDeviceContacts(context: Context): List<PhoneBookEntry> {
    val result = mutableListOf<PhoneBookEntry>()
    val seen = mutableSetOf<String>()
    try {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                val num = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                val key = "${name.lowercase()}|${num.filter { it.isDigit() || it == '+' }}"
                if (name.isNotEmpty() && num.isNotEmpty() && seen.add(key)) {
                    result.add(PhoneBookEntry(name, num))
                }
            }
        }
    } catch (_: Throwable) {}
    return result
}

@Composable
fun PhoneWindow(vm: OSViewModel, palette: RetroPalette) {
    val context = LocalContext.current
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Dialer, 1 = Address Book
    var dialNumber by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newNote by remember { mutableStateOf("") }
    var showAddForm by remember { mutableStateOf(false) }

    // Phone Contact Import state
    var showPhonePicker by remember { mutableStateOf(false) }
    var deviceContacts by remember { mutableStateOf<List<PhoneBookEntry>>(emptyList()) }
    var pickerSearch by remember { mutableStateOf("") }

    val contactsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            deviceContacts = loadDeviceContacts(context)
            showPhonePicker = true
            showAddForm = false
        }
    }

    fun openPhoneImport() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            deviceContacts = loadDeviceContacts(context)
            showPhonePicker = !showPhonePicker
            showAddForm = false
        } else {
            contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    fun placeCall(num: String) {
        val clean = num.trim()
        if (clean.isEmpty()) return
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
        } catch (_: Throwable) {}
    }

    fun sendSms(num: String) {
        val clean = num.trim()
        if (clean.isEmpty()) return
        try {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$clean")))
        } catch (_: Throwable) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RetroButton(
                label = "Dialer",
                theme = palette,
                soundEnabled = vm.soundEnabled,
                active = activeTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { activeTab = 0 }
            )
            RetroButton(
                label = "Address Book (${vm.contacts.size})",
                theme = palette,
                soundEnabled = vm.soundEnabled,
                active = activeTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { activeTab = 1 }
            )
        }

        if (activeTab == 0) {
            // Retro LCD Number Display
            BevelBox(theme = palette, raised = false, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.windowSurface)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dialNumber.ifEmpty { "Enter number..." },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dialNumber.isEmpty()) palette.textSecondary else palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (dialNumber.isNotEmpty()) {
                        RetroButton(
                            label = "⌫",
                            theme = palette,
                            soundEnabled = vm.soundEnabled,
                            onClick = { dialNumber = dialNumber.dropLast(1) }
                        )
                    }
                }
            }

            // Keypad Grid
            val rows = listOf(
                listOf("1" to "", "2" to "ABC", "3" to "DEF"),
                listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
                listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
                listOf("*" to "", "0" to "+", "#" to "")
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        row.forEach { (digit, sub) ->
                            RetroButton(
                                theme = palette,
                                soundEnabled = vm.soundEnabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onClick = { if (dialNumber.length < 20) dialNumber += digit }
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        digit,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = palette.textPrimary
                                    )
                                    if (sub.isNotEmpty()) {
                                        Text(
                                            sub,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 8.sp,
                                            color = palette.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Dialer Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RetroButton(
                    label = "+",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    onClick = { if (dialNumber.length < 20) dialNumber += "+" }
                )
                RetroButton(
                    label = "Call",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    active = true,
                    enabled = dialNumber.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    onClick = { placeCall(dialNumber) }
                )
                RetroButton(
                    label = "SMS",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    enabled = dialNumber.isNotBlank(),
                    onClick = { sendSms(dialNumber) }
                )
                RetroButton(
                    label = "Save",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    enabled = dialNumber.isNotBlank(),
                    onClick = {
                        newPhone = dialNumber
                        showAddForm = true
                        showPhonePicker = false
                        activeTab = 1
                    }
                )
            }
        } else if (showPhonePicker) {
            // Import from Phone Contacts View
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RetroButton("<- Back", palette, onClick = { showPhonePicker = false })
                BasicTextField(
                    value = pickerSearch,
                    onValueChange = { pickerSearch = it },
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
                            if (pickerSearch.isEmpty()) {
                                Text("Filter phone contacts...", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = palette.textSecondary)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            val existingNumbers = remember(vm.contacts) {
                vm.contacts.map { it.phone.filter { c -> c.isDigit() || c == '+' } }.toSet()
            }
            val filteredPhone = remember(deviceContacts, pickerSearch) {
                val q = pickerSearch.trim()
                if (q.isEmpty()) deviceContacts
                else deviceContacts.filter {
                    it.name.contains(q, ignoreCase = true) || it.phone.contains(q, ignoreCase = true)
                }
            }

            BevelBox(theme = palette, raised = false, modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (filteredPhone.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No phone contacts found.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = palette.textSecondary
                        )
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(filteredPhone, key = { "${it.name}|${it.phone}" }) { entry ->
                            val cleanNum = entry.phone.filter { c -> c.isDigit() || c == '+' }
                            val alreadyAdded = cleanNum in existingNumbers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        entry.name,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        entry.phone,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = palette.textSecondary,
                                        maxLines = 1
                                    )
                                }
                                RetroButton(
                                    label = if (alreadyAdded) "Added" else "+ Add",
                                    theme = palette,
                                    soundEnabled = vm.soundEnabled,
                                    active = !alreadyAdded,
                                    enabled = !alreadyAdded,
                                    onClick = { vm.addContact(entry.name, entry.phone) }
                                )
                            }
                            HorizontalDivider(color = palette.borderDark, thickness = 1.dp)
                        }
                    }
                }
            }
        } else {
            // Address Book Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
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
                            if (searchQuery.isEmpty()) {
                                Text("Search...", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = palette.textSecondary)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                RetroButton(
                    label = "Import",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    onClick = { openPhoneImport() }
                )
                RetroButton(
                    label = if (showAddForm) "Close" else "+ New",
                    theme = palette,
                    soundEnabled = vm.soundEnabled,
                    active = showAddForm,
                    onClick = { showAddForm = !showAddForm }
                )
            }

            // Add Contact Form
            if (showAddForm) {
                BevelBox(theme = palette, raised = true, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BasicTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = palette.textPrimary),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth().background(palette.windowSurface).border(1.dp, palette.borderDark).padding(5.dp)) {
                                    if (newName.isEmpty()) Text("Name...", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = palette.textSecondary)
                                    inner()
                                }
                            }
                        )
                        BasicTextField(
                            value = newPhone,
                            onValueChange = { newPhone = it },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = palette.textPrimary),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth().background(palette.windowSurface).border(1.dp, palette.borderDark).padding(5.dp)) {
                                    if (newPhone.isEmpty()) Text("Phone number...", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = palette.textSecondary)
                                    inner()
                                }
                            }
                        )
                        BasicTextField(
                            value = newNote,
                            onValueChange = { newNote = it },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = palette.textPrimary),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth().background(palette.windowSurface).border(1.dp, palette.borderDark).padding(5.dp)) {
                                    if (newNote.isEmpty()) Text("Note (optional)...", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = palette.textSecondary)
                                    inner()
                                }
                            }
                        )
                        RetroButton(
                            label = "Save Contact",
                            theme = palette,
                            soundEnabled = vm.soundEnabled,
                            active = true,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (newName.isNotBlank() && newPhone.isNotBlank()) {
                                    vm.addContact(newName, newPhone, newNote)
                                    newName = ""
                                    newPhone = ""
                                    newNote = ""
                                    showAddForm = false
                                }
                            }
                        )
                    }
                }
            }

            // Contacts List
            val filtered = remember(vm.contacts, searchQuery) {
                val q = searchQuery.trim()
                if (q.isEmpty()) vm.contacts
                else vm.contacts.filter {
                    it.name.contains(q, ignoreCase = true) ||
                        it.phone.contains(q, ignoreCase = true) ||
                        it.note.contains(q, ignoreCase = true)
                }
            }

            BevelBox(theme = palette, raised = false, modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (vm.contacts.isEmpty()) "Address Book is empty.\nTap Import or + New above!" else "No matches.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(filtered, key = { it.id }) { contact ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        contact.name,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        contact.phone + if (contact.note.isNotBlank()) " · ${contact.note}" else "",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = palette.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                RetroButton("Call", palette, active = true, onClick = { placeCall(contact.phone) })
                                RetroButton("SMS", palette, onClick = { sendSms(contact.phone) })
                                RetroButton("×", palette, onClick = { vm.deleteContact(contact.id) })
                            }
                            HorizontalDivider(color = palette.borderDark, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}
