package com.retrovibe.os.store

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.retrovibe.os.audio.RetroSound
import com.retrovibe.os.audio.RetroSoundEngine
import com.retrovibe.os.model.*
import java.time.LocalDate

fun todayIso(): String = LocalDate.now().toString()

class OSViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("retrovibe_os_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "state_v1"

    var themeId by mutableStateOf(ThemeId.DEAR_PASTEL)
    var customWallpaperUri by mutableStateOf<String?>(null)
    var soundEnabled by mutableStateOf(true)

    val windows = mutableStateMapOf<String, WindowState>()
    var activeWindowId by mutableStateOf<String?>(null)
    private var zCounter = 10

    var notes by mutableStateOf<List<NoteItem>>(emptyList())
    var folders by mutableStateOf<List<FolderItem>>(emptyList())
    var calendarEntries by mutableStateOf<Map<String, CalendarDayEntry>>(emptyMap())
    var reminders by mutableStateOf<List<ReminderItem>>(emptyList())
    var todos by mutableStateOf<List<TodoItem>>(emptyList())
    var tracks by mutableStateOf<List<MusicTrack>>(emptyList())
    var voiceMemos by mutableStateOf<List<VoiceMemo>>(emptyList())
    var artworks by mutableStateOf<List<PaintArtwork>>(emptyList())
    var editingArtworkId by mutableStateOf<String?>(null)
    var permittedPhotos by mutableStateOf<List<PermittedPhoto>>(emptyList())
    var trash by mutableStateOf<List<TrashItem>>(emptyList())

    var iconPositions by mutableStateOf<Map<String, IconPos>>(emptyMap())
    var hiddenDesktopIcons by mutableStateOf(
        listOf("icon-calc", "icon-recorder", "icon-settings", "icon-status", "icon-taskmgr", "icon-sweeper")
    )
    var clipboard by mutableStateOf<ClipboardEntry?>(null)

    var deskCatEnabled by mutableStateOf(false)
    var deskCatVariant by mutableStateOf("mindi") // "mindi" (ginger) | "spooks" (fluffy black)
    var deskCatX by mutableStateOf(220f)
    var browserFavorites by mutableStateOf<List<BrowserFavorite>>(emptyList())
    var mailPopupsEnabled by mutableStateOf(false)
    var mailPopupAlert by mutableStateOf<MailPopupAlert?>(null)

    var screensaverId by mutableStateOf(ScreensaverId.TOASTERS)
    var screensaverWaitSec by mutableStateOf(0)
    var screensaverActive by mutableStateOf(false)
    var crtScanlines by mutableStateOf(true)
    var loginPromptEnabled by mutableStateOf(false)
    var username by mutableStateOf("RetroUser")
    var githubRepo by mutableStateOf("KTihana/RetroVibe-OS")
    var pendingBrowserUrl by mutableStateOf<String?>(null)

    var contacts by mutableStateOf<List<ContactItem>>(emptyList())
    var alarms by mutableStateOf<List<AlarmItem>>(emptyList())
    var books by mutableStateOf<List<BookItem>>(emptyList())
    var weatherCity by mutableStateOf("Osijek")
    var weatherLat by mutableStateOf(45.555)
    var weatherLon by mutableStateOf(18.6955)
    var weatherTemp by mutableStateOf<Int?>(null)
    var weatherCode by mutableStateOf<Int?>(null)
    var torchOn by mutableStateOf(false)

    // Persistent in-memory Timer state so countdown keeps running even when Clock window is minimized/closed
    var timerTotalSec by mutableStateOf(25 * 60)
    var timerPausedLeftSec by mutableStateOf(25 * 60)
    var timerEndEpochMs by mutableStateOf<Long?>(null)
    var timerDone by mutableStateOf(false)

    fun openBrowserUrl(url: String) {
        pendingBrowserUrl = url
        openWindow("browser")
    }

    init {
        initWindows()
        loadState()
    }

    private fun initWindows() {
        val defaults = listOf(
            WindowState("explorer", "C:\\RETROVIBE\\", x = 14f, y = 40f, width = 336f, height = 420f),
            WindowState("gallery", "My Pictures (Permitted)", x = 14f, y = 40f, width = 336f, height = 430f),
            WindowState("camera", "RetroVibe Camera", x = 14f, y = 32f, width = 340f, height = 480f),
            WindowState("music", "Playlist.wpl", x = 18f, y = 80f, width = 324f, height = 340f),
            WindowState("calendar", "Calendar", x = 16f, y = 50f, width = 332f, height = 382f),
            WindowState("reminders", "Reminders.exe", x = 18f, y = 60f, width = 326f, height = 390f),
            WindowState("habits", "To do list", x = 22f, y = 90f, width = 300f, height = 320f),
            WindowState("paint", "untitled - Paint", isMaximized = true, x = 8f, y = 24f, width = 350f, height = 500f),
            WindowState("recorder", "Sound Recorder.exe", x = 20f, y = 90f, width = 320f, height = 320f),
            WindowState("recycle", "Recycle Bin", x = 22f, y = 80f, width = 318f, height = 350f),
            WindowState("settings", "Display Properties", x = 14f, y = 32f, width = 340f, height = 480f),
            WindowState("status", "System & Updates", x = 18f, y = 56f, width = 326f, height = 370f),
            WindowState("sweeper", "HeartSweeper", x = 14f, y = 36f, width = 344f, height = 470f),
            WindowState("browser", "Internet Voyager - RetroVibe OS", isMaximized = true, x = 10f, y = 20f, width = 356f, height = 500f),
            WindowState("whatsapp", "WhatsApp 98", isMaximized = true, x = 10f, y = 20f, width = 356f, height = 500f),
            WindowState("qrscanner", "QR Scanner", x = 16f, y = 36f, width = 336f, height = 450f),
            WindowState("mail", "RetroVibe Express - Mail", x = 14f, y = 40f, width = 344f, height = 440f),
            WindowState("calc", "Calculator", x = 36f, y = 70f, width = 292f, height = 314f),
            WindowState("terminal", "MS-DOS Prompt", x = 16f, y = 64f, width = 336f, height = 320f),
            WindowState("run", "Run", x = 22f, y = 140f, width = 314f, height = 184f),
            WindowState("taskmgr", "Task Manager", x = 18f, y = 70f, width = 328f, height = 380f),
            WindowState("phone", "Phone & Address Book", x = 16f, y = 40f, width = 332f, height = 460f),
            WindowState("clock", "Clock, Alarms & Timer", x = 16f, y = 48f, width = 332f, height = 430f),
            WindowState("control", "Control Panel", x = 18f, y = 52f, width = 328f, height = 420f),
            WindowState("weather", "Weather Forecast", x = 22f, y = 60f, width = 320f, height = 370f),
            WindowState("reader", "RetroVibe Reader", isMaximized = true, x = 10f, y = 24f, width = 350f, height = 500f)
        )
        defaults.forEach { windows[it.id] = it }
    }

    private fun seedDefaults() {
        folders = listOf(
            FolderItem("root-notes", "00. MY NOTES"),
            FolderItem("root-art", "01. MS PAINT"),
            FolderItem("root-voice", "02. VOICE MEMOS")
        )
        notes = listOf(
            NoteItem(
                id = "note-welcome",
                title = "Note.txt",
                content = "welcome to RetroVibe OS!\n\ntap \"New Note\" anytime.\npinned desktop notes stay right here when you return!",
                folderId = "root-notes",
                pinnedToDesktop = false
            )
        )
        todos = emptyList()
        reminders = emptyList()
        tracks = emptyList()
        calendarEntries = mapOf(
            todayIso() to CalendarDayEntry(todayIso(), "heart", "Started using RetroVibe OS!")
        )
    }

    private fun loadState() {
        val raw = prefs.getString(key, null)
        if (raw == null) {
            seedDefaults()
            persist()
            return
        }
        try {
            val s = gson.fromJson(raw, PersistedOSState::class.java)
            themeId = s.themeId ?: ThemeId.DEAR_PASTEL
            customWallpaperUri = s.customWallpaperUri
            soundEnabled = s.soundEnabled
            notes = s.notes.orEmpty()
            folders = s.folders.orEmpty().ifEmpty {
                listOf(
                    FolderItem("root-notes", "00. MY NOTES"),
                    FolderItem("root-art", "01. MS PAINT"),
                    FolderItem("root-voice", "02. VOICE MEMOS")
                )
            }
            calendarEntries = s.calendarEntries.orEmpty()
            val today = todayIso()
            reminders = s.reminders.orEmpty().map { r ->
                val due = r.dueDate ?: ""
                val isDue = !r.completed && due.isNotBlank() && due <= today && r.dismissedDate != today
                r.copy(dueDate = due, showPopup = isDue)
            }
            todos = s.todos.orEmpty()
            tracks = s.tracks.orEmpty().filterNot { it.isDemo }
            voiceMemos = s.voiceMemos.orEmpty()
            artworks = s.artworks.orEmpty()
            permittedPhotos = s.permittedPhotos.orEmpty()
            trash = s.trash.orEmpty()
            iconPositions = s.iconPositions.orEmpty()
            hiddenDesktopIcons = s.hiddenDesktopIcons.orEmpty()
            deskCatEnabled = s.deskCatEnabled
            deskCatVariant = (s.deskCatVariant ?: "").ifBlank { "mindi" }
            deskCatX = s.deskCatX
            browserFavorites = s.browserFavorites.orEmpty()
            mailPopupsEnabled = s.mailPopupsEnabled
            screensaverId = s.screensaverId ?: ScreensaverId.TOASTERS
            screensaverWaitSec = s.screensaverWaitSec
            crtScanlines = s.crtScanlines
            loginPromptEnabled = s.loginPromptEnabled
            username = (s.username ?: "").ifBlank { "RetroUser" }
            githubRepo = (s.githubRepo ?: "").ifBlank { "KTihana/RetroVibe-OS" }
            contacts = s.contacts.orEmpty()
            alarms = s.alarms.orEmpty()
            books = s.books.orEmpty()
            weatherCity = (s.weatherCity ?: "").ifBlank { "Osijek" }
            weatherLat = if (s.weatherLat == 0.0 && s.weatherLon == 0.0) 45.555 else s.weatherLat
            weatherLon = if (s.weatherLat == 0.0 && s.weatherLon == 0.0) 18.6955 else s.weatherLon
        } catch (_: Throwable) {
            seedDefaults()
        }
    }

    fun persist() {
        val snap = PersistedOSState(
            themeId = themeId,
            customWallpaperUri = customWallpaperUri,
            soundEnabled = soundEnabled,
            notes = notes,
            folders = folders,
            calendarEntries = calendarEntries,
            reminders = reminders,
            todos = todos,
            tracks = tracks,
            voiceMemos = voiceMemos,
            artworks = artworks,
            permittedPhotos = permittedPhotos,
            trash = trash,
            iconPositions = iconPositions,
            hiddenDesktopIcons = hiddenDesktopIcons,
            deskCatEnabled = deskCatEnabled,
            deskCatVariant = deskCatVariant,
            deskCatX = deskCatX,
            browserFavorites = browserFavorites,
            mailPopupsEnabled = mailPopupsEnabled,
            screensaverId = screensaverId,
            screensaverWaitSec = screensaverWaitSec,
            crtScanlines = crtScanlines,
            loginPromptEnabled = loginPromptEnabled,
            username = username,
            githubRepo = githubRepo,
            contacts = contacts,
            alarms = alarms,
            books = books,
            weatherCity = weatherCity,
            weatherLat = weatherLat,
            weatherLon = weatherLon
        )
        prefs.edit().putString(key, gson.toJson(snap)).apply()
    }

    fun storageSizeKb(): Double {
        val raw = prefs.getString(key, "") ?: ""
        return (raw.length / 1024.0)
    }

    fun openWindow(id: String, titleOverride: String? = null) {
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        zCounter++
        val base = windows[id] ?: WindowState(id, titleOverride ?: "Window")
        windows[id] = base.copy(
            title = titleOverride ?: base.title,
            isOpen = true,
            isMinimized = false,
            zIndex = zCounter
        )
        activeWindowId = id
    }

    fun closeWindow(id: String) {
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        windows[id]?.let { windows[id] = it.copy(isOpen = false, isMinimized = false) }
        if (activeWindowId == id) activeWindowId = null
    }

    fun minimizeWindow(id: String) {
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        windows[id]?.let { windows[id] = it.copy(isMinimized = true) }
        if (activeWindowId == id) activeWindowId = null
    }

    fun toggleMaximizeWindow(id: String) {
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        windows[id]?.let { windows[id] = it.copy(isMaximized = !it.isMaximized) }
    }

    fun focusWindow(id: String) {
        zCounter++
        windows[id]?.let { windows[id] = it.copy(isMinimized = false, zIndex = zCounter) }
        activeWindowId = id
    }

    fun moveWindow(id: String, x: Float, y: Float) {
        windows[id]?.let { windows[id] = it.copy(x = x, y = y) }
    }

    fun resizeWindow(id: String, w: Float, h: Float) {
        windows[id]?.let { windows[id] = it.copy(width = w, height = h) }
    }

    fun minimizeAllWindows() {
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        windows.keys.toList().forEach { id ->
            val w = windows[id] ?: return@forEach
            if (w.isOpen) windows[id] = w.copy(isMinimized = true)
        }
        activeWindowId = null
    }

    // Notes
    fun createNote(folderId: String = "root-notes", pinned: Boolean = true): NoteItem {
        val id = "note-${System.currentTimeMillis()}"
        val note = NoteItem(
            id = id,
            title = "Note_${notes.size + 1}.txt",
            content = "",
            folderId = folderId,
            pinnedToDesktop = pinned,
            x = 40f + (notes.size % 4) * 16f,
            y = 70f + (notes.size % 4) * 20f
        )
        notes = listOf(note) + notes
        windows["note:$id"] = WindowState("note:$id", note.title, isOpen = true, zIndex = ++zCounter, x = note.x, y = note.y, width = 272f, height = 215f)
        activeWindowId = "note:$id"
        persist()
        return note
    }

    fun updateNote(id: String, transform: (NoteItem) -> NoteItem) {
        notes = notes.map { if (it.id == id) transform(it) else it }
        notes.find { it.id == id }?.let { updated ->
            windows["note:$id"]?.let { windows["note:$id"] = it.copy(title = updated.title) }
        }
        persist()
    }

    fun deleteNote(id: String) {
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        val target = notes.find { it.id == id } ?: return
        notes = notes.filterNot { it.id == id }
        trash = listOf(TrashItem("trash-${System.currentTimeMillis()}", "note", target.title, gson.toJson(target))) + trash
        closeWindow("note:$id")
        persist()
    }

    // Folders
    fun createFolder(name: String, parentId: String? = null) {
        if (name.isBlank()) return
        RetroSoundEngine.play(RetroSound.SUCCESS, soundEnabled)
        folders = folders + FolderItem("folder-${System.currentTimeMillis()}", name.trim(), parentId)
        persist()
    }

    fun renameFolder(id: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        folders = folders.map { if (it.id == id) it.copy(name = clean) else it }
        persist()
    }

    fun createTextFile(title: String, content: String = "", folderId: String = "root-notes"): NoteItem {
        val id = "note-${System.currentTimeMillis()}"
        val cleanTitle = title.trim().ifEmpty { "Note_${notes.size + 1}.txt" }
        val note = NoteItem(
            id = id,
            title = cleanTitle,
            content = content,
            folderId = folderId,
            pinnedToDesktop = false,
            x = 40f + (notes.size % 4) * 16f,
            y = 70f + (notes.size % 4) * 20f
        )
        notes = listOf(note) + notes
        persist()
        return note
    }

    fun deleteFolder(id: String) {
        if (id.startsWith("root-")) return
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        val target = folders.find { it.id == id } ?: return
        folders = folders.filterNot { it.id == id }
        trash = listOf(TrashItem("trash-${System.currentTimeMillis()}", "folder", target.name, gson.toJson(target))) + trash
        persist()
    }

    // Todos
    fun addTodo(name: String) {
        if (name.isBlank()) return
        RetroSoundEngine.play(RetroSound.SUCCESS, soundEnabled)
        todos = todos + TodoItem("h-${System.currentTimeMillis()}", name.trim())
        persist()
    }

    fun toggleTodoToday(id: String) {
        val t = todayIso()
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        todos = todos.map { item ->
            if (item.id != id) item
            else if (t in item.completedDates) item.copy(completedDates = item.completedDates - t)
            else item.copy(completedDates = item.completedDates + t)
        }
        persist()
    }

    fun deleteTodo(id: String) {
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        val target = todos.find { it.id == id } ?: return
        todos = todos.filterNot { it.id == id }
        trash = listOf(TrashItem("trash-${System.currentTimeMillis()}", "todo", target.name, gson.toJson(target))) + trash
        persist()
    }

    // Calendar
    fun setCalendarDay(date: String, stamp: String? = null, clearStamp: Boolean = false, note: String? = null) {
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        val cur = calendarEntries[date] ?: CalendarDayEntry(date)
        val updated = cur.copy(
            stamp = if (clearStamp) null else stamp ?: cur.stamp,
            note = note ?: cur.note
        )
        calendarEntries = calendarEntries + (date to updated)
        persist()
    }

    // Reminders
    fun addReminder(title: String, message: String, dueDate: String, notifyEnabled: Boolean = false) {
        RetroSoundEngine.play(RetroSound.NOTIFY, soundEnabled)
        val cleanDate = dueDate.ifBlank { todayIso() }
        val isDueNow = cleanDate <= todayIso()
        val item = ReminderItem(
            id = "rem-${System.currentTimeMillis()}",
            title = title.ifBlank { "Reminder" },
            message = message,
            timeLabel = cleanDate,
            dueDate = cleanDate,
            notifyEnabled = notifyEnabled,
            showPopup = isDueNow
        )
        reminders = listOf(item) + reminders
        persist()
    }

    fun toggleReminder(id: String) {
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        reminders = reminders.map { if (it.id == id) it.copy(completed = !it.completed, showPopup = false) else it }
        persist()
    }

    fun dismissReminderPopup(id: String) {
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        val today = todayIso()
        reminders = reminders.map { if (it.id == id) it.copy(showPopup = false, dismissedDate = today) else it }
        persist()
    }

    fun deleteReminder(id: String) {
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        val target = reminders.find { it.id == id } ?: return
        reminders = reminders.filterNot { it.id == id }
        trash = listOf(TrashItem("trash-${System.currentTimeMillis()}", "reminder", target.title, gson.toJson(target))) + trash
        persist()
    }

    // Music, Gallery, Voice, Paint
    fun addTracks(newTracks: List<MusicTrack>) {
        RetroSoundEngine.play(RetroSound.SUCCESS, soundEnabled)
        tracks = tracks + newTracks
        persist()
    }

    fun removeTrack(id: String) {
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        val target = tracks.find { it.id == id } ?: return
        tracks = tracks.filterNot { it.id == id }
        trash = listOf(TrashItem("trash-${System.currentTimeMillis()}", "music", target.title, gson.toJson(target))) + trash
        persist()
    }

    fun addPermittedPhotos(photos: List<PermittedPhoto>) {
        val existing = permittedPhotos.map { it.uri }.toSet()
        permittedPhotos = photos.filterNot { it.uri in existing } + permittedPhotos
        persist()
    }

    fun removePermittedPhoto(id: String) {
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        val target = permittedPhotos.find { it.id == id } ?: return
        permittedPhotos = permittedPhotos.filterNot { it.id == id }
        trash = listOf(TrashItem("trash-${System.currentTimeMillis()}", "photo", target.filename, gson.toJson(target))) + trash
        persist()
    }

    fun clearPermittedPhotos() {
        if (permittedPhotos.isEmpty()) return
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        val now = System.currentTimeMillis()
        val trashed = permittedPhotos.mapIndexed { idx, p ->
            TrashItem("trash-$now-$idx", "photo", p.filename, gson.toJson(p))
        }
        trash = trashed + trash
        permittedPhotos = emptyList()
        persist()
    }

    fun addVoiceMemo(title: String, path: String, durationSec: Int) {
        RetroSoundEngine.play(RetroSound.SUCCESS, soundEnabled)
        voiceMemos = listOf(VoiceMemo("memo-${System.currentTimeMillis()}", title, path, durationSec)) + voiceMemos
        persist()
    }

    fun deleteVoiceMemo(id: String) {
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        val target = voiceMemos.find { it.id == id } ?: return
        voiceMemos = voiceMemos.filterNot { it.id == id }
        trash = listOf(TrashItem("trash-${System.currentTimeMillis()}", "memo", target.title, gson.toJson(target))) + trash
        persist()
    }

    fun saveArtwork(title: String, pngBase64: String, existingId: String? = null): PaintArtwork {
        RetroSoundEngine.play(RetroSound.SUCCESS, soundEnabled)
        val cur = existingId?.let { id -> artworks.find { it.id == id } }
        val saved = if (cur != null) {
            cur.copy(title = title, pngBase64 = pngBase64).also { upd ->
                artworks = artworks.map { if (it.id == cur.id) upd else it }
            }
        } else {
            PaintArtwork("art-${System.currentTimeMillis()}", title, pngBase64).also { created ->
                artworks = listOf(created) + artworks
            }
        }
        persist()
        return saved
    }

    fun deleteArtwork(id: String) {
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        val target = artworks.find { it.id == id } ?: return
        artworks = artworks.filterNot { it.id == id }
        trash = listOf(TrashItem("trash-${System.currentTimeMillis()}", "artwork", target.title, gson.toJson(target))) + trash
        persist()
    }

    // Recycle Bin
    fun restoreTrashItem(trashId: String) {
        RetroSoundEngine.play(RetroSound.SUCCESS, soundEnabled)
        val item = trash.find { it.id == trashId } ?: return
        when (item.type) {
            "note" -> notes = listOf(gson.fromJson(item.payloadJson, NoteItem::class.java)) + notes
            "folder" -> folders = folders + gson.fromJson(item.payloadJson, FolderItem::class.java)
            "memo" -> voiceMemos = listOf(gson.fromJson(item.payloadJson, VoiceMemo::class.java)) + voiceMemos
            "artwork" -> artworks = listOf(gson.fromJson(item.payloadJson, PaintArtwork::class.java)) + artworks
            "photo" -> {
                val restored = gson.fromJson(item.payloadJson, PermittedPhoto::class.java)
                if (permittedPhotos.none { it.uri == restored.uri }) {
                    permittedPhotos = listOf(restored) + permittedPhotos
                }
            }
            "music" -> {
                val restored = gson.fromJson(item.payloadJson, MusicTrack::class.java)
                tracks = tracks + restored
            }
            "todo" -> todos = listOf(gson.fromJson(item.payloadJson, TodoItem::class.java)) + todos
            "reminder" -> reminders = listOf(gson.fromJson(item.payloadJson, ReminderItem::class.java)) + reminders
            "favorite" -> {
                val restored = gson.fromJson(item.payloadJson, BrowserFavorite::class.java)
                if (browserFavorites.none { it.url == restored.url }) {
                    browserFavorites = listOf(restored) + browserFavorites
                }
            }
            "contact" -> contacts = listOf(gson.fromJson(item.payloadJson, ContactItem::class.java)) + contacts
            "alarm" -> alarms = listOf(gson.fromJson(item.payloadJson, AlarmItem::class.java)) + alarms
            "book" -> {
                val restored = gson.fromJson(item.payloadJson, BookItem::class.java)
                if (books.none { it.uri == restored.uri }) books = listOf(restored) + books
            }
        }
        trash = trash.filterNot { it.id == trashId }
        persist()
    }

    fun deleteTrashItem(trashId: String) {
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        trash = trash.filterNot { it.id == trashId }
        persist()
    }

    fun emptyRecycleBin() {
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        trash = emptyList()
        persist()
    }

    // Icons, Clipboard, Move-to-Folder
    fun setIconPos(id: String, x: Float, y: Float) {
        iconPositions = iconPositions + (id to IconPos(x, y))
        persist()
    }

    fun resetIconPositions() {
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        iconPositions = emptyMap()
        persist()
    }

    fun toggleDesktopIcon(id: String) {
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        hiddenDesktopIcons = if (id in hiddenDesktopIcons) hiddenDesktopIcons - id else hiddenDesktopIcons + id
        persist()
    }

    fun hideDesktopIcon(id: String) {
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        if (id !in hiddenDesktopIcons) hiddenDesktopIcons = hiddenDesktopIcons + id
        persist()
    }

    fun showDesktopIcon(id: String) {
        RetroSoundEngine.play(RetroSound.SUCCESS, soundEnabled)
        hiddenDesktopIcons = hiddenDesktopIcons - id
        persist()
    }

    fun moveItemToFolder(type: String, id: String, targetFolderId: String) {
        RetroSoundEngine.play(RetroSound.SUCCESS, soundEnabled)
        when (type) {
            "note" -> notes = notes.map { if (it.id == id) it.copy(folderId = targetFolderId) else it }
            "artwork" -> artworks = artworks.map { if (it.id == id) it.copy(folderId = targetFolderId) else it }
            "memo" -> voiceMemos = voiceMemos.map { if (it.id == id) it.copy(folderId = targetFolderId) else it }
        }
        persist()
    }

    fun copyToClipboard(type: String, itemId: String, title: String) {
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        clipboard = ClipboardEntry("copy", type, itemId, title)
    }

    fun cutToClipboard(type: String, itemId: String, title: String) {
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        clipboard = ClipboardEntry("cut", type, itemId, title)
    }

    fun pasteFromClipboard(targetFolderId: String, pinOnDesktop: Boolean = false, dropPos: IconPos? = null) {
        val cb = clipboard ?: return
        RetroSoundEngine.play(RetroSound.SUCCESS, soundEnabled)
        if (cb.mode == "cut") {
            moveItemToFolder(cb.type, cb.itemId, targetFolderId)
            if (cb.type == "note" && pinOnDesktop) {
                notes = notes.map { if (it.id == cb.itemId) it.copy(pinnedToDesktop = true) else it }
                dropPos?.let { setIconPos("icon-note-${cb.itemId}", it.x, it.y) }
            }
            clipboard = null
            persist()
            return
        }
        when (cb.type) {
            "note" -> {
                val src = notes.find { it.id == cb.itemId } ?: return
                val newId = "note-${System.currentTimeMillis()}"
                val dup = src.copy(
                    id = newId,
                    title = src.title.replace(Regex("\\.txt$", RegexOption.IGNORE_CASE), "_copy.txt"),
                    folderId = targetFolderId,
                    pinnedToDesktop = pinOnDesktop
                )
                notes = listOf(dup) + notes
                if (pinOnDesktop && dropPos != null) setIconPos("icon-note-$newId", dropPos.x, dropPos.y)
            }
            "artwork" -> {
                val src = artworks.find { it.id == cb.itemId } ?: return
                artworks = listOf(src.copy(id = "art-${System.currentTimeMillis()}", title = "${src.title}_copy.BMP", folderId = targetFolderId)) + artworks
            }
            "memo" -> {
                val src = voiceMemos.find { it.id == cb.itemId } ?: return
                voiceMemos = listOf(src.copy(id = "memo-${System.currentTimeMillis()}", title = "${src.title}_copy.WAV", folderId = targetFolderId)) + voiceMemos
            }
        }
        persist()
    }

    // Browser Favorites & Mail Popups
    fun addBrowserFavorite(title: String, url: String) {
        if (url.isBlank() || url == "about:home") return
        if (browserFavorites.any { it.url == url }) return
        RetroSoundEngine.play(RetroSound.SUCCESS, soundEnabled)
        browserFavorites = listOf(BrowserFavorite("fav-${System.currentTimeMillis()}", title, url)) + browserFavorites
        persist()
    }

    fun removeBrowserFavorite(id: String) {
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        val target = browserFavorites.find { it.id == id } ?: return
        browserFavorites = browserFavorites.filterNot { it.id == id }
        trash = listOf(TrashItem("trash-${System.currentTimeMillis()}", "favorite", target.title, gson.toJson(target))) + trash
        persist()
    }

    fun triggerMailPopup(sender: String, subject: String, force: Boolean = false) {
        if (!force && !mailPopupsEnabled) return
        RetroSoundEngine.play(RetroSound.MAIL, soundEnabled)
        mailPopupAlert = MailPopupAlert(sender, subject)
    }

    fun dismissMailPopup() {
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        mailPopupAlert = null
    }

    // Contacts
    fun addContact(name: String, phone: String, note: String = "") {
        if (name.isBlank() || phone.isBlank()) return
        RetroSoundEngine.play(RetroSound.SUCCESS, soundEnabled)
        contacts = (contacts + ContactItem("cnt-${System.currentTimeMillis()}", name.trim(), phone.trim(), note.trim()))
            .sortedBy { it.name.lowercase() }
        persist()
    }

    fun deleteContact(id: String) {
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        val target = contacts.find { it.id == id } ?: return
        contacts = contacts.filterNot { it.id == id }
        trash = listOf(TrashItem("trash-${System.currentTimeMillis()}", "contact", target.name, gson.toJson(target))) + trash
        persist()
    }

    // Alarms
    fun addAlarm(hour: Int, minute: Int, label: String) {
        RetroSoundEngine.play(RetroSound.SUCCESS, soundEnabled)
        alarms = (alarms + AlarmItem("alm-${System.currentTimeMillis()}", hour.coerceIn(0, 23), minute.coerceIn(0, 59), label.ifBlank { "Alarm" }, true))
            .sortedWith(compareBy({ it.hour }, { it.minute }))
        persist()
    }

    fun toggleAlarm(id: String) {
        RetroSoundEngine.play(RetroSound.CLICK, soundEnabled)
        alarms = alarms.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
        persist()
    }

    fun deleteAlarm(id: String) {
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        val target = alarms.find { it.id == id } ?: return
        alarms = alarms.filterNot { it.id == id }
        val timeStr = "%02d:%02d".format(target.hour, target.minute)
        trash = listOf(TrashItem("trash-${System.currentTimeMillis()}", "alarm", "$timeStr ${target.label}", gson.toJson(target))) + trash
        persist()
    }

    // E-Books
    fun addBook(title: String, uri: String): BookItem {
        val existing = books.find { it.uri == uri }
        if (existing != null) return existing
        RetroSoundEngine.play(RetroSound.SUCCESS, soundEnabled)
        val item = BookItem("book-${System.currentTimeMillis()}", title.trim().ifEmpty { "Untitled Book" }, uri, 0)
        books = listOf(item) + books
        persist()
        return item
    }

    fun updateBookPage(id: String, page: Int) {
        books = books.map { if (it.id == id) it.copy(currentPage = page.coerceAtLeast(0)) else it }
        persist()
    }

    fun deleteBook(id: String) {
        RetroSoundEngine.play(RetroSound.TRASH, soundEnabled)
        val target = books.find { it.id == id } ?: return
        books = books.filterNot { it.id == id }
        trash = listOf(TrashItem("trash-${System.currentTimeMillis()}", "book", target.title, gson.toJson(target))) + trash
        persist()
    }

    // Weather
    fun setWeatherLocation(city: String, lat: Double, lon: Double) {
        weatherCity = city
        weatherLat = lat
        weatherLon = lon
        persist()
    }
}
