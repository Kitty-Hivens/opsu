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
