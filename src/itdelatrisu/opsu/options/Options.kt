/*
 * opsu! - an open-source osu! client
 * Copyright (C) 2014-2017 Jeffrey Han
 *
 * opsu! is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * opsu! is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with opsu!.  If not, see <http://www.gnu.org/licenses/>.
 */

package itdelatrisu.opsu.options

import itdelatrisu.opsu.Container
import itdelatrisu.opsu.OpsuConstants
import itdelatrisu.opsu.Utils
import itdelatrisu.opsu.audio.MusicController
import itdelatrisu.opsu.beatmap.Beatmap
import itdelatrisu.opsu.beatmap.TimingPoint
import itdelatrisu.opsu.skins.Skin
import itdelatrisu.opsu.skins.SkinLoader
import itdelatrisu.opsu.ui.Fonts
import itdelatrisu.opsu.ui.UI
import org.lwjgl.LWJGLException
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.Display
import org.newdawn.slick.GameContainer
import org.newdawn.slick.Input
import org.newdawn.slick.SlickException
import org.newdawn.slick.openal.SoundStore
import org.newdawn.slick.util.ClasspathLocation
import org.newdawn.slick.util.FileSystemLocation
import org.newdawn.slick.util.Log
import org.newdawn.slick.util.ResourceLoader
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.*

object Options {

    // ─── Paths ───────────────────────────────────────────────────────────────

    val USE_XDG: Boolean = checkXDGFlag()

    private val CONFIG_DIR = xdgBase("XDG_CONFIG_HOME", ".config")
    private val DATA_DIR   = xdgBase("XDG_DATA_HOME",   ".local/share")
    private val CACHE_DIR  = xdgBase("XDG_CACHE_HOME",  ".cache")

    @JvmField val LOG_FILE     = File(CONFIG_DIR, ".opsu.log")
    @JvmField val BEATMAP_DB   = File(DATA_DIR,   ".opsu.db")
    @JvmField val SCORE_DB     = File(DATA_DIR,   ".opsu_scores.db")
    @JvmField val NATIVE_DIR   = File(CACHE_DIR,  "Natives/")
    @JvmField val TEMP_DIR     = File(CACHE_DIR,  "Temp/")

    private val OPTIONS_FILE   = File(CONFIG_DIR, ".opsu.cfg")
    private val DEFAULT_BEATMAP_DIR = File(DATA_DIR, "Songs/")
    private val DEFAULT_SKIN_DIR    = File(DATA_DIR, "Skins/")

    const val FONT_MAIN    = "Exo2-Regular.ttf"
    const val FONT_BOLD    = "Exo2-Bold.ttf"
    const val FONT_CJK     = "DroidSansFallback.ttf"
    const val VERSION_FILE = "version"
    const val USER_AGENT   = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36"

    // ─── Mutable state ───────────────────────────────────────────────────────

    private var beatmapDir:    File? = null
    private var importDir:     File? = null
    private var screenshotDir: File? = null
    private var replayDir:     File? = null
    private var skinRootDir:   File? = null
    private var FFmpegPath:    File? = null
    private var skin:          Skin? = null
    private var skinName             = "Default"
    private var resolution           = Resolution.RES_1024_768
    private var screenshotFormatIndex = 0
    private var targetFPSindex        = 0
    private var keyLeft               = Keyboard.KEY_NONE
    private var keyRight              = Keyboard.KEY_NONE

    private var themeString      = "theme.mp3,Rainbows,Kevin MacLeod,219350"
    private var themeTimingPoint = "1120,545.454545454545,4,1,0,100,0,0"

    private val screenshotFormats = arrayOf("png", "jpg", "bmp")
    private val targetFPS         = intArrayOf(60, 120, 240, -1)

    // ─── Sealed option types ─────────────────────────────────────────────────

    sealed class GameOption(
        val name: String?,
        val displayName: String,
        val description: String?,
    ) {
        open fun isRestartRequired()    = false
        open fun getItemList(): Array<Any>? = null
        open fun selectItem(index: Int, container: GameContainer) {}
        abstract fun getValueString(): String
        abstract fun write(): String
        abstract fun read(s: String)
        var visible = true

        // compatibility shims used by OptionsOverlay
        fun getBooleanValue()  = (this as? BoolOpt)?.value ?: false
        fun getIntegerValue()  = (this as? IntOpt)?.value  ?: 0
        fun getMinValue()      = (this as? IntOpt)?.min    ?: 0
        fun getMaxValue()      = (this as? IntOpt)?.max    ?: 0
        fun setValue(v: Boolean) { (this as? BoolOpt)?.value = v }
        fun setValue(v: Int)     { (this as? IntOpt)?.value  = v }
        fun toggle(container: GameContainer) { (this as? BoolOpt)?.run { value = !value; onToggle(container) } }
        fun matches(query: String) =
            query.isNotEmpty() && (name?.lowercase()?.contains(query) == true ||
                    description?.lowercase()?.contains(query) == true)
        enum class OptionType { BOOLEAN, NUMERIC, SELECT }
        fun getType() = when (this) {
            is BoolOpt -> OptionType.BOOLEAN
            is IntOpt  -> OptionType.NUMERIC
            else       -> OptionType.SELECT
        }
    }

