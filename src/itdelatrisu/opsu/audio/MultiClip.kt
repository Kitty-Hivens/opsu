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

import itdelatrisu.opsu.ErrorHandler
import java.io.IOException
import javax.sound.sampled.*

class MultiClip(val name: String, audioIn: AudioInputStream?) {

	private var clips = ArrayDeque<Clip>()
	private val format: AudioFormat?
	private var audioData: ByteArray?

	init {
		if (audioIn != null) {
			format = audioIn.format
			val allBufs = ArrayDeque<ByteArray>()
			var totalRead: Int
			var hasData = true
			while (hasData) {
				totalRead = 0
				val tbuf = ByteArray(BUFFER_SIZE)
				while (totalRead < tbuf.size) {
					val read = audioIn.read(tbuf, totalRead, tbuf.size - totalRead)
					if (read < 0) { hasData = false; break }
					totalRead += read
				}
				allBufs.add(tbuf)
			}
			audioData = ByteArray((allBufs.size - 1) * BUFFER_SIZE + totalRead)
			allBufs.forEachIndexed { cnt, tbuf ->
				val size = if (cnt == allBufs.size - 1) totalRead else BUFFER_SIZE
				System.arraycopy(tbuf, 0, audioData!!, BUFFER_SIZE * cnt, size)
			}
		} else {
			format = null
			audioData = null
		}
		getClip()
		ALL_MULTICLIPS.add(this)
	}

	@Throws(LineUnavailableException::class)
	fun start(volume: Float, listener: LineListener?) {
		val clip = getClip() ?: return
		if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			val gain = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
			val dB = (Math.log(volume.toDouble()) / Math.log(10.0) * 20.0).toFloat()
			gain.value = dB.coerceIn(gain.minimum, gain.maximum)
		} else if (clip.isControlSupported(FloatControl.Type.VOLUME)) {
			val vol = clip.getControl(FloatControl.Type.VOLUME) as FloatControl
			val amplitude = Math.sqrt(volume.toDouble()).toFloat() * vol.maximum
			vol.value = amplitude.coerceIn(vol.minimum, vol.maximum)
		}
		listener?.let { clip.addLineListener(it) }
		clip.framePosition = 0
		clip.start()
	}

	fun stop() {
		try {
			val clip = getClip() ?: return
			if (clip.isActive) clip.stop()
		} catch (e: LineUnavailableException) {}
	}

	@Throws(LineUnavailableException::class)
	private fun getClip(): Clip? {
		if (closingThreads > 0) return null

		clips.firstOrNull { !it.isRunning && !it.isActive }?.let {
			clips.remove(it); clips.add(it); return it
		}

		val c: Clip
		if (extraClips >= MAX_CLIPS) {
			if (clips.isEmpty()) return null
			c = clips.removeFirst()
			c.stop()
			clips.add(c)
		} else {
			val info = DataLine.Info(Clip::class.java, format)
			c = AudioSystem.getLine(info) as Clip
			if (format != null && !c.isOpen)
				c.open(format, audioData, 0, audioData!!.size)
			if (c.javaClass.simpleName == "PulseAudioClip")
				c.addLineListener(PulseAudioFixerListener(c))
			clips.add(c)
			if (clips.size != 1) extraClips++
		}
		return c
	}

	fun destroy() {
		if (clips.isNotEmpty()) {
			clips.forEach { it.stop(); it.flush(); it.close() }
			extraClips -= clips.size - 1
			clips.clear()
		}
		audioData = null
		try {
			// audioIn already consumed, nothing to close here
		} catch (e: IOException) {
			ErrorHandler.error("Could not close AudioInputStream for MultiClip $name.", e, true)
		}
	}

	companion object {
		private const val MAX_CLIPS = 20
		private const val BUFFER_SIZE = 0x1000

		private var extraClips = 0
		private var closingThreads = 0

		val ALL_MULTICLIPS = mutableListOf<MultiClip>()

		@JvmStatic
		fun destroyExtraClips() {
			if (extraClips == 0) return
				val toClose = mutableListOf<Clip>()
			for (mc in ALL_MULTICLIPS) {
				val iter = mc.clips.iterator()
				while (iter.hasNext()) {
					val c = iter.next()
					if (mc.clips.size > 1) { iter.remove(); toClose.add(c) }
				}
			}
			Thread {
				closingThreads++
				toClose.forEach { it.stop(); it.flush(); it.close() }
				closingThreads--
			}.start()
			extraClips = 0
		}
	}
}
