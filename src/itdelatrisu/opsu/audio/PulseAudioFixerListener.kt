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