    class BoolOpt(
        name: String, displayName: String, description: String,
        default: Boolean,
        val onToggle: (GameContainer) -> Unit = {},
        val restartRequired: Boolean = false,
    ) : GameOption(name, displayName, description) {
        var value = default
        override fun isRestartRequired()  = restartRequired
        override fun getValueString()     = if (value) "Yes" else "No"
        override fun write()              = value.toString()
        override fun read(s: String)      { value = s.toBoolean() }
    }

    class IntOpt(
        name: String, displayName: String, description: String,
        default: Int, val min: Int, val max: Int,
    ) : GameOption(name, displayName, description) {
        var value = default
            set(v) { field = v.coerceIn(min, max) }
        override fun getValueString() = "$value%"
        override fun write()          = value.toString()
        override fun read(s: String)  { s.toIntOrNull()?.let { if (it in min..max) value = it } }
    }

    // ─── Option definitions ───────────────────────────────────────────────────

    // Internal (config file only)
    val BEATMAP_DIRECTORY = object : GameOption(null, "BeatmapDirectory", null) {
        override fun getValueString() = getBeatmapDir().absolutePath
        override fun write()          = getBeatmapDir().absolutePath
        override fun read(s: String)  { beatmapDir = File(s) }
    }
    val IMPORT_DIRECTORY = object : GameOption(null, "ImportDirectory", null) {
        override fun getValueString() = getImportDir().absolutePath
        override fun write()          = getImportDir().absolutePath
        override fun read(s: String)  { importDir = File(s) }
    }
    val SCREENSHOT_DIRECTORY = object : GameOption(null, "ScreenshotDirectory", null) {
        override fun getValueString() = getScreenshotDir().absolutePath
        override fun write()          = getScreenshotDir().absolutePath
        override fun read(s: String)  { screenshotDir = File(s) }
    }
    val REPLAY_DIRECTORY = object : GameOption(null, "ReplayDirectory", null) {
        override fun getValueString() = getReplayDir().absolutePath
        override fun write()          = getReplayDir().absolutePath
        override fun read(s: String)  { replayDir = File(s) }
    }
    val SKIN_DIRECTORY = object : GameOption(null, "SkinDirectory", null) {
        override fun getValueString() = getSkinRootDir().absolutePath
        override fun write()          = getSkinRootDir().absolutePath
        override fun read(s: String)  { skinRootDir = File(s) }
    }
    val FFMPEG_PATH = object : GameOption(null, "FFmpegPath", null) {
        override fun getValueString() = FFmpegPath?.absolutePath ?: ""
        override fun write()          = FFmpegPath?.absolutePath ?: ""
        override fun read(s: String)  { if (s.isNotEmpty()) FFmpegPath = File(s) }
    }
    val THEME_SONG = object : GameOption(null, "ThemeSong", null) {
        override fun getValueString() = themeString
        override fun write()          = themeString
        override fun read(s: String) {
            val old = themeString; themeString = s
            val b = getThemeBeatmap()
            if (b == null || (!b.audioFilename.isFile && !ResourceLoader.resourceExists(b.audioFilename.name))) {
                themeString = old
                Log.warn("Cannot use theme song [$s]")
            }
        }
    }
    val THEME_SONG_TIMINGPOINT = object : GameOption(null, "ThemeSongTiming", null) {
        override fun getValueString() = themeTimingPoint
        override fun write()          = themeTimingPoint
        override fun read(s: String) {
            runCatching { TimingPoint(s); themeTimingPoint = s }
                .onFailure { Log.warn("Bad timing point [$s]") }
        }
    }

