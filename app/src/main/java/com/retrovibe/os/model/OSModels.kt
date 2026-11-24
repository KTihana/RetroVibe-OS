package com.retrovibe.os.model

enum class ThemeId { DEAR_PASTEL, WIN95_CLASSIC, Y2K_PINK, VAPORWAVE_NIGHT }

enum class ScreensaverId(val label: String, val desc: String) {
    TOASTERS("Flying Toasters!", "After Dark chrome toasters & buttered toast"),
    PIPES("3D Pipes", "Classic Win98 colorful growing pipes"),
    MAZE("3D Maze", "First-person retro brick maze walkthrough"),
    STARFIELD("Starfield", "Warp-speed stars through deep space"),
    HEARTS("Flying Hearts", "RetroVibe floating pastel pixel hearts"),
    NONE("(None)", "Disable screensaver")
}

data class WindowState(
    val id: String,
    val title: String,
    val isOpen: Boolean = false,
    val isMinimized: Boolean = false,
    val isMaximized: Boolean = false,
    val zIndex: Int = 4,
    val x: Float = 20f,
    val y: Float = 44f,
    val width: Float = 320f,
    val height: Float = 380f
)

data class NoteItem(
    val id: String,
    val title: String,
    val content: String,
    val folderId: String = "root-notes",
    val pinnedToDesktop: Boolean = false,
    val x: Float = 80f,
    val y: Float = 100f
)

data class FolderItem(
    val id: String,
    val name: String,
    val parentId: String? = null
)

data class CalendarDayEntry(
    val date: String, // YYYY-MM-DD
    val stamp: String? = null, // heart | star | cake | sparkle | music
    val note: String = ""
)

data class ReminderItem(
    val id: String,
    val title: String,
    val message: String,
    val timeLabel: String,
    val dueDate: String = "", // YYYY-MM-DD
    val notifyEnabled: Boolean = false,
    val dismissedDate: String = "",
    val completed: Boolean = false,
    val showPopup: Boolean = false
)

data class TodoItem(
    val id: String,
    val name: String,
    val completedDates: List<String> = emptyList()
)

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String = "RetroVibe Synth",
    val uri: String? = null,
    val isDemo: Boolean = false
)

data class VoiceMemo(
    val id: String,
    val title: String,
    val path: String,
    val durationSec: Int,
    val folderId: String = "root-voice"
)

data class PaintArtwork(
    val id: String,
    val title: String,
    val pngBase64: String,
    val folderId: String = "root-art"
)

data class PermittedPhoto(
    val id: String,
    val uri: String,
    val filename: String
)

data class BrowserFavorite(
    val id: String,
    val title: String,
    val url: String
)

data class TrashItem(
    val id: String,
    val type: String, // note | artwork | memo | folder
    val title: String,
    val payloadJson: String
)

data class ClipboardEntry(
    val mode: String, // copy | cut
    val type: String, // note | artwork | memo
    val itemId: String,
    val title: String
)

data class IconPos(val x: Float, val y: Float)

data class MailPopupAlert(val sender: String, val subject: String)

data class PersistedOSState(
    val themeId: ThemeId = ThemeId.DEAR_PASTEL,
    val customWallpaperUri: String? = null,
    val soundEnabled: Boolean = true,
    val notes: List<NoteItem> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val calendarEntries: Map<String, CalendarDayEntry> = emptyMap(),
    val reminders: List<ReminderItem> = emptyList(),
    val todos: List<TodoItem> = emptyList(),
    val tracks: List<MusicTrack> = emptyList(),
    val voiceMemos: List<VoiceMemo> = emptyList(),
    val artworks: List<PaintArtwork> = emptyList(),
    val permittedPhotos: List<PermittedPhoto> = emptyList(),
    val trash: List<TrashItem> = emptyList(),
    val iconPositions: Map<String, IconPos> = emptyMap(),
    val hiddenDesktopIcons: List<String> = listOf(
        "icon-calc", "icon-recorder", "icon-settings", "icon-status", "icon-taskmgr", "icon-sweeper"
    ),
    val deskCatEnabled: Boolean = false,
    val deskCatVariant: String = "mindi",
    val deskCatX: Float = 220f,
    val browserFavorites: List<BrowserFavorite> = emptyList(),
    val mailPopupsEnabled: Boolean = false,
    val screensaverId: ScreensaverId = ScreensaverId.TOASTERS,
    val screensaverWaitSec: Int = 0,
    val crtScanlines: Boolean = true,
    val loginPromptEnabled: Boolean = false,
    val username: String = "RetroUser",
    val githubRepo: String = "KTihana/RetroVibe-OS"
)
