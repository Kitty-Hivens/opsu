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

package itdelatrisu.opsu.user

import kotlin.math.pow

class User(
    private var name: String,
    private var icon: Int,
) : Comparable<User> {

    var score: Long = 0L; private set
    var accuracy: Double = 0.0; private set
    var passedPlays: Int = 0; private set
    var totalPlays: Int = 0; private set
    var level: Int = 1; private set
    var nextLevelProgress: Double = 0.0; private set

    constructor(name: String, score: Long, accuracy: Double, playsPassed: Int, playsTotal: Int, icon: Int) : this(name, icon) {
        this.score       = score
        this.accuracy    = accuracy
        this.passedPlays = playsPassed
        this.totalPlays  = playsTotal
        calculateLevel()
    }

    fun getName()    = name
    fun getScore()   = score
    fun getAccuracy()= accuracy
    fun getPassedPlays() = passedPlays
    fun getTotalPlays()  = totalPlays
    fun getIconId()  = icon
    fun getLevel()   = level
    fun getNextLevelProgress() = nextLevelProgress

    fun setName(name: String) { this.name = name }
    fun setIconId(id: Int)    { this.icon = id }

    fun add(score: Long, accuracy: Double) {
        this.score     += score
        this.accuracy   = (this.accuracy * passedPlays + accuracy) / (passedPlays + 1)
        passedPlays++
        totalPlays++
        calculateLevel()
    }

    fun add(score: Long) {
        this.score += score
        totalPlays++
        calculateLevel()
    }

    private fun calculateLevel() {
        if (score == 0L) { level = 1; nextLevelProgress = 0.0; return }
        var l = 1
        while (score >= scoreForLevel(l)) l++
        l--
        level = l
        val base = scoreForLevel(l)
        nextLevelProgress = (score - base).toDouble() / (scoreForLevel(l + 1) - base)
    }

    override fun compareTo(other: User) = name.compareTo(other.name, ignoreCase = true)

    companion object {
        fun scoreForLevel(level: Int): Long = when {
            level <= 1   -> 1L
            level <= 100 -> (5000.0 / 3 * (4 * level.toDouble().pow(3.0) - 3 * level.toDouble().pow(2.0) - level) + 1.25 * 1.8.pow(
                level - 60.0
            )).toLong()
            else         -> 26_931_190_829L + 100_000_000_000L * (level - 100)
        }
    }
}