    // General
    val SCREEN_RESOLUTION = object : GameOption("Resolution", "ScreenResolution", "") {
        private val items by lazy {
            val w = Display.getDesktopDisplayMode().width
            val h = Display.getDesktopDisplayMode().height
            Resolution.entries.filter { it == Resolution.RES_800_600 || (w >= it.width && h >= it.height) }.toTypedArray()
        }
        override fun isRestartRequired() = true
        override fun getValueString()    = resolution.toString()
        override fun getItemList()       = items as Array<Any>
        override fun selectItem(index: Int, container: GameContainer) {
            resolution = items[index]
            if (FULLSCREEN.value && !resolution.hasFullscreenMode()) FULLSCREEN.toggle(container)
        }
        override fun write()            = resolution.toString().removePrefix("RES_").replace('_', 'x')
        override fun read(s: String)    { runCatching { resolution = Resolution.valueOf("RES_${s.replace('x', '_')}") } }
    }
    val FULLSCREEN = BoolOpt("Fullscreen mode", "Fullscreen", "Switches to dedicated fullscreen mode.", false,
        restartRequired = true,
        onToggle = { container ->
            if (FULLSCREEN.value && !resolution.hasFullscreenMode()) {
                UI.getNotificationManager().sendBarNotification("Fullscreen not available at $resolution")
                FULLSCREEN.value = !FULLSCREEN.value
            }
        }
    )
    val SKIN = object : GameOption("Skin", "Skin", "") {
        private var items: Array<String>? = null
        private fun skinList() = items ?: run {
            val dirs = SkinLoader.getSkinDirectories(getSkinRootDir())
            arrayOf(Skin.DEFAULT_SKIN_NAME, *dirs.map { it.name }.toTypedArray()).also { items = it }
        }
        override fun isRestartRequired() = true
        override fun getValueString()    = skinName
        override fun getItemList()       = skinList() as Array<Any>
        override fun selectItem(index: Int, container: GameContainer) { skinName = skinList()[index] }
        override fun write()             = skinName
        override fun read(s: String)     { skinName = s }
    }
    val TARGET_FPS = object : GameOption("Frame limiter", "FrameSync", "Higher values may cause high CPU usage.") {
        private val labels = arrayOf("60fps (vsync)", "120fps", "240fps", "Unlimited")
        override fun getValueString() = labels[targetFPSindex]
        override fun getItemList()    = labels as Array<Any>
        override fun selectItem(index: Int, container: GameContainer) {
            targetFPSindex = index
            val fps = targetFPS[index]
            container.setTargetFrameRate(fps)
            if (container.isVSyncRequested != (fps == 60)) container.setVSync(fps == 60)
        }
        override fun write()         = targetFPS[targetFPSindex].toString()
        override fun read(s: String) { s.toIntOrNull()?.let { v -> targetFPSindex = targetFPS.indexOfFirst { it == v }.takeIf { it >= 0 } ?: 0 } }
    }
    val SHOW_FPS = BoolOpt("Show FPS counter", "FpsCounter",
        "Show a subtle FPS counter in the bottom right corner.", true,
        onToggle = { UI.resetFPSDisplay() }
    )
    val SHOW_UNICODE = BoolOpt("Prefer metadata in original language", "ShowUnicode",
        "Where available, song titles will be shown in their native language.", false,
        onToggle = { runCatching { Fonts.LARGE.loadGlyphs(); Fonts.MEDIUM.loadGlyphs(); Fonts.DEFAULT.loadGlyphs() } }
    )
    val SCREENSHOT_FORMAT = object : GameOption("Screenshot format", "ScreenshotFormat", "Press F12 to take a screenshot.") {
        override fun getValueString() = screenshotFormats[screenshotFormatIndex].uppercase()
        override fun getItemList()    = screenshotFormats.map { it.uppercase() }.toTypedArray() as Array<Any>
        override fun selectItem(index: Int, container: GameContainer) { screenshotFormatIndex = index }
        override fun write()          = screenshotFormatIndex.toString()
        override fun read(s: String)  { s.toIntOrNull()?.let { if (it in screenshotFormats.indices) screenshotFormatIndex = it } }
    }
    val CURSOR_SIZE = object : IntOpt("Cursor size", "CursorSize", "Change the cursor scale.", 100, 50, 200) {
        override fun getValueString() = "%.2fx".format(value / 100f)
        override fun write()          = "%.2f".format(value / 100f)
        override fun read(s: String)  { s.toFloatOrNull()?.let { value = (it * 100).toInt() } }
    }
    val DYNAMIC_BACKGROUND  = BoolOpt("Dynamic backgrounds",   "DynamicBackground", "The current beatmap background will be used as the main menu background.", true)
    val LOAD_VERBOSE        = BoolOpt("Detailed loading progress", "LoadVerbose",   "Display more verbose loading progress in the splash screen.", false)
    val MASTER_VOLUME = object : IntOpt("Master", "VolumeUniversal", "Global volume level.", 35, 0, 100) {
        override fun setValue(v: Int) { super.setValue(v); SoundStore.get().setMusicVolume(getMasterVolume() * getMusicVolume()) }
    }
    val MUSIC_VOLUME = object : IntOpt("Music", "VolumeMusic", "Music volume.", 80, 0, 100) {
        override fun setValue(v: Int) { super.setValue(v); SoundStore.get().setMusicVolume(getMasterVolume() * getMusicVolume()) }
    }
    val EFFECT_VOLUME       = IntOpt("Effects",    "VolumeEffect",    "Menu and game sound effects volume.", 70, 0, 100)
    val HITSOUND_VOLUME     = IntOpt("Hit sounds", "VolumeHitSound",  "Hit sounds volume.", 30, 0, 100)
    val MUSIC_OFFSET = object : IntOpt("Universal offset", "Offset", "Adjust if hit objects are out of sync.", -75, -500, 500) {
        override fun getValueString() = "${value}ms"
    }
    val DISABLE_GAMEPLAY_SOUNDS = BoolOpt("Disable sound effects in gameplay", "DisableGameplaySound", "Mute all sound effects during gameplay only.", false)
    val DISABLE_SOUNDS          = BoolOpt("Disable all sound effects", "DisableSound", "May resolve Linux sound driver issues.\nRequires a restart.", false, restartRequired = true)
    val KEY_LEFT = object : GameOption("Left game key", "keyOsuLeft", "Select this option to input a key.") {
        override fun getValueString() = Keyboard.getKeyName(getGameKeyLeft())
        override fun write()          = Keyboard.getKeyName(getGameKeyLeft())
        override fun read(s: String)  { setGameKeyLeft(Keyboard.getKeyIndex(s)) }
    }
    val KEY_RIGHT = object : GameOption("Right game key", "keyOsuRight", "Select this option to input a key.") {
        override fun getValueString() = Keyboard.getKeyName(getGameKeyRight())
        override fun write()          = Keyboard.getKeyName(getGameKeyRight())
        override fun read(s: String)  { setGameKeyRight(Keyboard.getKeyIndex(s)) }
    }
    val DISABLE_MOUSE_WHEEL   = BoolOpt("Disable mouse wheel in play mode", "MouseDisableWheel",    "During play, the mouse wheel adjusts volume and pauses.\nThis disables that.", false)
    val DISABLE_MOUSE_BUTTONS = BoolOpt("Disable mouse buttons in play mode", "MouseDisableButtons","Disables all mouse buttons during play.", false)
    val DISABLE_CURSOR        = BoolOpt("Disable cursor",                "DisableCursor",           "Hides the cursor sprite.", false)
    val BACKGROUND_DIM        = IntOpt("Background dim",   "DimLevel",              "Percentage to dim the background during gameplay.", 50, 0, 100)
    val FORCE_DEFAULT_PLAYFIELD = BoolOpt("Force default playfield", "ForceDefaultPlayfield", "Overrides the song background with the default playfield background.", false)
    val ENABLE_VIDEOS           = BoolOpt("Background video",  "Video",             "Enables background video playback.", true)
    val IGNORE_BEATMAP_SKINS    = BoolOpt("Ignore all beatmap skins", "IgnoreBeatmapSkins", "Never use skin element overrides provided by beatmaps.", false)
    val FORCE_SKIN_CURSOR       = BoolOpt("Always use skin cursor",   "UseSkinCursor",      "The selected skin's cursor overrides beatmap cursor modifications.", false)
    val SNAKING_SLIDERS         = BoolOpt("Snaking sliders",     "SnakingSliders",    "Sliders gradually snake out from their starting point.", true)
    val EXPERIMENTAL_SLIDERS    = BoolOpt("Use experimental sliders", "ExperimentalSliders", "Render sliders using the experimental slider style.", false)
    val EXPERIMENTAL_SLIDERS_CAPS   = BoolOpt("Draw slider caps",    "ExperimentalSliderCaps",   "Draw caps (end circles) on sliders. Only applies to experimental sliders.", false)
    val EXPERIMENTAL_SLIDERS_SHRINK = BoolOpt("Shrinking sliders",  "ExperimentalSliderShrink", "Sliders shrink toward their ending point. Only applies to experimental sliders.", true)
    val EXPERIMENTAL_SLIDERS_MERGE  = BoolOpt("Merging sliders",    "ExperimentalSliderMerge",  "Combine overlapping slider tracks. Only applies to experimental sliders.", true)
    val SHOW_HIT_LIGHTING   = BoolOpt("Hit lighting",    "HitLighting",    "Adds a subtle glow behind hit explosions.", true)
    val SHOW_COMBO_BURSTS   = BoolOpt("Combo bursts",    "ComboBurst",     "A character image bursts from the side at combo milestones.", true)
    val SHOW_PERFECT_HIT    = BoolOpt("Perfect hits",    "PerfectHit",     "Shows perfect hit result bursts (300s, slider ticks).", true)
    val SHOW_FOLLOW_POINTS  = BoolOpt("Follow points",   "FollowPoints",   "Shows follow points between hit objects.", true)
    val SHOW_HIT_ERROR_BAR  = BoolOpt("Hit error bar",   "ScoreMeter",     "Shows precisely how accurate you were with each hit.", false)
    val ALWAYS_SHOW_KEY_OVERLAY = BoolOpt("Always show key overlay", "KeyOverlay", "Show the key overlay when playing instead of only on replays.", false)
    val LOAD_HD_IMAGES      = BoolOpt("Load HD images",  "LoadHDImages",   "Loads HD (@2x) images when available.", true, restartRequired = true)
    val FIXED_CS = object : IntOpt("Fixed CS", "FixedCS", "Determines the size of circles and sliders.", 0, 0, 100) {
        override fun getValueString() = if (value == 0) "Disabled" else "%.1f".format(value / 10f)
        override fun write()          = "%.1f".format(value / 10f)
        override fun read(s: String)  { s.toFloatOrNull()?.let { value = (it * 10).toInt() } }
    }
    val FIXED_HP = object : IntOpt("Fixed HP", "FixedHP", "Determines the rate at which health decreases.", 0, 0, 100) {
        override fun getValueString() = if (value == 0) "Disabled" else "%.1f".format(value / 10f)
        override fun write()          = "%.1f".format(value / 10f)
        override fun read(s: String)  { s.toFloatOrNull()?.let { value = (it * 10).toInt() } }
    }
    val FIXED_AR = object : IntOpt("Fixed AR", "FixedAR", "Determines how long hit circles stay on screen.", 0, 0, 100) {
        override fun getValueString() = if (value == 0) "Disabled" else "%.1f".format(value / 10f)
        override fun write()          = "%.1f".format(value / 10f)
        override fun read(s: String)  { s.toFloatOrNull()?.let { value = (it * 10).toInt() } }
    }
    val FIXED_OD = object : IntOpt("Fixed OD", "FixedOD", "Determines the time window for hit results.", 0, 0, 100) {
        override fun getValueString() = if (value == 0) "Disabled" else "%.1f".format(value / 10f)
        override fun write()          = "%.1f".format(value / 10f)
        override fun read(s: String)  { s.toFloatOrNull()?.let { value = (it * 10).toInt() } }
    }
    val FIXED_SPEED = object : IntOpt("Fixed speed", "FixedSpeed", "Determines the speed of the music.", 0, 0, 300) {
        override fun getValueString() = if (value == 0) "Disabled" else "%.2fx".format(value / 100f)
        override fun write()          = "%.2f".format(value / 100f)
        override fun read(s: String)  { s.toFloatOrNull()?.let { value = (it * 100).toInt() } }
    }
    val CHECKPOINT = object : IntOpt("Track checkpoint", "Checkpoint", "Press Ctrl+L while playing to load a checkpoint, Ctrl+S to set one.", 0, 0, 1800) {
        override fun getValueString() = if (value == 0) "Disabled"
        else "%02d:%02d".format(value / 60, value % 60)
    }
    val PARALLAX           = BoolOpt("Parallax",      "MenuParallax",  "Add a parallax effect based on the cursor position.", true)
    val ENABLE_THEME_SONG  = BoolOpt("Theme song",    "MenuMusic",     "${OpsuConstants.PROJECT_NAME} will play themed music throughout the game.", true)
    val REPLAY_SEEKING     = BoolOpt("Replay seeking", "ReplaySeeking", "Enable a seeking bar on the left side of the screen during replays.", false)
    val DISABLE_UPDATER    = BoolOpt("Disable automatic updates", "DisableUpdater", "Disable checking for updates when the game starts.", false)
    val ENABLE_WATCH_SERVICE = BoolOpt("Watch service", "WatchService", "Watch the beatmap directory for changes. Requires a restart.", false, restartRequired = true)

