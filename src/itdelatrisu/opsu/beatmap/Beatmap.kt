package itdelatrisu.opsu.beatmap

import itdelatrisu.opsu.GameMod
import java.io.File

/**
 * Data container for a single beatmap difficulty.
 * Mirrors the .osu file fields; all fields are public-var for parser convenience.
 */
class Beatmap(val file: File) : Comparable<Beatmap> {

	// -------------------------------------------------------------------------
	// [General]
	// -------------------------------------------------------------------------
	var audioFilename: File? = null
	var audioLeadIn: Int = 0
	var previewTime: Int = -1
	var countdown: Boolean = false
	var sampleSet: String = ""
	var stackLeniency: Float = 0f
	var mode: Byte = 0
	var letterboxInBreaks: Boolean = false
	var widescreenStoryboard: Boolean = false
	var epiphanyReadyMode: Boolean = false

	// -------------------------------------------------------------------------
	// [Metadata]
	// -------------------------------------------------------------------------
	var title: String = ""
	var titleUnicode: String? = null
	var artist: String = ""
	var artistUnicode: String? = null
	var creator: String = ""
	var version: String = ""          // difficulty name
	var source: String? = null
	var tags: String? = null
	var beatmapID: Int = 0
	var beatmapSetID: Int = -1

	// -------------------------------------------------------------------------
	// [Difficulty]
	// -------------------------------------------------------------------------
	var hpDrainRate: Float = 5f
	var circleSize: Float = 5f
	var overallDifficulty: Float = 5f
	var approachRate: Float = 5f
	var sliderMultiplier: Float = 1.4f
	var sliderTickRate: Float = 1f

	// -------------------------------------------------------------------------
	// Derived / cached
	// -------------------------------------------------------------------------
	var endTime: Int = 0             // last object end time (ms)
	var starRating: Float = 0f
	var bpmMin: Float = 0f
	var bpmMax: Float = 0f
	var ranked: Byte = 0
	var dateAdded: Long = 0L
	var lastPlayed: Long = 0L
	var playCount: Int = 0
	var localOffset: Int = 0

	// -------------------------------------------------------------------------
	// Hit object counts (populated by parser)
	// -------------------------------------------------------------------------
	var hitObjectCircle: Int = 0
	var hitObjectSlider: Int = 0
	var hitObjectSpinner: Int = 0

	// -------------------------------------------------------------------------
	// Timing points & hit objects (lazy-loaded)
	// -------------------------------------------------------------------------
	@Transient var timingPoints: ArrayList<TimingPoint>? = null
	@Transient var hitObjects: Array<HitObject>? = null
	@Transient var breaks: ArrayList<Int>? = null       // pairs: start,end,start,end...
	@Transient var combo: IntArray? = null              // combo colour overrides

	// -------------------------------------------------------------------------
	// Computed helpers
	// -------------------------------------------------------------------------

	/** Total duration in ms (end of last object – audio lead-in). */
	fun getDuration(): Int = endTime - audioLeadIn

	/** Total hit objects. */
	fun objectCount(): Int = hitObjectCircle + hitObjectSlider + hitObjectSpinner

	/** True if timing data is loaded in memory. */
	fun isLoaded(): Boolean = hitObjects != null

	/** Unloads parsed hit-object data to free memory. */
	fun unload() { hitObjects = null; timingPoints = null; breaks = null }

	/** BPM string shown in song selection. */
	fun getBpmString(): String = if (bpmMin == bpmMax) "%.0f".format(bpmMax) else "%.0f-%.0f".format(bpmMin, bpmMax)

	/** Star-rating string. */
	fun getStarRatingString(): String = "%.2f★".format(starRating)

	// -------------------------------------------------------------------------
	// Mod-adjusted difficulty helpers
	// -------------------------------------------------------------------------

	fun getHpDrainRate(mods: EnumSet<GameMod> = EnumSet.noneOf(GameMod::class.java)): Float = when {
		GameMod.EASY    in mods -> hpDrainRate / 2f
		GameMod.HARD_ROCK in mods -> minOf(hpDrainRate * 1.4f, 10f)
        else -> hpDrainRate
	}

	fun getCircleSize(mods: EnumSet<GameMod> = EnumSet.noneOf(GameMod::class.java)): Float = when {
		GameMod.EASY      in mods -> circleSize / 2f
		GameMod.HARD_ROCK in mods -> minOf(circleSize * 1.3f, 10f)
        else -> circleSize
	}

	fun getOverallDifficulty(mods: EnumSet<GameMod> = EnumSet.noneOf(GameMod::class.java)): Float = when {
		GameMod.EASY      in mods -> overallDifficulty / 2f
		GameMod.HARD_ROCK in mods -> minOf(overallDifficulty * 1.4f, 10f)
        else -> overallDifficulty
	}

	fun getApproachRate(mods: EnumSet<GameMod> = EnumSet.noneOf(GameMod::class.java)): Float = when {
		GameMod.EASY      in mods -> approachRate / 2f
		GameMod.HARD_ROCK in mods -> minOf(approachRate * 1.4f, 10f)
        else -> approachRate
	}

	// -------------------------------------------------------------------------
	// Comparable — sort by title then version
	// -------------------------------------------------------------------------

	override fun compareTo(other: Beatmap): Int {
		val t = title.compareTo(other.title, ignoreCase = true)
		return if (t != 0) t else starRating.compareTo(other.starRating)
	}

	override fun toString(): String = "$artist - $title [$version]"
}
