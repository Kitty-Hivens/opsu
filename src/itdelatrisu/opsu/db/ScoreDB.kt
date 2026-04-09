package itdelatrisu.opsu.db

import itdelatrisu.opsu.GameMod
import itdelatrisu.opsu.ScoreData
import itdelatrisu.opsu.beatmap.Beatmap
import itdelatrisu.opsu.user.User
import itdelatrisu.opsu.user.UserList
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

// ─── Table definitions ───────────────────────────────────────────────────────

object Scores : Table("scores") {
    val timestamp  = long("timestamp")
    val MID        = integer("MID")
    val MSID       = integer("MSID")
    val title      = text("title")
    val artist     = text("artist")
    val creator    = text("creator")
    val version    = text("version")
    val hit300     = integer("hit300")
    val hit100     = integer("hit100")
    val hit50      = integer("hit50")
    val geki       = integer("geki")
    val katu       = integer("katu")
    val miss       = integer("miss")
    val score      = long("score")
    val combo      = integer("combo")
    val perfect    = bool("perfect")
    val mods       = integer("mods")
    val replay     = text("replay").nullable()
    val playerName = text("playerName").nullable()

    override val primaryKey = PrimaryKey(timestamp)
}

object Users : Table("users") {
    val name         = text("name").uniqueIndex()
    val score        = long("score")
    val accuracy     = double("accuracy")
    val playsPassed  = integer("playsPassed")
    val playsTotal   = integer("playsTotal")
    val icon         = integer("icon")

    override val primaryKey = PrimaryKey(name)
}

object ScoreInfo : Table("info") {
    val key   = text("key").uniqueIndex()
    val value = text("value")
}

// ─── ScoreDB ─────────────────────────────────────────────────────────────────

object ScoreDB {

    private const val DATABASE_VERSION = 20170201

    fun init() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Scores, Users, ScoreInfo)
            exec("CREATE INDEX IF NOT EXISTS idx ON scores (MID, MSID, title, artist, creator, version)")

            val storedVersion = ScoreInfo
                .select(ScoreInfo.value)
                .where { ScoreInfo.key eq "version" }
                .firstOrNull()?.get(ScoreInfo.value)?.toIntOrNull() ?: 0

            if (storedVersion < DATABASE_VERSION) {
                applyUpdates(storedVersion)
                ScoreInfo.upsert { it[key] = "version"; it[value] = DATABASE_VERSION.toString() }
            }
        }
    }

    private fun applyUpdates(from: Int) {
        if (from < 20140311)
            runCatching { exec("ALTER TABLE scores ADD COLUMN replay TEXT") }
        if (from < 20150401)
            runCatching { exec("ALTER TABLE scores ADD COLUMN playerName TEXT") }
        if (from < 20170201)
            exec("UPDATE scores SET playerName = '${UserList.DEFAULT_USER_NAME}' WHERE playerName IS NULL")
    }

    // ── Score CRUD ────────────────────────────────────────────────────────────

    fun addScore(data: ScoreData) = transaction {
        Scores.insertIgnore { it.from(data) }
    }

    fun deleteScore(data: ScoreData) = transaction {
        Scores.deleteWhere {
            (timestamp eq data.timestamp) and
            (MID       eq data.MID)       and
            (MSID      eq data.MSID)      and
            (title     eq data.title)     and
            (artist    eq data.artist)    and
            (creator   eq data.creator)   and
            (version   eq data.version)   and
            (hit300    eq data.hit300)    and
            (hit100    eq data.hit100)    and
            (hit50     eq data.hit50)     and
            (geki      eq data.geki)      and
            (katu      eq data.katu)      and
            (miss      eq data.miss)      and
            (score     eq data.score)     and
            (combo     eq data.combo)     and
            (perfect   eq data.perfect)   and
            (mods      eq data.mods)
        }
    }

    fun deleteScore(beatmap: Beatmap) = transaction {
        Scores.deleteWhere {
            (MID     eq beatmap.beatmapID) and
            (title   eq beatmap.title)     and
            (artist  eq beatmap.artist)    and
            (creator eq beatmap.creator)   and
            (version eq beatmap.version)
        }
    }

    // ── Score queries ─────────────────────────────────────────────────────────

    fun getMapScores(beatmap: Beatmap): Array<ScoreData>? =
        getMapScoresExcluding(beatmap, null)

    fun getMapScoresExcluding(beatmap: Beatmap, exclude: String?): Array<ScoreData>? = transaction {
        Scores.selectAll()
            .where {
                (Scores.MID     eq beatmap.beatmapID) and
                (Scores.title   eq beatmap.title)     and
                (Scores.artist  eq beatmap.artist)    and
                (Scores.creator eq beatmap.creator)   and
                (Scores.version eq beatmap.version)
            }
            .mapNotNull { row ->
                val s = row.toScoreData()
                if (s.replayString != null && s.replayString == exclude) null else s
            }
            .sortedDescending()
            .toTypedArray()
    }

    fun getMapSetScores(beatmap: Beatmap): Map<String, Array<ScoreData>>? = transaction {
        Scores.selectAll()
            .where {
                (Scores.MSID    eq beatmap.beatmapSetID) and
                (Scores.title   eq beatmap.title)        and
                (Scores.artist  eq beatmap.artist)       and
                (Scores.creator eq beatmap.creator)
            }
            .orderBy(Scores.version to SortOrder.DESC)
            .groupBy { it[Scores.version] }
            .mapValues { (_, rows) ->
                rows.map { it.toScoreData() }.sortedDescending().toTypedArray()
            }
    }

    // ── User CRUD ─────────────────────────────────────────────────────────────

    fun getUsers(): List<User> = transaction {
        Users.selectAll().map { row ->
            User(
                row[Users.name],
                row[Users.score],
                row[Users.accuracy],
                row[Users.playsPassed],
                row[Users.playsTotal],
                row[Users.icon],
            )
        }
    }

    fun getCurrentUser(): String? = transaction {
        ScoreInfo
            .select(ScoreInfo.value)
            .where { ScoreInfo.key eq "user" }
            .firstOrNull()?.get(ScoreInfo.value)
    }

    fun setCurrentUser(name: String) = transaction {
        ScoreInfo.upsert { it[key] = "user"; it[value] = name }
    }

    fun updateUser(user: User) = transaction {
        Users.upsert {
            it[name]        = user.name
            it[score]       = user.score
            it[accuracy]    = user.accuracy
            it[playsPassed] = user.passedPlays
            it[playsTotal]  = user.totalPlays
            it[icon]        = user.iconId
        }
    }

    fun deleteUser(name: String) = transaction {
        Users.deleteWhere { Users.name eq name }
    }
}