    // ── All options (for iteration / file I/O) ────────────────────────────────
    val ALL_OPTIONS: List<GameOption> = listOf(
        // internal
        BEATMAP_DIRECTORY, IMPORT_DIRECTORY, SCREENSHOT_DIRECTORY, REPLAY_DIRECTORY,
        SKIN_DIRECTORY, FFMPEG_PATH, THEME_SONG, THEME_SONG_TIMINGPOINT,
        // visible
        SCREEN_RESOLUTION, FULLSCREEN, SKIN, TARGET_FPS, SHOW_FPS, SHOW_UNICODE,
        SCREENSHOT_FORMAT, CURSOR_SIZE, DYNAMIC_BACKGROUND, LOAD_VERBOSE,
        MASTER_VOLUME, MUSIC_VOLUME, EFFECT_VOLUME, HITSOUND_VOLUME, MUSIC_OFFSET,
        DISABLE_GAMEPLAY_SOUNDS, DISABLE_SOUNDS, KEY_LEFT, KEY_RIGHT,
        DISABLE_MOUSE_WHEEL, DISABLE_MOUSE_BUTTONS, DISABLE_CURSOR,
        BACKGROUND_DIM, FORCE_DEFAULT_PLAYFIELD, ENABLE_VIDEOS,
        IGNORE_BEATMAP_SKINS, FORCE_SKIN_CURSOR, SNAKING_SLIDERS,
        EXPERIMENTAL_SLIDERS, EXPERIMENTAL_SLIDERS_CAPS, EXPERIMENTAL_SLIDERS_SHRINK, EXPERIMENTAL_SLIDERS_MERGE,
        SHOW_HIT_LIGHTING, SHOW_COMBO_BURSTS, SHOW_PERFECT_HIT, SHOW_FOLLOW_POINTS,
        SHOW_HIT_ERROR_BAR, ALWAYS_SHOW_KEY_OVERLAY, LOAD_HD_IMAGES,
        FIXED_CS, FIXED_HP, FIXED_AR, FIXED_OD, FIXED_SPEED,
        CHECKPOINT, PARALLAX, ENABLE_THEME_SONG, REPLAY_SEEKING,
        DISABLE_UPDATER, ENABLE_WATCH_SERVICE,
    )

