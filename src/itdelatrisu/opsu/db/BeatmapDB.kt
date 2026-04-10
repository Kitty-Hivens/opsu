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
package itdelatrisu.opsu.db

import itdelatrisu.opsu.beatmap.Beatmap
import itdelatrisu.opsu.beatmap.BeatmapParser
import itdelatrisu.opsu.options.Options
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

// ─── Table definition ────────────────────────────────────────────────────────

object Beatmaps : Table("beatmaps") {
    val dir           = text("dir")
    val file          = text("file")
    val lastModified  = long("lastModified")
    val MID           = integer("MID")
    val MSID          = integer("MSID")
    val title         = text("title")
    val titleUnicode  = text("titleUnicode")
    val artist        = text("artist")
    val artistUnicode = text("artistUnicode")
    val creator       = text("creator")
    val version       = text("version")
    val source        = text("source")
    val tags          = text("tags")
    val circles       = integer("circles")
    val sliders       = integer("sliders")
    val spinners      = integer("spinners")
    val hp            = float("hp")
    val cs            = float("cs")
    val od            = float("od")
    val ar            = float("ar")
    val sliderMultiplier = float("sliderMultiplier")
    val sliderTickRate   = float("sliderTickRate")
    val bpmMin        = integer("bpmMin")
    val bpmMax        = integer("bpmMax")
    val endTime       = integer("endTime")
    val audioFile     = text("audioFile")
    val audioLeadIn   = integer("audioLeadIn")
    val previewTime   = integer("previewTime")
    val countdown     = byte("countdown")
    val sampleSet     = text("sampleSet")
    val stackLeniency = float("stackLeniency")
    val mode          = byte("mode")
    val letterboxInBreaks       = bool("letterboxInBreaks")
    val widescreenStoryboard    = bool("widescreenStoryboard")
    val epilepsyWarning         = bool("epilepsyWarning")
    val bg            = text("bg").nullable()
    val sliderBorder  = text("sliderBorder").nullable()
    val timingPoints  = text("timingPoints").nullable()
    val breaks        = text("breaks").nullable()
    val combo         = text("combo").nullable()
    val md5hash       = text("md5hash").nullable()
    val stars         = double("stars").default(-1.0)
    val dateAdded     = long("dateAdded").default(0)
    val favorite      = bool("favorite").default(false)
    val playCount     = integer("playCount").default(0)
    val lastPlayed    = long("lastPlayed").default(0)
    val localOffset   = integer("localOffset").default(0)
    val video         = text("video").nullable()
    val videoOffset   = integer("videoOffset").default(0)

    override val primaryKey = PrimaryKey(dir, file)
}

object BeatmapInfo : Table("info") {
    val key   = text("key").uniqueIndex()
    val value = text("value")
}

// ─── LastModified entry ──────────────────────────────────────────────────────

data class LastModifiedEntry(val lastModified: Long, val mode: Byte)

// ─── BeatmapDB ───────────────────────────────────────────────────────────────

object BeatmapDB {

    private const val DATABASE_VERSION = 20170221

