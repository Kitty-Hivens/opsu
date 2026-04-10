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
package itdelatrisu.opsu.beatmap

import itdelatrisu.opsu.Utils
import org.newdawn.slick.util.Log

class TimingPoint(line: String) {
    var time: Int = 0;              private set
    var beatLength: Float = 0f;    private set
    private var velocity: Int = 0
    var meter: Int = 4;            private set
    var sampleType: Byte = 1;      private set
    var sampleTypeCustom: Byte = 0; private set
    var sampleVolume: Int = 100;   private set
    var isInherited: Boolean = false; private set
    var isKiaiTimeActive: Boolean = false; private set

    init {
        val t = line.split(",")
        try {
            time             = t[0].toFloat().toInt()
            meter            = t[2].toInt()
            sampleType       = t[3].toByte()
            sampleTypeCustom = t[4].toByte()
            sampleVolume     = t[5].toInt()
            if (t.size > 7) isKiaiTimeActive = Utils.parseBoolean(t[7])
        } catch (_: ArrayIndexOutOfBoundsException) {
            Log.debug("Error parsing timing point: '$line'")
        }
        val bl = t[1].toFloat()
        if (bl > 0) beatLength = bl else { velocity = bl.toInt(); isInherited = true }
    }

    fun getSliderMultiplier(): Float = Utils.clamp(-velocity, 10, 1000) / 100f
    fun getSampleVolume(): Float = sampleVolume / 100f

    override fun toString(): String {
        val kiai = if (isKiaiTimeActive) 1 else 0
        return if (isInherited)
            "$time,$velocity,$meter,${sampleType.toInt()},${sampleTypeCustom.toInt()},$sampleVolume,1,$kiai"
        else
            "$time,$beatLength,$meter,${sampleType.toInt()},${sampleTypeCustom.toInt()},$sampleVolume,0,$kiai"
    }
}
