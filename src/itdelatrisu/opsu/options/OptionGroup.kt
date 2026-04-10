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

package itdelatrisu.opsu.options

import itdelatrisu.opsu.GameImage
import org.newdawn.slick.Image

class OptionGroup private constructor(
    private val category: String,
    private val options: Array<Options.GameOption>?,
    private val icon: GameImage?,
) {
    private var visible = true

    constructor(category: String, options: Array<Options.GameOption>) : this(category, options, null)
    constructor(category: String, icon: GameImage) : this(category, null, icon)

    fun getName()    = category
    fun getOptions() = options
    fun getIcon(): Image = icon!!.getImage()
    fun getOption(i: Int) = options!![i]
    fun isVisible()  = visible
    fun setVisible(v: Boolean) { visible = v }

    companion object {
        @JvmField
        val ALL_OPTIONS: Array<OptionGroup> = arrayOf(
            OptionGroup("General",    GameImage.MENU_NAV_GENERAL),
            OptionGroup("LANGUAGE",   arrayOf(Options.SHOW_UNICODE)),
            OptionGroup("UPDATES",    arrayOf(Options.DISABLE_UPDATER)),
            OptionGroup("Graphics",   GameImage.MENU_NAV_GRAPHICS),
            OptionGroup("LAYOUT",     arrayOf(Options.SCREEN_RESOLUTION, Options.FULLSCREEN)),
            OptionGroup("RENDERER",   arrayOf(Options.TARGET_FPS, Options.SHOW_FPS)),
            OptionGroup("DETAIL SETTINGS", arrayOf(
                Options.SNAKING_SLIDERS, Options.ENABLE_VIDEOS,
                Options.SHOW_COMBO_BURSTS, Options.SHOW_HIT_LIGHTING,
                Options.SHOW_PERFECT_HIT, Options.SHOW_FOLLOW_POINTS,
                Options.SCREENSHOT_FORMAT,
            )),
            OptionGroup("EXPERIMENTAL SLIDERS", arrayOf(
                Options.EXPERIMENTAL_SLIDERS, Options.EXPERIMENTAL_SLIDERS_MERGE,
                Options.EXPERIMENTAL_SLIDERS_SHRINK, Options.EXPERIMENTAL_SLIDERS_CAPS,
            )),
            OptionGroup("MAIN MENU",  arrayOf(Options.DYNAMIC_BACKGROUND, Options.PARALLAX, Options.ENABLE_THEME_SONG)),
            OptionGroup("Gameplay",   GameImage.MENU_NAV_GAMEPLAY),
            OptionGroup("GENERAL",    arrayOf(
                Options.BACKGROUND_DIM, Options.FORCE_DEFAULT_PLAYFIELD,
                Options.SHOW_HIT_ERROR_BAR, Options.ALWAYS_SHOW_KEY_OVERLAY,
            )),
            OptionGroup("Audio",      GameImage.MENU_NAV_AUDIO),
            OptionGroup("VOLUME",     arrayOf(
                Options.MASTER_VOLUME, Options.MUSIC_VOLUME, Options.EFFECT_VOLUME,
                Options.HITSOUND_VOLUME, Options.DISABLE_GAMEPLAY_SOUNDS, Options.DISABLE_SOUNDS,
            )),
            OptionGroup("OFFSET ADJUSTMENT", arrayOf(Options.MUSIC_OFFSET)),
            OptionGroup("Skin",       GameImage.MENU_NAV_SKIN),
            OptionGroup("SKIN",       arrayOf(
                Options.SKIN, Options.LOAD_HD_IMAGES, Options.IGNORE_BEATMAP_SKINS,
                Options.FORCE_SKIN_CURSOR, Options.CURSOR_SIZE, Options.DISABLE_CURSOR,
            )),
            OptionGroup("Input",      GameImage.MENU_NAV_INPUT),
            OptionGroup("MOUSE",      arrayOf(Options.DISABLE_MOUSE_WHEEL, Options.DISABLE_MOUSE_BUTTONS)),
            OptionGroup("KEYBOARD",   arrayOf(Options.KEY_LEFT, Options.KEY_RIGHT)),
            OptionGroup("Custom",     GameImage.MENU_NAV_CUSTOM),
            OptionGroup("DIFFICULTY", arrayOf(
                Options.FIXED_CS, Options.FIXED_HP, Options.FIXED_AR,
                Options.FIXED_OD, Options.FIXED_SPEED,
            )),
            OptionGroup("SEEKING",    arrayOf(Options.CHECKPOINT, Options.REPLAY_SEEKING)),
            OptionGroup("MISCELLANEOUS", arrayOf(Options.ENABLE_WATCH_SERVICE, Options.LOAD_VERBOSE)),
        )
    }
}
