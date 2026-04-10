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

package itdelatrisu.opsu

import itdelatrisu.opsu.audio.HitSound
import itdelatrisu.opsu.audio.MusicController
import itdelatrisu.opsu.audio.SoundController
import itdelatrisu.opsu.audio.SoundEffect
import itdelatrisu.opsu.beatmap.Beatmap
import itdelatrisu.opsu.beatmap.Health
import itdelatrisu.opsu.beatmap.HitObject
import itdelatrisu.opsu.downloads.Updater
import itdelatrisu.opsu.objects.curves.Curve
import itdelatrisu.opsu.options.Options
import itdelatrisu.opsu.replay.LifeFrame
import itdelatrisu.opsu.replay.Replay
import itdelatrisu.opsu.replay.ReplayFrame
import itdelatrisu.opsu.ui.UI
import itdelatrisu.opsu.user.UserList
import org.newdawn.slick.Animation
import org.newdawn.slick.Color
import org.newdawn.slick.Graphics
import org.newdawn.slick.Image
import java.io.File
import java.util.*
import java.util.concurrent.LinkedBlockingDeque

/**
 * Holds game data and renders all related elements.
 */
class GameData {

    // ── Enums ─────────────────────────────────────────────────────────────────

    /** Letter grades. */
    enum class Grade(
        private val large: GameImage?,
        private val small: GameImage?,
    ) {
        NULL(null, null),
        SS  (GameImage.RANKING_SS,  GameImage.RANKING_SS_SMALL),
        SSH (GameImage.RANKING_SSH, GameImage.RANKING_SSH_SMALL),
        S   (GameImage.RANKING_S,   GameImage.RANKING_S_SMALL),
        SH  (GameImage.RANKING_SH,  GameImage.RANKING_SH_SMALL),
        A   (GameImage.RANKING_A,   GameImage.RANKING_A_SMALL),
        B   (GameImage.RANKING_B,   GameImage.RANKING_B_SMALL),
        C   (GameImage.RANKING_C,   GameImage.RANKING_C_SMALL),
        D   (GameImage.RANKING_D,   GameImage.RANKING_D_SMALL);

        // TODO: LibGDX — scaled menu image cache
        private var menuImage: Image? = null

        /** Returns the large size grade image. */
        fun getLargeImage(): Image = large!!.getImage()  // TODO: LibGDX

        /** Returns the small size grade image. */
        fun getSmallImage(): Image = small!!.getImage()  // TODO: LibGDX

        /**
         * Returns the large size grade image scaled for song menu use.
         */
        fun getMenuImage(): Image {  // TODO: LibGDX
            menuImage?.let { return it }
            val img = getSmallImage()
            if (!small!!.hasBeatmapSkinImage())
                this.menuImage = img
            return img
        }

        companion object {
            /**
             * Clears all image references.
             * This does NOT destroy images, so be careful of memory leaks!
             */
            fun clearReferences() {
                for (grade in values())
                    grade.menuImage = null
            }
        }
    }

    /** Hit object types, used for drawing results. */
    enum class HitObjectType { CIRCLE, SLIDERTICK, SLIDER_FIRST, SLIDER_LAST, SPINNER }

    // ── Inner classes ─────────────────────────────────────────────────────────

    /**
     * Class to store hit error information.
     * @author fluddokt
     */
    private inner class HitErrorInfo(
        /** The correct hit time. */
        val time: Int,
        /** The coordinates of the hit. */
        @Suppress("unused") val x: Int,
        @Suppress("unused") val y: Int,
        /** The difference between the correct and actual hit times. */
        val timeDiff: Int,
    )

