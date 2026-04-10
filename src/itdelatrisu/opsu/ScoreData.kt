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

package itdelatrisu.opsu

import itdelatrisu.opsu.GameData.Grade
import java.sql.ResultSet
import java.text.SimpleDateFormat
import java.util.Date

class ScoreData : Comparable<ScoreData> {

    var timestamp: Long = 0
    var MID: Int = 0
    var MSID: Int = 0
    var title: String = ""
    var artist: String = ""
    var creator: String = ""
    var version: String = ""
    var hit300: Int = 0
    var hit100: Int = 0
    var hit50: Int = 0
    var geki: Int = 0
    var katu: Int = 0
    var miss: Int = 0
    var score: Long = 0
    var combo: Int = 0
    var perfect: Boolean = false
    var mods: Int = 0
    var replayString: String? = null
    var playerName: String? = null

    private var timeSince: String? = null
    private var grade: Grade? = null
    private var scorePercent: Float = -1f
    private var timeString: String? = null
    private var tooltip: String? = null

    constructor()

    constructor(rs: ResultSet) {
        timestamp    = rs.getLong(1)
        MID          = rs.getInt(2)
        MSID         = rs.getInt(3)
        title        = rs.getString(4)
        artist       = rs.getString(5)
        creator      = rs.getString(6)
        version      = rs.getString(7)
        hit300       = rs.getInt(8)
        hit100       = rs.getInt(9)
        hit50        = rs.getInt(10)
        geki         = rs.getInt(11)
        katu         = rs.getInt(12)
        miss         = rs.getInt(13)
        score        = rs.getLong(14)
        combo        = rs.getInt(15)
        perfect      = rs.getBoolean(16)
        mods         = rs.getInt(17)
        replayString = rs.getString(18)
        playerName   = rs.getString(19)
    }

    fun getTimeString(): String {
        if (timeString == null)
            timeString = SimpleDateFormat("M/d/yyyy h:mm:ss a").format(Date(timestamp * 1000L))
        return timeString!!
    }

    private fun getScorePercent(): Float {
        if (scorePercent < 0f)
            scorePercent = GameData.getScorePercent(hit300, hit100, hit50, miss)
        return scorePercent
    }

    fun getGrade(): Grade {
        if (grade == null)
            grade = GameData.getGrade(
                hit300, hit100, hit50, miss,
                (mods and GameMod.HIDDEN.getBit()) > 0 || (mods and GameMod.FLASHLIGHT.getBit()) > 0
            )
        return grade!!
    }

    fun getTimeSince(): String? {
        if (timeSince == null) {
            val seconds = System.currentTimeMillis() / 1000L - timestamp
            timeSince = when {
                seconds < 60    -> "${seconds}s"
                seconds < 3600  -> "${seconds / 60}m"
                seconds < 86400 -> "${seconds / 3600}h"
                else            -> ""
            }
        }
        return timeSince!!.ifEmpty { null }
    }

    fun getTooltipString(): String {
        if (tooltip == null)
            tooltip = "Achieved on ${getTimeString()}\n" +
                    "300:$hit300 100:$hit100 50:$hit50 Miss:$miss\n" +
                    "Accuracy: ${"%.2f".format(getScorePercent())}%%\n" +
                    "Mods: ${GameMod.getModString(mods)}"
        return tooltip!!
    }

    // TODO: LibGDX — реализовать draw()
    // TODO: LibGDX — реализовать drawSmall()
    // TODO: LibGDX — реализовать init(), buttonContains(), areaContains(), drawScrollbar(), clipToArea()

    override fun compareTo(other: ScoreData): Int {
        return if (score != other.score)
            score.compareTo(other.score)
        else
            timestamp.compareTo(other.timestamp)
    }

    override fun toString(): String =
        "${getTimeString()} | ID: ($MID, $MSID) | $artist - $title [$version] (by $creator) | " +
                "Hits: ($hit300, $hit100, $hit50, $geki, $katu, $miss) | " +
                "Score: $score (${combo}combo${if (perfect) ", FC" else ""}) | " +
                "Mods: ${GameMod.getModString(mods)}"

    companion object {
        // TODO: LibGDX — статические поля для координат кнопок
        @JvmStatic var MAX_SCORE_BUTTONS = 7
        @JvmStatic fun getButtonOffset(): Float = 0f  // TODO: LibGDX
    }
}
