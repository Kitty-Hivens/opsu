package itdelatrisu.opsu.replay

import itdelatrisu.opsu.ScoreData
import itdelatrisu.opsu.beatmap.Beatmap
import itdelatrisu.opsu.io.OsuReader
import itdelatrisu.opsu.io.OsuWriter
import itdelatrisu.opsu.options.Options
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAInputStream
import org.tukaani.xz.LZMAOutputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.util.Date
import org.newdawn.slick.util.Log

class Replay(private val file: File? = null) {

	private var scoreData: ScoreData? = null

	var loaded = false
	var mode: Byte = 0
	var version: Int = 0
	var beatmapHash: String = ""
	var playerName: String = ""
	var replayHash: String? = null
	var hit300: Short = 0
	var hit100: Short = 0
	var hit50: Short = 0
	var geki: Short = 0
	var katu: Short = 0
	var miss: Short = 0
	var score: Int = 0
	var combo: Short = 0
	var perfect: Boolean = false
	var mods: Int = 0
	var lifeFrames: Array<LifeFrame>? = null
	var timestamp: Date? = null
	var replayLength: Int = 0
	var frames: Array<ReplayFrame>? = null
	var seed: Int = 0

	companion object {
		private const val SEED_STRING = "-12345"
	}

	@Throws(IOException::class)
	fun load() {
		if (loaded) return
			val f = file ?: throw IOException("No file associated with this replay")
		OsuReader(f).use { reader ->
			loadHeader(reader)
			loadData(reader)
		}
		loaded = true
	}

	@Throws(IOException::class)
	fun loadHeader() {
		val f = file ?: throw IOException("No file associated with this replay")
		OsuReader(f).use { loadHeader(it) }
	}

	private fun loadHeader(reader: OsuReader) {
		mode        = reader.readByte()
		version     = reader.readInt()
		beatmapHash = reader.readString()
		playerName  = reader.readString()
		replayHash  = reader.readString()
		hit300      = reader.readShort()
		hit100      = reader.readShort()
		hit50       = reader.readShort()
		geki        = reader.readShort()
		katu        = reader.readShort()
		miss        = reader.readShort()
		score       = reader.readInt()
		combo       = reader.readShort()
		perfect     = reader.readBoolean()
		mods        = reader.readInt()
	}

	private fun loadData(reader: OsuReader) {
		// life frames
		val lifeData = reader.readString().split(",")
		lifeFrames = lifeData.mapNotNull { frame ->
			val tokens = frame.split("|")
			if (tokens.size < 2) return@mapNotNull null
			runCatching {
				LifeFrame(tokens[0].toInt(), tokens[1].toFloat())
			}.onFailure {
				Log.warn("Failed to load life frame: '$frame'", it)
			}.getOrNull()
		}.toTypedArray()

		timestamp    = reader.readDate()
		replayLength = reader.readInt()

		if (replayLength > 0) {
			val lzma = LZMAInputStream(reader.getInputStream())
			val raw = lzma.readBytes()
			lzma.close()
			val replayFrames = String(raw).split(",")
			var lastTime = 0
			frames = replayFrames.mapNotNull { frame ->
				if (frame.isEmpty()) return@mapNotNull null
				val tokens = frame.split("|")
				if (tokens.size < 4) return@mapNotNull null
				runCatching {
					if (tokens[0] == SEED_STRING) {
						seed = tokens[3].toInt()
						return@mapNotNull null
					}
					val timeDiff = tokens[0].toInt()
					val time = timeDiff + lastTime
					val x = tokens[1].toFloat()
					val y = tokens[2].toFloat()
					val keys = tokens[3].toInt()
					lastTime = time
					ReplayFrame(timeDiff, time, x, y, keys)
				}.onFailure {
					Log.warn("Failed to parse frame: '$frame'", it)
				}.getOrNull()
			}.toTypedArray()
		}
	}