    private val optionByDisplayName = ALL_OPTIONS.associateBy { it.displayName }

    // ─── Screen resolutions ───────────────────────────────────────────────────

    private enum class Resolution(val width: Int, val height: Int) {
        RES_800_600(800, 600), RES_1024_600(1024, 600), RES_1024_768(1024, 768),
        RES_1280_720(1280, 720), RES_1280_800(1280, 800), RES_1280_960(1280, 960),
        RES_1280_1024(1280, 1024), RES_1366_768(1366, 768), RES_1440_900(1440, 900),
        RES_1600_900(1600, 900), RES_1600_1200(1600, 1200), RES_1680_1050(1680, 1050),
        RES_1920_1080(1920, 1080), RES_1920_1200(1920, 1200),
        RES_2560_1440(2560, 1440), RES_2560_1600(2560, 1600), RES_3840_2160(3840, 2160);

        fun hasFullscreenMode() = try {
            Display.getAvailableDisplayModes().any { it.width == width && it.height == height }
        } catch (e: LWJGLException) { false }

        override fun toString() = "${width}x${height}"
    }

    // ─── Public accessors (same API as before) ────────────────────────────────

    @JvmStatic fun getTargetFPS()          = targetFPS[targetFPSindex]
    @JvmStatic fun getMasterVolume()       = MASTER_VOLUME.value / 100f
    @JvmStatic fun getMusicVolume()        = MUSIC_VOLUME.value / 100f
    @JvmStatic fun getEffectVolume()       = EFFECT_VOLUME.value / 100f
    @JvmStatic fun getHitSoundVolume()     = HITSOUND_VOLUME.value / 100f
    @JvmStatic fun getMusicOffset()        = MUSIC_OFFSET.value
    @JvmStatic fun getScreenshotFormat()   = screenshotFormats[screenshotFormatIndex]
    @JvmStatic fun getCursorScale()        = CURSOR_SIZE.value / 100f
    @JvmStatic fun getBackgroundDim()      = (100 - BACKGROUND_DIM.value) / 100f
    @JvmStatic fun getFixedCS()            = FIXED_CS.value / 10f
    @JvmStatic fun getFixedHP()            = FIXED_HP.value / 10f
    @JvmStatic fun getFixedAR()            = FIXED_AR.value / 10f
    @JvmStatic fun getFixedOD()            = FIXED_OD.value / 10f
    @JvmStatic fun getFixedSpeed()         = FIXED_SPEED.value / 100f
    @JvmStatic fun getCheckpoint()         = CHECKPOINT.value * 1000
    @JvmStatic fun getFFmpegLocation()     = FFmpegPath
    @JvmStatic fun getSkin()               = skin
    @JvmStatic fun isFullscreen()          = FULLSCREEN.value
    @JvmStatic fun isFPSCounterEnabled()   = SHOW_FPS.value
    @JvmStatic fun isHitLightingEnabled()  = SHOW_HIT_LIGHTING.value
    @JvmStatic fun isComboBurstEnabled()   = SHOW_COMBO_BURSTS.value
    @JvmStatic fun isDynamicBackgroundEnabled() = DYNAMIC_BACKGROUND.value
    @JvmStatic fun isPerfectHitBurstEnabled()   = SHOW_PERFECT_HIT.value
    @JvmStatic fun isFollowPointEnabled()  = SHOW_FOLLOW_POINTS.value
    @JvmStatic fun isDefaultPlayfieldForced()   = FORCE_DEFAULT_PLAYFIELD.value
    @JvmStatic fun isBeatmapVideoEnabled() = ENABLE_VIDEOS.value
    @JvmStatic fun isBeatmapSkinIgnored()  = IGNORE_BEATMAP_SKINS.value
    @JvmStatic fun isSkinCursorForced()    = FORCE_SKIN_CURSOR.value
    @JvmStatic fun isSliderSnaking()       = SNAKING_SLIDERS.value
    @JvmStatic fun isExperimentalSliderStyle()   = EXPERIMENTAL_SLIDERS.value
    @JvmStatic fun isExperimentalSliderCapsDrawn()  = EXPERIMENTAL_SLIDERS_CAPS.value
    @JvmStatic fun isExperimentalSliderShrinking()  = EXPERIMENTAL_SLIDERS_SHRINK.value
    @JvmStatic fun isExperimentalSliderMerging()    = EXPERIMENTAL_SLIDERS_MERGE.value
    @JvmStatic fun isHitErrorBarEnabled()  = SHOW_HIT_ERROR_BAR.value
    @JvmStatic fun alwaysShowKeyOverlay()  = ALWAYS_SHOW_KEY_OVERLAY.value
    @JvmStatic fun loadHDImages()          = LOAD_HD_IMAGES.value
    @JvmStatic fun isMouseWheelDisabled()  = DISABLE_MOUSE_WHEEL.value
    @JvmStatic fun isMouseDisabled()       = DISABLE_MOUSE_BUTTONS.value
    @JvmStatic fun isCursorDisabled()      = DISABLE_CURSOR.value
    @JvmStatic fun isLoadVerbose()         = LOAD_VERBOSE.value
    @JvmStatic fun isSoundDisabled()       = DISABLE_SOUNDS.value
    @JvmStatic fun isGameplaySoundDisabled() = DISABLE_GAMEPLAY_SOUNDS.value
    @JvmStatic fun useUnicodeMetadata()    = SHOW_UNICODE.value
    @JvmStatic fun isParallaxEnabled()     = PARALLAX.value
    @JvmStatic fun isThemeSongEnabled()    = ENABLE_THEME_SONG.value
    @JvmStatic fun isReplaySeekingEnabled() = REPLAY_SEEKING.value
    @JvmStatic fun isUpdaterDisabled()     = DISABLE_UPDATER.value
    @JvmStatic fun isWatchServiceEnabled() = ENABLE_WATCH_SERVICE.value

