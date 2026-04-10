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

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent
import javax.sound.sampled.LineListener

class PulseAudioFixerListener(private val clip: Clip) : LineListener {

    override fun update(event: LineEvent) {
        if (event.type == LineEvent.Type.STOP) {
            executor.execute { clip.stop() }
        }
    }

    companion object {
        private val executor: ExecutorService = Executors.newCachedThreadPool()
    }
}