	fun getScoreData(beatmap: Beatmap): ScoreData {
		scoreData?.let { return it }
		return ScoreData().also { sd ->
			sd.timestamp   = (file?.lastModified() ?: 0L) / 1000L
			sd.MID         = beatmap.beatmapID
			sd.MSID        = beatmap.beatmapSetID
			sd.title       = beatmap.title
			sd.artist      = beatmap.artist
			sd.creator     = beatmap.creator
			sd.version     = beatmap.version
			sd.hit300      = hit300.toInt()
			sd.hit100      = hit100.toInt()
			sd.hit50       = hit50.toInt()
			sd.geki        = geki.toInt()
			sd.katu        = katu.toInt()
			sd.miss        = miss.toInt()
			sd.score       = score.toLong()
			sd.combo       = combo.toInt()
			sd.perfect     = perfect
			sd.mods        = mods
			sd.replayString = getReplayFilename()
			sd.playerName  = playerName
			scoreData = sd
		}
	}

	fun save() {
		val replayDir = Options.getReplayDir()
		if (!replayDir.isDirectory && !replayDir.mkdir()) {
			Log.error("Failed to create replay directory.")
			return
		}

		val outFile = File(replayDir, "${getReplayFilename()}.osr")
		Thread {
			try {
				BufferedOutputStream(FileOutputStream(outFile)).use { out ->
					val writer = OsuWriter(out)
					writer.write(mode)
					writer.write(version)
					writer.write(beatmapHash)
					writer.write(playerName)
					writer.write(replayHash ?: "")
					writer.write(hit300)
					writer.write(hit100)
					writer.write(hit50)
					writer.write(geki)
					writer.write(katu)
					writer.write(miss)
					writer.write(score)
					writer.write(combo)
					writer.write(perfect)
					writer.write(mods)

					// life data
					val nf = DecimalFormat("##.##")
					val lifeStr = buildString {
						var lastFrameTime = 0
						lifeFrames?.forEachIndexed { i, frame ->
							if (i > 0 && frame.time - lastFrameTime < LifeFrame.SAMPLE_INTERVAL) return@forEachIndexed
								append("${frame.time}|${nf.format(frame.health)},")
							lastFrameTime = frame.time
						}
					}
					writer.write(lifeStr)
					writer.write(timestamp ?: Date())

					// LZMA replay data
					val framesLocal = frames
					if (!framesLocal.isNullOrEmpty()) {
						val nf2 = DecimalFormat("###.#####")
						val frameStr = buildString {
							framesLocal.forEach { frame ->
								append("${frame.getTimeDiff()}|${nf2.format(frame.x)}|${nf2.format(frame.y)}|${frame.keys},")
							}
							append("$SEED_STRING|0|0|$seed")
						}
						val bytes = StandardCharsets.US_ASCII.newEncoder()
							.encode(CharBuffer.wrap(frameStr)).array()
						val bout = ByteArrayOutputStream()
						LZMAOutputStream(bout, LZMA2Options(), bytes.size.toLong()).use { it.write(bytes) }
						val compressed = bout.toByteArray()
						writer.write(compressed.size)
						writer.write(compressed)
					} else {
						writer.write(0)
					}
					writer.close()
				}
			} catch (e: IOException) {
				Log.error("Could not save replay data.", e)
			}
		}.start()
	}

	fun getReplayFilename(): String? {
		val hash = replayHash ?: return null
		return "$hash-$hit300$hit100$hit50$geki$katu$miss"
	}

	override fun toString(): String = buildString {
		append("File: ${file?.name}\n")
		append("Mode: $mode\n")
		append("Version: $version\n")
		append("Beatmap hash: $beatmapHash\n")
		append("Player: $playerName\n")
		append("Hits: $hit300 $hit100 $hit50 $geki $katu $miss\n")
		append("Score: $score, Combo: $combo, Perfect: $perfect\n")
		append("Mods: $mods\n")
		append("Seed: $seed\n")
	}
}