    /** Hit result helper class. */
    private inner class HitObjectResult(
        /** Object start time. */
        val time: Int,
        /** Hit result. */
        val result: Int,
        /** Object coordinates. */
        val x: Float,
        val y: Float,
        /** Combo color. */
        val color: Color?,
        /** The type of the hit object. */
        val hitResultType: HitObjectType,
        /** Slider curve. */
        val curve: Curve?,
        /** Whether or not to expand when animating. */
        val expand: Boolean,
        /** Whether or not to hide the hit result. */
        val hideResult: Boolean,
    ) {
        /** Alpha level (for fading out). */
        var alpha: Float = 1f
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        /** Time, in milliseconds, for a hit result to remain existent. */
        const val HITRESULT_TIME      = 833
        /** Time, in milliseconds, for a hit result to fade. */
        const val HITRESULT_FADE_TIME = 500
        /** Time, in milliseconds, for a hit circle to fade. */
        const val HITCIRCLE_FADE_TIME = 300

        private const val COMBO_POP_TIME            = 250
        private const val HIT_ERROR_FADE_TIME       = 5000
        private const val HITCIRCLE_ANIM_SCALE      = 1.38f
        private const val HITCIRCLE_TEXT_ANIM_SCALE  = 1.28f
        private const val HITCIRCLE_TEXT_BOUNCE_TIME = 100
        private const val HITCIRCLE_TEXT_FADE_TIME   = 833

        // Hit result type constants
        const val HIT_MISS             = 0
        const val HIT_50               = 1
        const val HIT_100              = 2
        const val HIT_300              = 3
        const val HIT_100K             = 4   // 100-Katu
        const val HIT_300K             = 5   // 300-Katu
        const val HIT_300G             = 6   // Geki
        const val HIT_SLIDER10         = 7
        const val HIT_SLIDER30         = 8
        const val HIT_MAX              = 9
        const val HIT_SLIDER_REPEAT    = 10
        const val HIT_ANIMATION_RESULT = 11
        const val HIT_SPINNERSPIN      = 12
        const val HIT_SPINNERBONUS     = 13
        const val HIT_MU               = 14  // Mu

        /** Random number generator (for score animation). */
        private val random = Random()

        /**
         * Returns the raw score percentage.
         * @param hit300 the number of 300s
         * @param hit100 the number of 100s
         * @param hit50  the number of 50s
         * @param miss   the number of misses
         */
        @JvmStatic
        fun getScorePercent(hit300: Int, hit100: Int, hit50: Int, miss: Int): Float {
            val objectCount = hit300 + hit100 + hit50 + miss
            return if (objectCount > 0)
                (hit300 * 300 + hit100 * 100 + hit50 * 50) / (objectCount * 300f) * 100f
            else 0f
        }

        /**
         * Returns letter grade based on score data,
         * or [Grade.NULL] if no objects have been processed.
         * @param silver whether or not a silver SS/S should be awarded (if applicable)
         */
        @JvmStatic
        fun getGrade(hit300: Int, hit100: Int, hit50: Int, miss: Int, silver: Boolean): Grade {
            val objectCount = hit300 + hit100 + hit50 + miss
            if (objectCount < 1) return Grade.NULL
            val percent     = getScorePercent(hit300, hit100, hit50, miss)
            val hit300ratio = hit300 * 100f / objectCount
            val hit50ratio  = hit50  * 100f / objectCount
            val noMiss      = (miss == 0)
            return when {
                percent >= 100f                                           -> if (silver) Grade.SSH else Grade.SS
                hit300ratio >= 90f && hit50ratio < 1.0f && noMiss        -> if (silver) Grade.SH  else Grade.S
                (hit300ratio >= 80f && noMiss) || hit300ratio >= 90f     -> Grade.A
                (hit300ratio >= 70f && noMiss) || hit300ratio >= 80f     -> Grade.B
                hit300ratio >= 60f                                       -> Grade.C
                else                                                     -> Grade.D
            }
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    /** Hit result-related images (indexed by HIT_* constants up to HIT_MAX). */
    private var hitResults: Array<Image?> = arrayOfNulls(HIT_MAX) // TODO: LibGDX

    /** Counts of each hit result so far (indexed by HIT_* constants up to HIT_MAX). */
    private var hitResultCount: IntArray = IntArray(HIT_MAX)

    /** Total objects including slider hits/ticks (for determining Full Combo status). */
    private var fullObjectCount: Int = 0

    /** The current combo streak. */
    private var combo: Int = 0

    /** The max combo streak obtained. */
    private var comboMax: Int = 0

    /** The current combo pop timer, in milliseconds. */
    private var comboPopTime: Int = COMBO_POP_TIME

    /**
     * Hit result types accumulated this streak (bitmask), for Katu/Geki status.
     *  - &1: 100
     *  - &2: 50/Miss
     */
    private var comboEnd: Byte = 0

    /** Combo burst images. */
    private var comboBurstImages: Array<Image>? = null // TODO: LibGDX

    /** Index of the current combo burst image. */
    private var comboBurstIndex: Int = -1

    /** Alpha level of the current combo burst image (for fade out). */
    private var comboBurstAlpha: Float = 0f

    /** Current x coordinate of the combo burst image (for sliding animation). */
    private var comboBurstX: Float = 0f

    /** Time offsets for obtaining each hit result (indexed by HIT_* constants up to HIT_MAX). */
    private var hitResultOffset: IntArray? = null

    /** List of hit result objects associated with hit objects. */
    private var hitResultList: LinkedBlockingDeque<HitObjectResult> = LinkedBlockingDeque()

    /** List containing recent hit error information. */
    private var hitErrorList: LinkedBlockingDeque<HitErrorInfo> = LinkedBlockingDeque()

    /** List containing all hit error time differences. */
    private var hitErrors: MutableList<Int> = ArrayList()

    /** Performance string containing hit error averages and unstable rate. */
    private var performanceString: String? = null

    /** Current game score. */
    private var score: Long = 0L

    /** Displayed game score (for animation, slightly behind score). */
    private var scoreDisplay: Long = 0L

    /** Displayed game score percent (for animation, slightly behind score percent). */
    private var scorePercentDisplay: Float = 0f

    /** Health. */
    private val health: Health = Health()

    /** The difficulty multiplier used in the score formula. */
    private var difficultyMultiplier: Int = 2

    /** Default text symbol images. */
    private var defaultSymbols: Array<Image?>? = null // TODO: LibGDX

    /** Score text symbol images. */
    private var scoreSymbols: HashMap<Char, Image>? = null // TODO: LibGDX

    /** Scorebar animation. */
    private var scorebarColour: Animation? = null // TODO: LibGDX

    /** The associated score data. */
    private var scoreData: ScoreData? = null

    /** The associated replay. */
    private var replay: Replay? = null

    /** Whether this object is used for gameplay (true) or score viewing (false). */
    var isGameplay: Boolean = false

    /** Container dimensions. */
    private val width:  Int
    private val height: Int

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Constructor for gameplay.
     * @param width  container width
     * @param height container height
     */
    constructor(width: Int, height: Int) {
        this.width     = width
        this.height    = height
        this.isGameplay = true
        clear()
    }

    /**
     * Constructor for score viewing.
     * Initializes all parameters and images needed for [drawRankingElements].
     * @param s      the ScoreData object
     * @param width  container width
     * @param height container height
     */
    constructor(s: ScoreData, width: Int, height: Int) {
        this.width     = width
        this.height    = height
        this.isGameplay = false

        this.scoreData      = s
        this.score          = s.score
        this.comboMax       = s.combo
        this.fullObjectCount = if (s.perfect) s.combo else -1
        this.hitResultCount = IntArray(HIT_MAX)
        hitResultCount[HIT_300]  = s.hit300
        hitResultCount[HIT_100]  = s.hit100
        hitResultCount[HIT_50]   = s.hit50
        hitResultCount[HIT_300G] = s.geki
        hitResultCount[HIT_300K] = 0
        hitResultCount[HIT_100K] = s.katu
        hitResultCount[HIT_MISS] = s.miss
        this.replay = if (s.replayString == null) null
        else Replay(File(Options.getReplayDir(), "${s.replayString}.osr"))

        loadImages()
    }

    // ── Data management ───────────────────────────────────────────────────────

    /** Clears all data and re-initializes object. */
    fun clear() {
        score             = 0L
        scoreDisplay      = 0L
        scorePercentDisplay = 0f
        health.reset()
        hitResultCount    = IntArray(HIT_MAX)
        hitResultList.forEach { it.curve?.discardGeometry() }
        hitResultList     = LinkedBlockingDeque()
        hitErrorList      = LinkedBlockingDeque()
        hitErrors         = ArrayList()
        performanceString = null
        fullObjectCount   = 0
        combo             = 0
        comboMax          = 0
        comboPopTime      = COMBO_POP_TIME
        comboEnd          = 0
        comboBurstIndex   = -1
        scoreData         = null
    }

    /** Loads all game score images. */
    // TODO: LibGDX — all Image/Animation loading must be re-implemented
    fun loadImages() {
        if (isGameplay) {
            comboBurstImages = when {
                GameImage.COMBO_BURST.hasBeatmapSkinImages() ||
                        (!GameImage.COMBO_BURST.hasBeatmapSkinImage() &&
                                GameImage.COMBO_BURST.getImages() != null)
                    -> GameImage.COMBO_BURST.getImages()
                else -> arrayOf(GameImage.COMBO_BURST.getImage())
            }

            scorebarColour = null
            if (GameImage.SCOREBAR_COLOUR.getImages() != null)
                scorebarColour = GameImage.SCOREBAR_COLOUR.getAnimation()

            defaultSymbols = arrayOfNulls(10)
            defaultSymbols!![0] = GameImage.DEFAULT_0.getImage()
            defaultSymbols!![1] = GameImage.DEFAULT_1.getImage()
            defaultSymbols!![2] = GameImage.DEFAULT_2.getImage()
            defaultSymbols!![3] = GameImage.DEFAULT_3.getImage()
            defaultSymbols!![4] = GameImage.DEFAULT_4.getImage()
            defaultSymbols!![5] = GameImage.DEFAULT_5.getImage()
            defaultSymbols!![6] = GameImage.DEFAULT_6.getImage()
            defaultSymbols!![7] = GameImage.DEFAULT_7.getImage()
            defaultSymbols!![8] = GameImage.DEFAULT_8.getImage()
            defaultSymbols!![9] = GameImage.DEFAULT_9.getImage()
        }

        scoreSymbols = HashMap(14)
        scoreSymbols!!['0'] = GameImage.SCORE_0.getImage()
        scoreSymbols!!['1'] = GameImage.SCORE_1.getImage()
        scoreSymbols!!['2'] = GameImage.SCORE_2.getImage()
        scoreSymbols!!['3'] = GameImage.SCORE_3.getImage()
        scoreSymbols!!['4'] = GameImage.SCORE_4.getImage()
        scoreSymbols!!['5'] = GameImage.SCORE_5.getImage()
        scoreSymbols!!['6'] = GameImage.SCORE_6.getImage()
        scoreSymbols!!['7'] = GameImage.SCORE_7.getImage()
        scoreSymbols!!['8'] = GameImage.SCORE_8.getImage()
        scoreSymbols!!['9'] = GameImage.SCORE_9.getImage()
        scoreSymbols!![','] = GameImage.SCORE_COMMA.getImage()
        scoreSymbols!!['.'] = GameImage.SCORE_DOT.getImage()
        scoreSymbols!!['%'] = GameImage.SCORE_PERCENT.getImage()
        scoreSymbols!!['x'] = GameImage.SCORE_X.getImage()

        hitResults = arrayOfNulls(HIT_MAX)
        hitResults[HIT_MISS]     = GameImage.HIT_MISS.getImage()
        hitResults[HIT_50]       = GameImage.HIT_50.getImage()
        hitResults[HIT_100]      = GameImage.HIT_100.getImage()
        hitResults[HIT_300]      = GameImage.HIT_300.getImage()
        hitResults[HIT_100K]     = GameImage.HIT_100K.getImage()
        hitResults[HIT_300K]     = GameImage.HIT_300K.getImage()
        hitResults[HIT_300G]     = GameImage.HIT_300G.getImage()
        hitResults[HIT_SLIDER10] = GameImage.HIT_SLIDER10.getImage()
        hitResults[HIT_SLIDER30] = GameImage.HIT_SLIDER30.getImage()
    }

    // ── Symbol drawing ────────────────────────────────────────────────────────

    /** Returns a default text symbol image for a digit. */
    fun getDefaultSymbolImage(i: Int): Image = defaultSymbols!![i]!! // TODO: LibGDX

    /** Returns a score text symbol image for a character. */
    fun getScoreSymbolImage(c: Char): Image = scoreSymbols!![c]!! // TODO: LibGDX

    /** Sets the array of hit result offsets. */
    fun setHitResultOffset(hitResultOffset: IntArray) {
        this.hitResultOffset = hitResultOffset
    }

    /**
     * Draws a number with defaultSymbols.
     * @param n     the number to draw
     * @param x     the center x coordinate
     * @param y     the center y coordinate
     * @param scale the scale to apply
     * @param alpha the alpha level
     */
    fun drawSymbolNumber(n: Int, x: Float, y: Float, scale: Float, alpha: Float) { // TODO: LibGDX
        var num = n
        val length = Math.log10(n.toDouble()).toInt() + 1
        val digitWidth = getDefaultSymbolImage(0).width * scale
        var cx = x + (length - 1) * (digitWidth / 2)
        for (i in 0 until length) {
            val digit = getDefaultSymbolImage(num % 10).getScaledCopy(scale)
            digit.setAlpha(alpha)
            digit.drawCentered(cx, y)
            cx -= digitWidth
            num /= 10
        }
    }

    /**
     * Draws a string of scoreSymbols.
     * @param str        the string to draw
     * @param x          the starting x coordinate
     * @param y          the y coordinate
     * @param scale      the scale to apply
     * @param alpha      the alpha level
     * @param rightAlign align right (true) or left (false)
     */
    fun drawSymbolString(str: String, x: Float, y: Float, scale: Float, alpha: Float, rightAlign: Boolean) { // TODO: LibGDX
        val chars = str.toCharArray()
        var cx = x
        if (rightAlign) {
            for (i in chars.indices.reversed()) {
                var digit = getScoreSymbolImage(chars[i])
                if (scale != 1.0f) digit = digit.getScaledCopy(scale)
                cx -= digit.width
                digit.setAlpha(alpha); digit.draw(cx, y); digit.setAlpha(1f)
            }
        } else {
            for (c in chars) {
                var digit = getScoreSymbolImage(c)
                if (scale != 1.0f) digit = digit.getScaledCopy(scale)
                digit.setAlpha(alpha); digit.draw(cx, y); digit.setAlpha(1f)
                cx += digit.width
            }
        }
    }

    /**
     * Draws a string of scoreSymbols of fixed width.
     * @param str        the string to draw
     * @param x          the starting x coordinate
     * @param y          the y coordinate
     * @param scale      the scale to apply
     * @param alpha      the alpha level
     * @param fixedsize  the width to use for all symbols
     * @param rightAlign align right (true) or left (false)
     */
    fun drawFixedSizeSymbolString(str: String, x: Float, y: Float, scale: Float, alpha: Float, fixedsize: Float, rightAlign: Boolean) { // TODO: LibGDX
        val chars = str.toCharArray()
        var cx = x
        if (rightAlign) {
            for (i in chars.indices.reversed()) {
                var digit = getScoreSymbolImage(chars[i])
                if (scale != 1.0f) digit = digit.getScaledCopy(scale)
                cx -= fixedsize
                digit.setAlpha(alpha); digit.draw(cx + (fixedsize - digit.width) / 2, y); digit.setAlpha(1f)
            }
        } else {
            for (c in chars) {
                var digit = getScoreSymbolImage(c)
                if (scale != 1.0f) digit = digit.getScaledCopy(scale)
                digit.setAlpha(alpha); digit.draw(cx + (fixedsize - digit.width) / 2, y); digit.setAlpha(1f)
                cx += fixedsize
            }
        }
    }

    // ── Main drawing methods ──────────────────────────────────────────────────

    /**
     * Draws game elements: scorebar, score, score percentage, map progress circle,
     * mod icons, combo count, combo burst, hit error bar, and grade.
     * @param g           the graphics context
     * @param breakPeriod if true, will not draw scorebar/combo elements and will draw grade
     * @param firstObject true if the first hit object's start time has not yet passed
     * @param alpha       the alpha level at which to render all elements (except the hit error bar)
     */
    @Suppress("DEPRECATION")
    fun drawGameElements(g: Graphics, breakPeriod: Boolean, firstObject: Boolean, alpha: Float) {
        // TODO: LibGDX — full reimplementation required
    }

    /**
     * Draws ranking elements: score, results, ranking, game mods.
     * @param g       the graphics context
     * @param beatmap the beatmap
     * @param time    the animation time
     */
    fun drawRankingElements(g: Graphics, beatmap: Beatmap, time: Int) {
        // TODO: LibGDX — full reimplementation required
    }

    /**
     * Draws stored hit results and removes them from the list as necessary.
     * @param trackPosition the current track position (in ms)
     * @param over          true if drawing elements over hit objects, false for under
     */
    fun drawHitResults(trackPosition: Int, over: Boolean) {
        val iter = hitResultList.iterator()
        while (iter.hasNext()) {
            val hitResult = iter.next()
            if (hitResult.time + HITRESULT_TIME > trackPosition) {
                if (over) {
                    // TODO: LibGDX — draw spinner osu, hit lighting, hit result text
                    hitResult.alpha = 1 - (trackPosition - hitResult.time).toFloat() / HITRESULT_FADE_TIME
                } else {
                    // draw hit animations under hit objects
                    if (!GameMod.HIDDEN.isActive())
                        drawHitAnimations(hitResult, trackPosition)
                }
            } else {
                hitResult.curve?.discardGeometry()
                iter.remove()
            }
        }
    }

    /**
     * Draw the hit animations: circles, reverse arrows, slider curves (fading out and/or expanding).
     */
    private fun drawHitAnimations(hitResult: HitObjectResult, trackPosition: Int) {
        // TODO: LibGDX — fade out slider curve, hit circle expansion, repeat arrow
    }

    // ── Health ────────────────────────────────────────────────────────────────

    /** Returns the current health percentage. */
    fun getHealthPercent(): Float = health.getHealth()

    /**
     * Sets the health modifiers.
     * @param hpDrainRate          the HP drain rate
     * @param hpMultiplierNormal   the normal HP multiplier
     * @param hpMultiplierComboEnd the combo-end HP multiplier
     */
    fun setHealthModifiers(hpDrainRate: Float, hpMultiplierNormal: Float, hpMultiplierComboEnd: Float) {
        health.setModifiers(hpDrainRate, hpMultiplierNormal, hpMultiplierComboEnd)
    }

    /**
     * Returns false if health is zero.
     * If "No Fail" or "Auto" mods are active, this will always return true.
     */
    fun isAlive(): Boolean =
        health.getHealth() > 0f ||
                GameMod.NO_FAIL.isActive()    ||
                GameMod.AUTO.isActive()       ||
                GameMod.RELAX.isActive()      ||
                GameMod.AUTOPILOT.isActive()

    /**
     * Changes health by a raw value.
     * @param value the health value
     */
    fun changeHealth(value: Float) = health.changeHealth(value)

    // ── Score ─────────────────────────────────────────────────────────────────

    /** Returns the raw score. */
    fun getScore(): Long = score

    /**
     * Changes score by a raw value (not affected by other modifiers).
     * @param value the score value
     */
    fun changeScore(value: Int) { score += value }

    /** Returns the raw score percentage. */
    fun getScorePercent(): Float = getScorePercent(
        hitResultCount[HIT_300], hitResultCount[HIT_100],
        hitResultCount[HIT_50],  hitResultCount[HIT_MISS],
    )

    private fun getGrade(): Grade {
        val silver = if (scoreData == null)
            GameMod.HIDDEN.isActive() || GameMod.FLASHLIGHT.isActive()
        else
            (scoreData!!.mods and (GameMod.HIDDEN.getBit() or GameMod.FLASHLIGHT.getBit())) != 0
        return getGrade(hitResultCount[HIT_300], hitResultCount[HIT_100],
            hitResultCount[HIT_50],  hitResultCount[HIT_MISS], silver)
    }

    // ── Combo ─────────────────────────────────────────────────────────────────

    /** Returns the current combo streak. */
    fun getComboStreak(): Int = combo

    /** Increases the combo streak by one. */
    private fun incrementComboStreak() {
        combo++
        comboPopTime = 0
        if (combo > comboMax) comboMax = combo

        // combo bursts (at 30, 60, 100+50x)
        val images = comboBurstImages ?: return
        if (Options.isComboBurstEnabled() &&
            (combo == 30 || combo == 60 || (combo >= 100 && combo % 50 == 0))) {
            comboBurstIndex = if (Options.getSkin().isComboBurstRandom())
                (Math.random() * images.size).toInt()
            else when (combo) {
                30   -> 0
                else -> (comboBurstIndex + 1) % images.size
            }
            comboBurstAlpha = 0.8f
            comboBurstX = if (comboBurstIndex % 2 == 0) width.toFloat()
            else images[0].width.toFloat() * -1
        }
    }

    /** Resets the combo streak to zero. */
    private fun resetComboStreak() {
        if (combo > 20 && !(GameMod.RELAX.isActive() || GameMod.AUTOPILOT.isActive())) {
            if (!Options.isGameplaySoundDisabled())
                SoundController.playSound(SoundEffect.COMBOBREAK)
        }
        combo = 0
        if (GameMod.SUDDEN_DEATH.isActive())
            health.setHealth(0f)
    }

    // ── Slider result senders ─────────────────────────────────────────────────

    /**
     * Handles a slider repeat result (animation only: arrow).
     */
    fun sendSliderRepeatResult(time: Int, x: Float, y: Float, color: Color, curve: Curve, type: HitObjectType) {
        hitResultList.add(HitObjectResult(time, HIT_SLIDER_REPEAT, x, y, color, type, curve, true, true))
    }

    /**
     * Handles a slider start result (animation only: initial circle).
     */
    fun sendSliderStartResult(time: Int, x: Float, y: Float, color: Color, expand: Boolean) {
        hitResultList.add(HitObjectResult(time, HIT_ANIMATION_RESULT, x, y, color, HitObjectType.CIRCLE, null, expand, true))
    }

    /**
     * Handles a slider tick result.
     * @param time      the tick start time
     * @param result    the hit result (HIT_* constants)
     * @param x         the x coordinate
     * @param y         the y coordinate
     * @param hitObject the hit object
     * @param repeat    the current repeat number
     */
    fun sendSliderTickResult(time: Int, result: Int, x: Float, y: Float, hitObject: HitObject, repeat: Int) {
        val hitValue: Int = when (result) {
            HIT_SLIDER30 -> {
                SoundController.playHitSound(
                    hitObject.getEdgeHitSoundType(repeat),
                    hitObject.getSampleSet(repeat),
                    hitObject.getAdditionSampleSet(repeat))
                30
            }
            HIT_SLIDER10 -> {
                SoundController.playHitSound(HitSound.SLIDERTICK)
                10
            }
            HIT_MISS     -> { resetComboStreak(); 0 }
            else         -> return
        }

        if (hitValue > 0) {
            score += hitValue
            incrementComboStreak()
            health.changeHealthForHit(result)
            if (Options.isPerfectHitBurstEnabled())
                hitResultList.add(HitObjectResult(time, result, x, y, null, HitObjectType.SLIDERTICK, null, false, false))
        }
        fullObjectCount++
    }

    /**
     * Handles a spinner spin result.
     * @param result the hit result (HIT_* constants)
     */
    fun sendSpinnerSpinResult(result: Int) {
        val hitValue: Int = when (result) {
            HIT_SPINNERSPIN  -> { if (!Options.isGameplaySoundDisabled()) SoundController.playSound(SoundEffect.SPINNERSPIN);  100 }
            HIT_SPINNERBONUS -> { if (!Options.isGameplaySoundDisabled()) SoundController.playSound(SoundEffect.SPINNERBONUS); 1100 }
            else             -> return
        }
        score += hitValue
        health.changeHealthForHit(result)
    }

    // ── Score formula ─────────────────────────────────────────────────────────

    /**
     * Returns the score for a hit based on:
     * Score = Hit Value + Hit Value * (Combo * Difficulty * Mod) / 25
     */
    private fun getScoreForHit(hitValue: Int, hitObject: HitObject): Int {
        var comboMultiplier = maxOf(combo - 1, 0)
        if (hitObject.isSlider) comboMultiplier++
        return hitValue + (hitValue * (comboMultiplier * difficultyMultiplier * GameMod.getScoreMultiplier()) / 25).toInt()
    }

    /**
     * Computes and stores the difficulty multiplier used in the score formula.
     * @param drainRate         the raw HP drain rate value
     * @param circleSize        the raw circle size value
     * @param overallDifficulty the raw overall difficulty value
     */
    fun calculateDifficultyMultiplier(drainRate: Float, circleSize: Float, overallDifficulty: Float) {
        val sum = drainRate + circleSize + overallDifficulty
        difficultyMultiplier = when {
            sum <= 5f  -> 2
            sum <= 12f -> 3
            sum <= 17f -> 4
            sum <= 24f -> 5
            else       -> 6
        }
    }

    // ── Hit result processing ─────────────────────────────────────────────────

    /**
     * Handles a hit result and performs all associated calculations.
     * @return the actual hit result (HIT_* constants)
     */
    private fun handleHitResult(
        time: Int, result: Int, x: Float, y: Float, color: Color?,
        end: Boolean, hitObject: HitObject, hitResultType: HitObjectType,
        repeat: Int, noIncrementCombo: Boolean,
    ): Int {
        var actualResult = result
        val hitValue: Int = when (result) {
            HIT_300  -> 300
            HIT_100  -> { comboEnd = (comboEnd.toInt() or 1).toByte(); 100 }
            HIT_50   -> { comboEnd = (comboEnd.toInt() or 2).toByte();  50 }
            HIT_MISS -> { comboEnd = (comboEnd.toInt() or 2).toByte(); resetComboStreak(); 0 }
            else     -> return HIT_MISS
        }

        if (hitValue > 0) {
            SoundController.playHitSound(
                hitObject.getEdgeHitSoundType(repeat),
                hitObject.getSampleSet(repeat),
                hitObject.getAdditionSampleSet(repeat))
            changeScore(getScoreForHit(hitValue, hitObject))
            if (!noIncrementCombo) incrementComboStreak()
        }
        health.changeHealthForHit(result)
        hitResultCount[result]++
        fullObjectCount++

        // last element in combo: check for Geki/Katu
        if (end) {
            when {
                comboEnd.toInt() == 0 -> {
                    actualResult = HIT_300G
                    health.changeHealthForHit(HIT_300G)
                    hitResultCount[HIT_300G]++
                }
                (comboEnd.toInt() and 2) == 0 -> when (result) {
                    HIT_100 -> {
                        actualResult = HIT_100K
                        health.changeHealthForHit(HIT_100K)
                        hitResultCount[HIT_100K]++
                    }
                    HIT_300 -> {
                        actualResult = HIT_300K
                        health.changeHealthForHit(HIT_300K)
                        hitResultCount[HIT_300K]++
                    }
                }
                hitValue > 0 -> health.changeHealthForHit(HIT_MU)
            }
            comboEnd = 0
        }

        return actualResult
    }

    /**
     * Handles a hit result.
     * @param time             the object start time
     * @param result           the hit result (HIT_* constants)
     * @param x                the x coordinate
     * @param y                the y coordinate
     * @param color            the combo color
     * @param end              true if this is the last hit object in the combo
     * @param hitObject        the hit object
     * @param hitResultType    the type of hit object for the result
     * @param expand           whether the hit result animation should expand
     * @param repeat           the current repeat number (for sliders, or 0 otherwise)
     * @param curve            the slider curve (or null if not applicable)
     * @param sliderHeldToEnd  whether or not the slider was held to the end
     */
    fun sendHitResult(
        time: Int, result: Int, x: Float, y: Float, color: Color?,
        end: Boolean, hitObject: HitObject, hitResultType: HitObjectType,
        expand: Boolean, repeat: Int, curve: Curve?, sliderHeldToEnd: Boolean,
    ) {
        val hitResult = handleHitResult(
            time, result, x, y, color, end, hitObject, hitResultType,
            repeat, curve != null && !sliderHeldToEnd,
        )
        // "relax" and "autopilot" mods: hide misses
        if (hitResult == HIT_MISS && (GameMod.RELAX.isActive() || GameMod.AUTOPILOT.isActive())) return

        val hideResult = (hitResult == HIT_300 || hitResult == HIT_300G || hitResult == HIT_300K) &&
                !Options.isPerfectHitBurstEnabled()
        hitResultList.add(HitObjectResult(time, hitResult, x, y, color, hitResultType, curve, expand, hideResult))
    }

    // ── Display updates ───────────────────────────────────────────────────────

    /**
     * Updates displayed elements based on a delta value.
     * @param delta the delta interval since the last call
     */
    fun updateDisplays(delta: Int) {
        // score display
        if (scoreDisplay < score) {
            scoreDisplay += (score - scoreDisplay) * delta / 50 + 1
            if (scoreDisplay > score) scoreDisplay = score
        }

        // score percent display
        val scorePercent = getScorePercent()
        if (scorePercentDisplay != scorePercent) {
            if (scorePercentDisplay < scorePercent) {
                scorePercentDisplay += (scorePercent - scorePercentDisplay) * delta / 50f + 0.01f
                if (scorePercentDisplay > scorePercent) scorePercentDisplay = scorePercent
            } else {
                scorePercentDisplay -= (scorePercentDisplay - scorePercent) * delta / 50f + 0.01f
                if (scorePercentDisplay < scorePercent) scorePercentDisplay = scorePercent
            }
        }

        health.update(delta)

        // combo burst
        val burstImages = comboBurstImages
        if (comboBurstIndex > -1 && Options.isComboBurstEnabled() && burstImages != null) {
            val leftX  = 0f
            val rightX = width - burstImages[comboBurstIndex].width.toFloat()
            when {
                comboBurstX < leftX -> {
                    comboBurstX += (delta / 2f) * GameImage.getUIscale()
                    if (comboBurstX > leftX) comboBurstX = leftX
                }
                comboBurstX > rightX -> {
                    comboBurstX -= (delta / 2f) * GameImage.getUIscale()
                    if (comboBurstX < rightX) comboBurstX = rightX
                }
                comboBurstAlpha > 0f -> {
                    comboBurstAlpha -= delta / 1200f
                    if (comboBurstAlpha < 0f) comboBurstAlpha = 0f
                }
            }
        }

        // combo pop
        comboPopTime += delta
        if (comboPopTime > COMBO_POP_TIME) comboPopTime = COMBO_POP_TIME

        // hit error bar
        if (Options.isHitErrorBarEnabled()) {
            val trackPosition = MusicController.getPosition(true)
            val offset = hitResultOffset
            val iter = hitErrorList.iterator()
            while (iter.hasNext()) {
                val info = iter.next()
                if ((offset != null && Math.abs(info.timeDiff) >= offset[HIT_50]) ||
                    info.time + HIT_ERROR_FADE_TIME <= trackPosition)
                    iter.remove()
            }
        }
    }

    /**
     * Updates displayed ranking elements based on a delta value.
     * @param delta  the delta interval since the last call
     * @param mouseX the mouse x coordinate
     * @param mouseY the mouse y coordinate
     */
    fun updateRankingDisplays(delta: Int, mouseX: Int, mouseY: Int) {
        // TODO: LibGDX — graph tooltip uses Image dimensions
        val graphImg = GameImage.RANKING_GRAPH.getImage()
        val graphX = 416 * GameImage.getUIscale()
        val graphY = 688 * GameImage.getUIscale()
        if (isGameplay &&
            mouseX >= graphX - graphImg.width / 2f && mouseX <= graphX + graphImg.width / 2f &&
            mouseY >= graphY - graphImg.height / 2f && mouseY <= graphY + graphImg.height / 2f) {
            if (performanceString == null)
                performanceString = getPerformanceString(hitErrors)
            UI.updateTooltip(delta, performanceString, true)
        }
    }

    // ── Score/Replay data ─────────────────────────────────────────────────────

    /**
     * Returns a ScoreData object encapsulating all game data.
     * If score data already exists, the existing object will be returned.
     * @see getCurrentScoreData
     */
    fun getScoreData(beatmap: Beatmap): ScoreData {
        if (scoreData == null) scoreData = getCurrentScoreData(beatmap, false)
        return scoreData!!
    }

    /**
     * Returns a ScoreData object encapsulating all current game data.
     * @param beatmap       the beatmap
     * @param slidingScore  if true, use the display score (might not be actual score)
     * @see getScoreData
     */
    fun getCurrentScoreData(beatmap: Beatmap, slidingScore: Boolean): ScoreData {
        val sd = ScoreData()
        sd.timestamp = System.currentTimeMillis() / 1000L
        sd.MID     = beatmap.beatmapID
        sd.MSID    = beatmap.beatmapSetID
        sd.title   = beatmap.title
        sd.artist  = beatmap.artist
        sd.creator = beatmap.creator
        sd.version = beatmap.version
        sd.hit300  = hitResultCount[HIT_300]
        sd.hit100  = hitResultCount[HIT_100]
        sd.hit50   = hitResultCount[HIT_50]
        sd.geki    = hitResultCount[HIT_300G]
        sd.katu    = hitResultCount[HIT_300K] + hitResultCount[HIT_100K]
        sd.miss    = hitResultCount[HIT_MISS]
        sd.score   = if (slidingScore) scoreDisplay else score
        sd.combo   = comboMax
        sd.perfect = (comboMax == fullObjectCount)
        sd.mods    = GameMod.getModState()
        sd.replayString = replay?.getReplayFilename()
        sd.playerName   = if (GameMod.AUTO.isActive()) UserList.AUTO_USER_NAME
        else UserList.get().getCurrentUser().getName()
        return sd
    }

    /**
     * Returns a Replay object encapsulating all game data.
     * If a replay already exists and frames is null, the existing object will be returned.
     * @param frames     the replay frames
     * @param lifeFrames the life frames
     * @param beatmap    the associated beatmap
     * @return the Replay object, or null if none exists and frames is null
     */
    fun getReplay(frames: Array<ReplayFrame>?, lifeFrames: Array<LifeFrame>?, beatmap: Beatmap?): Replay? {
        if (replay != null && frames == null) return replay
        frames ?: return null

        val r = Replay()
        r.mode        = 0  // TODO: Beatmap.MODE_OSU
        r.version     = Updater.get().getBuildDate()
        r.beatmapHash = beatmap?.md5Hash ?: ""
        r.playerName  = UserList.get().getCurrentUser().getName()
        r.replayHash  = System.currentTimeMillis().toString()  // TODO
        r.hit300      = hitResultCount[HIT_300].toShort()
        r.hit100      = hitResultCount[HIT_100].toShort()
        r.hit50       = hitResultCount[HIT_50].toShort()
        r.geki        = hitResultCount[HIT_300G].toShort()
        r.katu        = (hitResultCount[HIT_300K] + hitResultCount[HIT_100K]).toShort()
        r.miss        = hitResultCount[HIT_MISS].toShort()
        r.score       = score.toInt()
        r.combo       = comboMax.toShort()
        r.perfect     = (comboMax == fullObjectCount)
        r.mods        = GameMod.getModState()
        r.lifeFrames  = lifeFrames
        r.timestamp   = Date()
        r.frames      = frames
        r.seed        = 0  // TODO
        r.loaded      = true
        replay = r
        return r
    }

    /** Sets the replay object. */
    fun setReplay(replay: Replay?) { this.replay = replay }

    // ── Hit error ─────────────────────────────────────────────────────────────

    /**
     * Adds the hit into the list of hit error information.
     * @param time     the correct hit time
     * @param x        the x coordinate of the hit
     * @param y        the y coordinate of the hit
     * @param timeDiff the difference between the correct and actual hit times
     */
    fun addHitError(time: Int, x: Int, y: Int, timeDiff: Int) {
        hitErrorList.addFirst(HitErrorInfo(time, x, y, timeDiff))
        hitErrors.add(timeDiff)
    }

    /** Computes the error values and unstable rate for the map. */
    private fun getPerformanceString(errors: List<Int>): String {
        var earlyCount = 0; var lateCount = 0
        var earlySum   = 0; var lateSum   = 0
        for (diff in errors) {
            when {
                diff < 0 -> { earlyCount++; earlySum += diff }
                diff > 0 -> { lateCount++;  lateSum  += diff }
            }
        }
        val hitErrorEarly = if (earlyCount > 0) earlySum.toFloat() / earlyCount else 0f
        val hitErrorLate  = if (lateCount  > 0) lateSum.toFloat()  / lateCount  else 0f
        val unstableRate  = if (errors.isNotEmpty()) Utils.standardDeviation(errors).toFloat() * 10f else 0f
        return "Accuracy:\nError: %.2fms - %.2fms avg\nUnstable Rate: %.2f"
            .format(hitErrorEarly, hitErrorLate, unstableRate)
    }
}