    fun init() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Beatmaps, BeatmapInfo)
            exec("CREATE INDEX IF NOT EXISTS idx ON beatmaps (dir, file)")
            exec("PRAGMA locking_mode = EXCLUSIVE")
            exec("PRAGMA journal_mode = WAL")

            val storedVersion = BeatmapInfo
                .select(BeatmapInfo.value)
                .where { BeatmapInfo.key eq "version" }
                .firstOrNull()?.get(BeatmapInfo.value)?.toIntOrNull() ?: 0

            if (storedVersion < DATABASE_VERSION) {
                applyUpdates(storedVersion)
                BeatmapInfo.upsert { it[key] = "version"; it[value] = DATABASE_VERSION.toString() }
            }
        }
    }

    private fun applyUpdates(from: Int) {
        // schema is now managed by Exposed; historical migrations kept for
        // databases that existed before this rewrite
        if (from < 20161222) {
            runCatching { exec("ALTER TABLE beatmaps ADD COLUMN dateAdded INTEGER DEFAULT 0") }
            runCatching { exec("ALTER TABLE beatmaps ADD COLUMN favorite BOOLEAN DEFAULT 0") }
            runCatching { exec("ALTER TABLE beatmaps ADD COLUMN playCount INTEGER DEFAULT 0") }
            runCatching { exec("ALTER TABLE beatmaps ADD COLUMN lastPlayed INTEGER DEFAULT 0") }
        }
        if (from < 20161225)
            runCatching { exec("ALTER TABLE beatmaps ADD COLUMN localOffset INTEGER DEFAULT 0") }
        if (from < 20170128) {
            runCatching { exec("ALTER TABLE beatmaps ADD COLUMN video TEXT") }
            runCatching { exec("ALTER TABLE beatmaps ADD COLUMN videoOffset INTEGER DEFAULT 0") }
        }
        if (from < 20170221)
            exec("UPDATE beatmaps SET stars = -1")
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    fun insert(beatmap: Beatmap) = transaction {
        Beatmaps.upsert { it.from(beatmap) }
        updateCacheSize(1)
    }

    fun insert(batch: List<Beatmap>) = transaction {
        Beatmaps.batchUpsert(batch) { it.from(this) }
        updateCacheSize(batch.size)
    }

    fun delete(dir: String, file: String) = transaction {
        val count = Beatmaps.deleteWhere { Beatmaps.dir eq dir and (Beatmaps.file eq file) }
        updateCacheSize(-count)
    }

    fun delete(dir: String) = transaction {
        val count = Beatmaps.deleteWhere { Beatmaps.dir eq dir }
        updateCacheSize(-count)
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    fun load(beatmap: Beatmap, flag: Int) = transaction {
        Beatmaps
            .selectAll()
            .where { (Beatmaps.dir eq beatmap.file.parentFile.name) and (Beatmaps.file eq beatmap.file.name) }
            .firstOrNull()
            ?.applyTo(beatmap, flag)
    }

    fun load(batch: List<Beatmap>, flag: Int) = transaction {
        if (batch.isEmpty()) return@transaction
        val byPath = batch.associateBy { "${it.file.parentFile.name}/${it.file.name}" }
        Beatmaps.selectAll().forEach { row ->
            val path = "${row[Beatmaps.dir]}/${row[Beatmaps.file]}"
            byPath[path]?.let { row.applyTo(it, flag) }
        }
    }

    fun getLastModifiedMap(): Map<String, LastModifiedEntry> = transaction {
        Beatmaps
            .select(Beatmaps.dir, Beatmaps.file, Beatmaps.lastModified, Beatmaps.mode)
            .associate { row ->
                "${row[Beatmaps.dir]}/${row[Beatmaps.file]}" to
                    LastModifiedEntry(row[Beatmaps.lastModified], row[Beatmaps.mode])
            }
    }

    // ── Update helpers ────────────────────────────────────────────────────────

    fun setStars(beatmap: Beatmap) = transaction {
        Beatmaps.update({ Beatmaps.dir eq beatmap.file.parentFile.name and (Beatmaps.file eq beatmap.file.name) }) {
            it[stars] = beatmap.starRating
        }
    }

    fun updatePlayStatistics(beatmap: Beatmap) = transaction {
        Beatmaps.update({ Beatmaps.dir eq beatmap.file.parentFile.name and (Beatmaps.file eq beatmap.file.name) }) {
            it[playCount] = beatmap.playCount
            it[lastPlayed] = beatmap.lastPlayed
        }
    }

    fun updateFavoriteStatus(beatmap: Beatmap) = transaction {
        Beatmaps.update({ Beatmaps.dir eq beatmap.file.parentFile.name and (Beatmaps.file eq beatmap.file.name) }) {
            it[favorite] = beatmap.favorite
        }
    }

    fun updateLocalOffset(beatmap: Beatmap) = transaction {
        Beatmaps.update({ Beatmaps.dir eq beatmap.file.parentFile.name and (Beatmaps.file eq beatmap.file.name) }) {
            it[localOffset] = beatmap.localMusicOffset
        }
    }

    fun clearDatabase() = transaction {
        SchemaUtils.drop(Beatmaps)
        SchemaUtils.create(Beatmaps)
        BeatmapInfo.upsert { it[key] = "size"; it[value] = "0" }
    }

    // ── Cache size ────────────────────────────────────────────────────────────

    private fun updateCacheSize(delta: Int) {
        val current = BeatmapInfo
            .select(BeatmapInfo.value)
            .where { BeatmapInfo.key eq "size" }
            .firstOrNull()?.get(BeatmapInfo.value)?.toIntOrNull() ?: 0
        BeatmapInfo.upsert { it[key] = "size"; it[value] = (current + delta).coerceAtLeast(0).toString() }
    }
}

// ─── Extension: Beatmap → Row ────────────────────────────────────────────────

private fun UpdateBuilder<*>.from(b: Beatmap) {
    val dirFile = b.file.parentFile
    this[Beatmaps.dir]           = dirFile.name
    this[Beatmaps.file]          = b.file.name
    this[Beatmaps.lastModified]  = b.file.lastModified()
    this[Beatmaps.MID]           = b.beatmapID
    this[Beatmaps.MSID]          = b.beatmapSetID
    this[Beatmaps.title]         = b.title
    this[Beatmaps.titleUnicode]  = b.titleUnicode
    this[Beatmaps.artist]        = b.artist
    this[Beatmaps.artistUnicode] = b.artistUnicode
    this[Beatmaps.creator]       = b.creator
    this[Beatmaps.version]       = b.version
    this[Beatmaps.source]        = b.source
    this[Beatmaps.tags]          = b.tags
    this[Beatmaps.circles]       = b.hitObjectCircle
    this[Beatmaps.sliders]       = b.hitObjectSlider
    this[Beatmaps.spinners]      = b.hitObjectSpinner
    this[Beatmaps.hp]            = b.HPDrainRate
    this[Beatmaps.cs]            = b.circleSize
    this[Beatmaps.od]            = b.overallDifficulty
    this[Beatmaps.ar]            = b.approachRate
    this[Beatmaps.sliderMultiplier] = b.sliderMultiplier
    this[Beatmaps.sliderTickRate]   = b.sliderTickRate
    this[Beatmaps.bpmMin]        = b.bpmMin
    this[Beatmaps.bpmMax]        = b.bpmMax
    this[Beatmaps.endTime]       = b.endTime
    this[Beatmaps.audioFile]     = b.audioFilename.name
    this[Beatmaps.audioLeadIn]   = b.audioLeadIn
    this[Beatmaps.previewTime]   = b.previewTime
    this[Beatmaps.countdown]     = b.countdown
    this[Beatmaps.sampleSet]     = b.sampleSet
    this[Beatmaps.stackLeniency] = b.stackLeniency
    this[Beatmaps.mode]          = b.mode
    this[Beatmaps.letterboxInBreaks]    = b.letterboxInBreaks
    this[Beatmaps.widescreenStoryboard] = b.widescreenStoryboard
    this[Beatmaps.epilepsyWarning]      = b.epilepsyWarning
    this[Beatmaps.bg]            = b.bg?.name
    this[Beatmaps.sliderBorder]  = b.sliderBorderToString()
    this[Beatmaps.timingPoints]  = b.timingPointsToString()
    this[Beatmaps.breaks]        = b.breaksToString()
    this[Beatmaps.combo]         = b.comboToString()
    this[Beatmaps.md5hash]       = b.md5Hash
    this[Beatmaps.stars]         = b.starRating
    this[Beatmaps.dateAdded]     = b.dateAdded
    this[Beatmaps.favorite]      = b.favorite
    this[Beatmaps.playCount]     = b.playCount
    this[Beatmaps.lastPlayed]    = b.lastPlayed
    this[Beatmaps.localOffset]   = b.localMusicOffset
    this[Beatmaps.video]         = b.video?.name
    this[Beatmaps.videoOffset]   = b.videoOffset
}

// ─── Extension: Row → Beatmap ────────────────────────────────────────────────

private fun ResultRow.applyTo(b: Beatmap, flag: Int) {
    val dir = File(b.file.parent)
    if (flag and BeatmapDB.LOAD_NONARRAY != 0) {
        b.beatmapID        = this[Beatmaps.MID]
        b.beatmapSetID     = this[Beatmaps.MSID]
        b.title            = BeatmapParser.getDBString(this[Beatmaps.title])
        b.titleUnicode     = BeatmapParser.getDBString(this[Beatmaps.titleUnicode])
        b.artist           = BeatmapParser.getDBString(this[Beatmaps.artist])
        b.artistUnicode    = BeatmapParser.getDBString(this[Beatmaps.artistUnicode])
        b.creator          = BeatmapParser.getDBString(this[Beatmaps.creator])
        b.version          = BeatmapParser.getDBString(this[Beatmaps.version])
        b.source           = BeatmapParser.getDBString(this[Beatmaps.source])
        b.tags             = BeatmapParser.getDBString(this[Beatmaps.tags])
        b.hitObjectCircle  = this[Beatmaps.circles]
        b.hitObjectSlider  = this[Beatmaps.sliders]
        b.hitObjectSpinner = this[Beatmaps.spinners]
        b.HPDrainRate      = this[Beatmaps.hp]
        b.circleSize       = this[Beatmaps.cs]
        b.overallDifficulty = this[Beatmaps.od]
        b.approachRate     = this[Beatmaps.ar]
        b.sliderMultiplier = this[Beatmaps.sliderMultiplier]
        b.sliderTickRate   = this[Beatmaps.sliderTickRate]
        b.bpmMin           = this[Beatmaps.bpmMin]
        b.bpmMax           = this[Beatmaps.bpmMax]
        b.endTime          = this[Beatmaps.endTime]
        b.audioFilename    = File(dir, BeatmapParser.getDBString(this[Beatmaps.audioFile]))
        b.audioLeadIn      = this[Beatmaps.audioLeadIn]
        b.previewTime      = this[Beatmaps.previewTime]
        b.countdown        = this[Beatmaps.countdown]
        b.sampleSet        = BeatmapParser.getDBString(this[Beatmaps.sampleSet])
        b.stackLeniency    = this[Beatmaps.stackLeniency]
        b.mode             = this[Beatmaps.mode]
        b.letterboxInBreaks       = this[Beatmaps.letterboxInBreaks]
        b.widescreenStoryboard    = this[Beatmaps.widescreenStoryboard]
        b.epilepsyWarning         = this[Beatmaps.epilepsyWarning]
        b.bg               = this[Beatmaps.bg]?.let { File(dir, BeatmapParser.getDBString(it)) }
        b.sliderBorderFromString(this[Beatmaps.sliderBorder])
        b.md5Hash          = this[Beatmaps.md5hash]
        b.starRating       = this[Beatmaps.stars]
        b.dateAdded        = this[Beatmaps.dateAdded]
        b.favorite         = this[Beatmaps.favorite]
        b.playCount        = this[Beatmaps.playCount]
        b.lastPlayed       = this[Beatmaps.lastPlayed]
        b.localMusicOffset = this[Beatmaps.localOffset]
        b.video            = this[Beatmaps.video]?.let { File(dir, BeatmapParser.getDBString(it)) }
        b.videoOffset      = this[Beatmaps.videoOffset]
    }
    if (flag and BeatmapDB.LOAD_ARRAY != 0) {
        b.timingPointsFromString(this[Beatmaps.timingPoints])
        b.breaksFromString(this[Beatmaps.breaks])
        b.comboFromString(this[Beatmaps.combo])
    }
}

// ─── Constants (kept for call-site compatibility) ─────────────────────────────

@Suppress("unused")
object BeatmapDBFlags {
    const val LOAD_NONARRAY = 1
    const val LOAD_ARRAY    = 2
    const val LOAD_ALL      = 3
}