    @JvmStatic fun toggleFPSCounter()    { SHOW_FPS.toggle(TODO_CONTAINER) }
    @JvmStatic fun toggleMouseDisabled() {
        DISABLE_MOUSE_BUTTONS.value = !DISABLE_MOUSE_BUTTONS.value
        val msg = if (DISABLE_MOUSE_BUTTONS.value) "Mouse buttons are disabled." else "Mouse buttons are enabled."
        UI.getNotificationManager().sendBarNotification(msg)
    }

    // Hack: container reference needed for toggleFPSCounter; set on init
    private lateinit var TODO_CONTAINER: GameContainer

    @JvmStatic fun setMasterVolume(container: GameContainer, volume: Float) {
        if (volume in 0f..1f) {
            MASTER_VOLUME.value = (volume * 100).toInt()
            MusicController.setVolume(getMasterVolume() * getMusicVolume())
        }
    }

    @JvmStatic fun setNextFPS(container: GameContainer) {
        val next = (targetFPSindex + 1) % (targetFPS.size - 1)  // skip Unlimited
        TARGET_FPS.selectItem(next, container)
        UI.getNotificationManager().sendBarNotification("Frame limiter: ${TARGET_FPS.getValueString()}")
    }

    @JvmStatic fun setCheckpoint(timeSec: Int): Boolean {
        if (timeSec !in 0 until 3600) return false
        CHECKPOINT.value = timeSec; return true
    }

