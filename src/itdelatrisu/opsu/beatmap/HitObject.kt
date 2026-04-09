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

import itdelatrisu.opsu.GameMod
import itdelatrisu.opsu.objects.curves.CatmullCurve
import itdelatrisu.opsu.objects.curves.CircumscribedCircle
import itdelatrisu.opsu.objects.curves.Curve
import itdelatrisu.opsu.objects.curves.LinearBezier
import itdelatrisu.opsu.objects.curves.Vec2f
import java.text.DecimalFormat

/** Data type representing a parsed hit object. */
class HitObject(line: String) {
    companion object {
        // Hit object types (bits)
        const val TYPE_CIRCLE   = 1
        const val TYPE_SLIDER   = 2
        const val TYPE_NEWCOMBO = 4  // not an object
        const val TYPE_SPINNER  = 8

        // Hit sound types (bits)
        const val SOUND_NORMAL:  Byte = 0
        const val SOUND_WHISTLE: Byte = 2
        const val SOUND_FINISH:  Byte = 4
        const val SOUND_CLAP:    Byte = 8

        // Slider curve types (deprecated: only Beziers currently used)
        const val SLIDER_CATMULL:       Char = 'C'
        const val SLIDER_BEZIER:        Char = 'B'
        const val SLIDER_LINEAR:        Char = 'L'
        const val SLIDER_PERFECT_CURVE: Char = 'P'

        private const val MAX_X = 512
        private const val MAX_Y = 384

        @JvmStatic var xMultiplier = 0f;  private set
        @JvmStatic var yMultiplier = 0f;  private set
        @JvmStatic var xOffset     = 0;   private set
        @JvmStatic var yOffset     = 0;   private set
        private var containerHeight = 0
        @JvmStatic var stackOffset  = 0f

        @JvmStatic fun init(width: Int, height: Int) {
            containerHeight = height
            var sw = width; var sh = height
            if (sw * 3 > sh * 4) sw = sh * 4 / 3 else sh = sw * 3 / 4
            xMultiplier = sw / 640f
            yMultiplier = sh / 480f
            xOffset = (width  - MAX_X * xMultiplier).toInt() / 2
            yOffset = (height - MAX_Y * yMultiplier).toInt() / 2
        }
    }

    // ---- immutable fields parsed from line ----

    val x: Float
    val y: Float
    val time: Int
    val type: Int
    /** Use getHitSoundType() for Java interop. */
    val hitSound: Short
    val addition: ByteArray?
    val additionCustomSampleIndex: Byte
    val additionHitSoundVolume: Int
    val additionHitSound: String?
    val sliderType: Char
    val sliderX: FloatArray?
    val sliderY: FloatArray?
    /** Use getRepeatCount() for Java interop. */
    val repeat: Int
    val pixelLength: Float
    val endTime: Int
    val edgeHitSound: ShortArray?
    val edgeAddition: Array<ByteArray>?

    // ---- mutable state fields ----

    var comboIndex  = 0
    var comboNumber = 0
    var stack       = 0

    init {
        /**
         * [OBJECT FORMATS]
         * Circles:  x,y,time,type,hitSound,addition
         * Sliders:  x,y,time,type,hitSound,sliderType|curveX:curveY|...,repeat,pixelLength,edgeHitSound,edgeAddition,addition
         * Spinners: x,y,time,type,hitSound,endTime,addition
         * addition -> sampl:add:cust:vol:hitsound (optional, defaults to "0:0:0:0:")
         */
        val tokens = line.split(",")
        x        = tokens[0].toFloat()
        y        = tokens[1].toFloat()
        time     = tokens[2].toInt()
        type     = tokens[3].toInt()
        hitSound = tokens[4].toShort()

        // type-specific fields — use tmp vars before assigning vals
        var sliderTypeTmp:    Char         = '\u0000'
        var sliderXTmp:       FloatArray?  = null
        var sliderYTmp:       FloatArray?  = null
        var repeatTmp:        Int          = 0
        var pixelLengthTmp:   Float        = 0f
        var endTimeTmp:       Int          = 0
        var edgeHitSoundTmp:  ShortArray?  = null
        var edgeAdditionTmp:  Array<ByteArray>? = null

        val additionIndex: Int = when {
            type and TYPE_CIRCLE != 0 -> 5
            type and TYPE_SLIDER != 0 -> {
                val sliderTokens = tokens[5].split("|")
                sliderTypeTmp = sliderTokens[0][0]
                sliderXTmp = FloatArray(sliderTokens.size - 1)
                sliderYTmp = FloatArray(sliderTokens.size - 1)
                for (j in 1 until sliderTokens.size) {
                    val xy = sliderTokens[j].split(":")
                    sliderXTmp[j - 1] = xy[0].toInt().toFloat()
                    sliderYTmp[j - 1] = xy[1].toInt().toFloat()
                }
                repeatTmp      = tokens[6].toInt()
                pixelLengthTmp = tokens[7].toFloat()
                if (tokens.size > 8) {
                    val t = tokens[8].split("|")
                    edgeHitSoundTmp = ShortArray(t.size) { t[it].toShort() }
                }
                if (tokens.size > 9) {
                    val t = tokens[9].split("|")
                    edgeAdditionTmp = Array(t.size) { i ->
                        val ab = t[i].split(":"); byteArrayOf(ab[0].toByte(), ab[1].toByte())
                    }
                }
                10
            }
            else -> { // spinner
                // some 'endTime' fields contain a ':' character (?)
                endTimeTmp = tokens[5].substringBefore(':').toInt()
                6
            }
        }

        sliderType  = sliderTypeTmp
        sliderX     = sliderXTmp
        sliderY     = sliderYTmp
        repeat      = repeatTmp
        pixelLength = pixelLengthTmp
        endTime     = endTimeTmp
        edgeHitSound  = edgeHitSoundTmp
        edgeAddition  = edgeAdditionTmp

        // addition fields
        var additionTmp:                ByteArray? = null
        var additionCustomSampleTmp:    Byte       = 0
        var additionHitSoundVolumeTmp:  Int        = 0
        var additionHitSoundTmp:        String?    = null

        if (tokens.size > additionIndex) {
            val t = tokens[additionIndex].split(":")
            if (t.size > 1) additionTmp = byteArrayOf(t[0].toByte(), t[1].toByte())
            if (t.size > 2) additionCustomSampleTmp   = t[2].toByte()
            if (t.size > 3) additionHitSoundVolumeTmp = t[3].toInt()
            if (t.size > 4) additionHitSoundTmp       = t[4]
        }

        addition                   = additionTmp
        additionCustomSampleIndex  = additionCustomSampleTmp
        additionHitSoundVolume     = additionHitSoundVolumeTmp
        additionHitSound           = additionHitSoundTmp
    }