// ─── Extensions ──────────────────────────────────────────────────────────────

private fun UpdateBuilder<*>.from(d: ScoreData) {
    this[Scores.timestamp]  = d.timestamp
    this[Scores.MID]        = d.MID
    this[Scores.MSID]       = d.MSID
    this[Scores.title]      = d.title
    this[Scores.artist]     = d.artist
    this[Scores.creator]    = d.creator
    this[Scores.version]    = d.version
    this[Scores.hit300]     = d.hit300
    this[Scores.hit100]     = d.hit100
    this[Scores.hit50]      = d.hit50
    this[Scores.geki]       = d.geki
    this[Scores.katu]       = d.katu
    this[Scores.miss]       = d.miss
    this[Scores.score]      = d.score
    this[Scores.combo]      = d.combo
    this[Scores.perfect]    = d.perfect
    this[Scores.mods]       = d.mods
    this[Scores.replay]     = d.replayString
    this[Scores.playerName] = d.playerName
}

private fun ResultRow.toScoreData() = ScoreData().also { s ->
    s.timestamp   = this[Scores.timestamp]
    s.MID         = this[Scores.MID]
    s.MSID        = this[Scores.MSID]
    s.title       = this[Scores.title]
    s.artist      = this[Scores.artist]
    s.creator     = this[Scores.creator]
    s.version     = this[Scores.version]
    s.hit300      = this[Scores.hit300]
    s.hit100      = this[Scores.hit100]
    s.hit50       = this[Scores.hit50]
    s.geki        = this[Scores.geki]
    s.katu        = this[Scores.katu]
    s.miss        = this[Scores.miss]
    s.score       = this[Scores.score]
    s.combo       = this[Scores.combo]
    s.perfect     = this[Scores.perfect]
    s.mods        = this[Scores.mods]
    s.replayString = this[Scores.replay]
    s.playerName  = this[Scores.playerName]
}