    // ─── Keys ─────────────────────────────────────────────────────────────────

    @JvmStatic fun getGameKeyLeft()  = if (keyLeft  == Keyboard.KEY_NONE) { setGameKeyLeft(Input.KEY_Z);  keyLeft  } else keyLeft
    @JvmStatic fun getGameKeyRight() = if (keyRight == Keyboard.KEY_NONE) { setGameKeyRight(Input.KEY_X); keyRight } else keyRight

    @JvmStatic fun setGameKeyLeft(key: Int): Boolean {
        if (key == keyRight && key != Keyboard.KEY_NONE || !isValidGameKey(key)) return false
        keyLeft = key; return true
    }
    @JvmStatic fun setGameKeyRight(key: Int): Boolean {
        if (key == keyLeft && key != Keyboard.KEY_NONE || !isValidGameKey(key)) return false
        keyRight = key; return true
    }
    private fun isValidGameKey(key: Int) = key !in setOf(
        Keyboard.KEY_ESCAPE, Keyboard.KEY_SPACE, Keyboard.KEY_UP, Keyboard.KEY_DOWN,
        Keyboard.KEY_F7, Keyboard.KEY_F10, Keyboard.KEY_F12,
    )

    // ─── Directories ──────────────────────────────────────────────────────────

    @JvmStatic fun getBeatmapDir(): File {
        beatmapDir?.takeIf { it.isDirectory }?.let { return it }
        osuInstallDir()?.let { File(it, DEFAULT_BEATMAP_DIR.name).takeIf { d -> d.isDirectory }?.let { d -> return d.also { beatmapDir = it } } }
        return DEFAULT_BEATMAP_DIR.also { it.mkdirs(); beatmapDir = it }
    }
    @JvmStatic fun getImportDir(): File =
        importDir?.takeIf { it.isDirectory } ?: File(DATA_DIR, "Import/").also { it.mkdirs(); importDir = it }
    @JvmStatic fun getScreenshotDir(): File =
        screenshotDir?.takeIf { it.isDirectory } ?: File(DATA_DIR, "Screenshots/").also { screenshotDir = it }
    @JvmStatic fun getReplayDir(): File =
        replayDir?.takeIf { it.isDirectory } ?: File(DATA_DIR, "Replays/").also { replayDir = it }
    @JvmStatic fun getSkinRootDir(): File {
        skinRootDir?.takeIf { it.isDirectory }?.let { return it }
        osuInstallDir()?.let { File(it, DEFAULT_SKIN_DIR.name).takeIf { d -> d.isDirectory }?.let { d -> return d.also { skinRootDir = it } } }
        return DEFAULT_SKIN_DIR.also { it.mkdirs(); skinRootDir = it }
    }
    @JvmStatic fun getSkinDir(): File? = File(getSkinRootDir(), skinName).takeIf { it.isDirectory }