    // ---- type predicates ----

    val isCircle:   Boolean get() = type and TYPE_CIRCLE   != 0
    val isSlider:   Boolean get() = type and TYPE_SLIDER   != 0
    val isSpinner:  Boolean get() = type and TYPE_SPINNER  != 0
    val isNewCombo: Boolean get() = type and TYPE_NEWCOMBO != 0

    fun getComboSkip() = type shr TYPE_NEWCOMBO

    fun getTypeName() = when {
        isCircle  -> "circle"
        isSlider  -> "slider"
        isSpinner -> "spinner"
        else      -> "unknown object type"
    }

    // ---- Java-compatible named accessors where property name ≠ original getter ----

    fun getHitSoundType()    = hitSound
    fun getRepeatCount()     = repeat
    fun getCustomSampleIndex() = additionCustomSampleIndex
    fun getHitSoundVolume()  = additionHitSoundVolume
    fun getHitSoundFile()    = additionHitSound

    fun getEdgeHitSoundType(index: Int) = edgeHitSound?.get(index) ?: hitSound

    fun getSampleSet(index: Int): Byte {
        edgeAddition?.let { return it[index][0] }
        addition?.let    { return it[0] }
        return 0
    }

    fun getAdditionSampleSet(index: Int): Byte {
        edgeAddition?.let { return it[index][1] }
        addition?.let    { return it[1] }
        return 0
    }

    // ---- scaled coordinates ----

    fun getScaledX() = (x - stack * stackOffset) * xMultiplier + xOffset

    fun getScaledY() = if (GameMod.HARD_ROCK.isActive())
        containerHeight - ((y + stack * stackOffset) * yMultiplier + yOffset)
    else
        (y - stack * stackOffset) * yMultiplier + yOffset

    fun getScaledSliderX(): FloatArray? = sliderX?.let { sx ->
        FloatArray(sx.size) { i -> (sx[i] - stack * stackOffset) * xMultiplier + xOffset }
    }

    fun getScaledSliderY(): FloatArray? = sliderY?.let { sy ->
        if (GameMod.HARD_ROCK.isActive())
            FloatArray(sy.size) { i -> containerHeight - ((sy[i] + stack * stackOffset) * yMultiplier + yOffset) }
        else
            FloatArray(sy.size) { i -> (sy[i] - stack * stackOffset) * yMultiplier + yOffset }
    }

    // ---- slider ----

    fun getSliderTime(sliderMultiplier: Float, beatLength: Float) =
        beatLength * (pixelLength / sliderMultiplier) / 100f

    fun getSliderCurve(scaled: Boolean): Curve {
        if (sliderType == SLIDER_PERFECT_CURVE && sliderX!!.size == 2) {
            val nora = Vec2f(sliderX[0] - x,          sliderY!![0] - y).nor()
            val norb = Vec2f(sliderX[0] - sliderX[1], sliderY[0] - sliderY[1]).nor()
            return if (Math.abs(norb.x * nora.y - norb.y * nora.x) < 0.00001f)
                LinearBezier(this, false, scaled)  // vectors parallel
            else
                CircumscribedCircle(this, scaled)
        }
        return if (sliderType == SLIDER_CATMULL) CatmullCurve(this, scaled)
               else LinearBezier(this, sliderType == SLIDER_LINEAR, scaled)
    }

    // ---- toString ----

    override fun toString(): String = buildString {
        val nf = DecimalFormat("###.#####")
        append(nf.format(x)); append(',')
        append(nf.format(y)); append(',')
        append(time);         append(',')
        append(type);         append(',')
        append(hitSound);     append(',')

        when {
            isCircle  -> { /* no extra fields */ }
            isSlider  -> {
                append(sliderType); append('|')
                for (i in sliderX!!.indices) {
                    append(nf.format(sliderX[i])); append(':')
                    append(nf.format(sliderY!![i])); append('|')
                }
                setCharAt(length - 1, ',')
                append(repeat); append(',')
                append(pixelLength); append(',')
                edgeHitSound?.let { ehs ->
                    for (v in ehs) { append(v); append('|') }
                    setCharAt(length - 1, ',')
                }
                edgeAddition?.let { ea ->
                    for (arr in ea) { append(arr[0]); append(':'); append(arr[1]); append('|') }
                    setCharAt(length - 1, ',')
                }
            }
            isSpinner -> { append(endTime); append(',') }
        }

        if (addition != null) {
            for (b in addition) { append(b); append(':') }
            append(additionCustomSampleIndex); append(':')
            append(additionHitSoundVolume);    append(':')
            if (additionHitSound != null) append(additionHitSound)
        } else
            setLength(length - 1)
    }
}
