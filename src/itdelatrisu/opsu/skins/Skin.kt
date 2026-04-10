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

package itdelatrisu.opsu.skins

import itdelatrisu.opsu.OpsuConstants
import org.newdawn.slick.Color
import java.io.File

open class Skin(val directory: File?) {

	companion object {
		const val DEFAULT_SKIN_NAME  = "Default"
		const val STYLE_PEPPYSLIDER: Byte = 1
		const val STYLE_MMSLIDER:    Byte = 2
		const val STYLE_TOONSLIDER:  Byte = 3
		const val STYLE_OPENGLSLIDER:Byte = 4
		const val LATEST_VERSION = 2f

		private val DEFAULT_COMBO = arrayOf(
			Color(255, 192, 0), Color(0, 202, 0),
			Color(18, 124, 255), Color(242, 24, 57)
		)
		private val DEFAULT_MENU_GLOW             = Color(0, 78, 155)
		private val DEFAULT_SLIDER_BORDER         = Color(255, 255, 255)
		private val DEFAULT_SLIDER_BALL           = Color(2, 170, 255)
		private val DEFAULT_SPINNER_APPROACH      = Color(77, 139, 217)
		private val DEFAULT_SONG_SELECT_ACTIVE    = Color(255, 255, 255)
		private val DEFAULT_SONG_SELECT_INACTIVE  = Color(178, 178, 178)
		private val DEFAULT_STAR_BREAK_ADDITIVE   = Color(255, 182, 193)
		private val DEFAULT_INPUT_OVERLAY_TEXT    = Color(0, 0, 0)
		private val DEFAULT_COMBO_BURST_SOUNDS    = intArrayOf(50, 75, 100, 200, 300)
	}

	var name:                    String   = "${OpsuConstants.PROJECT_NAME} Default Skin"
	var author:                  String   = "[various authors]"
	var version:                 Float    = LATEST_VERSION
	var sliderBallFlip:          Boolean  = false
	var cursorRotate:            Boolean  = true
	var cursorExpand:            Boolean  = true
	var cursorCentre:            Boolean  = true
	var sliderBallFrames:        Int      = 10
	var hitCircleOverlayAboveNumber: Boolean = true
	var spinnerFrequencyModulate:Boolean  = false
	var layeredHitSounds:        Boolean  = true
	var spinnerFadePlayfield:    Boolean  = true
	var spinnerNoBlink:          Boolean  = false
	var allowSliderBallTint:     Boolean  = false
	var animationFramerate:      Int      = -1
	var cursorTrailRotate:       Boolean  = false
	var customComboBurstSounds:  IntArray = DEFAULT_COMBO_BURST_SOUNDS
	var comboBurstRandom:        Boolean  = false
	var sliderStyle:             Byte     = STYLE_MMSLIDER

	var combo:                   Array<Color> = DEFAULT_COMBO
	var menuGlow:                Color    = DEFAULT_MENU_GLOW
	var sliderBorder:            Color    = DEFAULT_SLIDER_BORDER
	var sliderBall:              Color    = DEFAULT_SLIDER_BALL
	var spinnerApproachCircle:   Color    = DEFAULT_SPINNER_APPROACH
	var songSelectActiveText:    Color    = DEFAULT_SONG_SELECT_ACTIVE
	var songSelectInactiveText:  Color    = DEFAULT_SONG_SELECT_INACTIVE
	var starBreakAdditive:       Color    = DEFAULT_STAR_BREAK_ADDITIVE
	var inputOverlayText:        Color    = DEFAULT_INPUT_OVERLAY_TEXT

	var hitCirclePrefix:  String = "default"
	var hitCircleOverlap: Int    = -2
	var scorePrefix:      String = "score"
	var scoreOverlap:     Int    = 0
	var comboPrefix:      String = "score"
	var comboOverlap:     Int    = 0

	// getters для Java-совместимости
	fun getDirectory()               = directory
	fun getName()                    = name
	fun getAuthor()                  = author
	fun getVersion()                 = version
	fun isSliderBallFlipped()        = sliderBallFlip
	fun isCursorRotated()            = cursorRotate
	fun isCursorExpanded()           = cursorExpand
	fun isCursorCentered()           = cursorCentre
	fun getSliderBallFrames()        = sliderBallFrames
	fun isHitCircleOverlayAboveNumber() = hitCircleOverlayAboveNumber
	fun isSpinnerFrequencyModulated()= spinnerFrequencyModulate
	fun isLayeredHitSounds()         = layeredHitSounds
	fun isSpinnerFadePlayfield()     = spinnerFadePlayfield
	fun isSpinnerNoBlink()           = spinnerNoBlink
	fun isAllowSliderBallTint()      = allowSliderBallTint
	fun getAnimationFramerate()      = animationFramerate
	fun isCursorTrailRotated()       = cursorTrailRotate
	fun getCustomComboBurstSounds()  = customComboBurstSounds
	fun isComboBurstRandom()         = comboBurstRandom
	fun getSliderStyle()             = sliderStyle
	fun getComboColors()             = combo
	fun getMenuGlowColor()           = menuGlow
	fun getSliderBorderColor()       = sliderBorder
	fun getSliderBallColor()         = sliderBall
	fun getSpinnerApproachCircleColor() = spinnerApproachCircle
	fun getSongSelectActiveTextColor()  = songSelectActiveText
	fun getSongSelectInactiveTextColor()= songSelectInactiveText
	fun getStarBreakAdditiveColor()  = starBreakAdditive
	fun getInputOverlayText()        = inputOverlayText
	fun getHitCircleFontPrefix()     = hitCirclePrefix
	fun getHitCircleFontOverlap()    = hitCircleOverlap
	fun getScoreFontPrefix()         = scorePrefix
	fun getScoreFontOverlap()        = scoreOverlap
	fun getComboFontPrefix()         = comboPrefix
	fun getComboFontOverlap()        = comboOverlap
}
