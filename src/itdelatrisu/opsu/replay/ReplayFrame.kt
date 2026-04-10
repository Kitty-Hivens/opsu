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

package itdelatrisu.opsu.replay

import itdelatrisu.opsu.beatmap.HitObject

class ReplayFrame(
    private var timeDiff: Int,
    val time: Int,
    val x: Float,
    val y: Float,
    val keys: Int,
) {
    companion object {
        const val KEY_NONE = 0
        const val KEY_M1   = (1 shl 0)
        const val KEY_M2   = (1 shl 1)
        const val KEY_K1   = (1 shl 2) or (1 shl 0)
        const val KEY_K2   = (1 shl 3) or (1 shl 1)

        fun getStartFrame(t: Int) = ReplayFrame(t, t, 256f, -500f, 0)
    }

    fun getTimeDiff() = timeDiff
    fun setTimeDiff(diff: Int) { timeDiff = diff }

    fun getScaledX() = (x * HitObject.xMultiplier + HitObject.xOffset).toInt()
    fun getScaledY() = (y * HitObject.yMultiplier + HitObject.yOffset).toInt()

    fun isKeyPressed() = keys != KEY_NONE

    override fun toString() = "($time, [${"%.2f".format(x)}, ${"%.2f".format(y)}], $keys)"
}
