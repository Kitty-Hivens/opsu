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
package itdelatrisu.opsu.audio

class HitSound private constructor(private val filename: String) : SoundController.SoundComponent {

	private val clips = mutableMapOf<SampleSet, MultiClip>()

	fun getFileName() = filename

	override fun getClip(): MultiClip? =
	if (currentSampleSet != null) clips[currentSampleSet] else null

	fun getClip(s: SampleSet): MultiClip? = clips[s]

	fun setClip(s: SampleSet, clip: MultiClip) { clips[s] = clip }

	enum class SampleSet(val sampleName: String, val index: Int) {
		NORMAL("normal", 1),
		SOFT  ("soft",   2),
		DRUM  ("drum",   3);

		companion object {
			@JvmField val SIZE = values().size
		}
	}

	companion object {
		@JvmField val CLAP         = HitSound("hitclap")
		@JvmField val FINISH       = HitSound("hitfinish")
		@JvmField val NORMAL       = HitSound("hitnormal")
		@JvmField val WHISTLE      = HitSound("hitwhistle")
		@JvmField val SLIDERSLIDE  = HitSound("sliderslide")
		@JvmField val SLIDERTICK   = HitSound("slidertick")
		@JvmField val SLIDERWHISTLE= HitSound("sliderwhistle")

		@JvmField val SIZE = 7

		private var currentSampleSet: SampleSet? = null
		private var currentDefaultSampleSet: SampleSet = SampleSet.NORMAL

		@JvmStatic fun setDefaultSampleSet(sampleSet: String) {
			currentDefaultSampleSet = SampleSet.values()
				.firstOrNull { it.sampleName.equals(sampleSet, ignoreCase = true) }
                ?: SampleSet.NORMAL
		}

		@JvmStatic fun setDefaultSampleSet(sampleType: Byte) {
			currentDefaultSampleSet = SampleSet.values()
				.firstOrNull { it.index == sampleType.toInt() }
                ?: SampleSet.NORMAL
		}

		@JvmStatic fun setSampleSet(sampleType: Byte) {
			currentSampleSet = SampleSet.values()
				.firstOrNull { it.index == sampleType.toInt() }
                ?: currentDefaultSampleSet
		}
	}
}
