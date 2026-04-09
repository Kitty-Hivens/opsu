package itdelatrisu.opsu.db

import itdelatrisu.opsu.options.Options
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection

object DBController {

    fun init() {
        // Single shared connection per database file (WAL mode handles concurrency)
        connect(Options.BEATMAP_DB.path)
        BeatmapDB.init()

        connect(Options.SCORE_DB.path)
        ScoreDB.init()
    }

    private fun connect(path: String) {
        Database.connect(
            url      = "jdbc:sqlite:$path",
            driver   = "org.sqlite.JDBC",
        )
        // SQLite works best with TRANSACTION_SERIALIZABLE
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
    }

    /** No-op: Exposed closes connections automatically via transactions. */
    fun closeConnections() = Unit
}