    // ─── Skin ─────────────────────────────────────────────────────────────────

    @JvmStatic fun loadSkin() {
        val dir = getSkinDir()
        if (dir == null) skinName = Skin.DEFAULT_SKIN_NAME
        ResourceLoader.removeAllResourceLocations()
        skin = if (dir == null) Skin(null) else SkinLoader.loadSkin(dir).also {
            ResourceLoader.addResourceLocation(FileSystemLocation(dir))
        }
        ResourceLoader.addResourceLocation(ClasspathLocation())
        ResourceLoader.addResourceLocation(FileSystemLocation(File(".")))
        ResourceLoader.addResourceLocation(FileSystemLocation(File("./res/")))
    }

    // ─── Theme beatmap ────────────────────────────────────────────────────────

    @JvmStatic fun getThemeBeatmap(): Beatmap? {
        val tokens = themeString.split(",")
        if (tokens.size != 4) return null
        return Beatmap(null).also {
            it.audioFilename  = File(tokens[0])
            it.title          = tokens[1]
            it.artist         = tokens[2]
            it.endTime        = tokens[3].toIntOrNull() ?: return null
            it.timingPoints   = ArrayList<TimingPoint>(1).also { tp ->
                runCatching { tp.add(TimingPoint(themeTimingPoint)) }.onFailure { return null }
            }
        }
    }

    // ─── Display mode ─────────────────────────────────────────────────────────

    @JvmStatic fun setDisplayMode(app: Container) {
        val sw = app.screenWidth; val sh = app.screenHeight
        var fs = isFullscreen()
        if (sw < resolution.width || sh < resolution.height) resolution = Resolution.RES_800_600
        if (fs && !resolution.hasFullscreenMode()) fs = false
        try { app.setDisplayMode(resolution.width, resolution.height, fs) }
        catch (e: SlickException) { Log.error("Failed to set display mode.", e) }
        if (!fs) System.setProperty(
            "org.lwjgl.opengl.Window.undecorated",
            (sw == resolution.width && sh == resolution.height).toString()
        )
    }

    // ─── Config file I/O ──────────────────────────────────────────────────────

    @JvmStatic fun parseOptions() {
        if (!OPTIONS_FILE.isFile) { saveOptions(); return }
        OPTIONS_FILE.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.length < 2 || trimmed.startsWith("//")) return@forEachLine
            val idx = trimmed.indexOf('=').takeIf { it != -1 } ?: return@forEachLine
            val key = trimmed.substring(0, idx).trim()
            val value = trimmed.substring(idx + 1).trim()
            runCatching { optionByDisplayName[key]?.read(value) }
                .onFailure { Log.warn("Failed to read option '$key': ${it.message}") }
        }
    }

    @JvmStatic fun saveOptions() {
        val date = SimpleDateFormat("EEEE, MMMM dd, yyyy").format(Date())
        OPTIONS_FILE.bufferedWriter().use { w ->
            w.write("# ${OpsuConstants.PROJECT_NAME} configuration\n")
            w.write("# last updated on $date\n\n")
            ALL_OPTIONS.forEach { opt ->
                w.write("${opt.displayName} = ${opt.write()}\n")
            }
        }
    }

    // ─── XDG / helpers ───────────────────────────────────────────────────────

    private fun checkXDGFlag(): Boolean {
        val jar = Utils.getJarFile() ?: return false
        return jar.manifest?.mainAttributes?.getValue("Use-XDG")?.equals("true", ignoreCase = true) == true
    }

    private fun xdgBase(env: String, fallback: String): File {
        val working = if (Utils.isJarRunning()) Utils.getRunningDirectory()?.parentFile else Utils.getWorkingDirectory()
            ?: File("./")
        if (!USE_XDG) return working!!
        val os = System.getProperty("os.name").lowercase()
        if ("nix" !in os && "nux" !in os && "aix" !in os) return working!!
        val root = System.getenv(env) ?: "${System.getProperty("user.home")}/$fallback"
        return File(root, "opsu").also { it.mkdirs() }
    }

    private fun osuInstallDir(): File? {
        if (!System.getProperty("os.name").startsWith("Win")) return null
        return runCatching {
            val value = com.sun.jna.platform.win32.Advapi32Util
                .registryGetStringValue(com.sun.jna.platform.win32.WinReg.HKEY_CLASSES_ROOT, "osu\\DefaultIcon", null)
            val m = Regex(""""(.+)\\[^\\/]+\.exe"""").find(value) ?: return null
            File(m.groupValues[1]).takeIf { it.isDirectory }
        }.getOrNull()
    }
}
