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

import itdelatrisu.opsu.ui.MenuButton
import itdelatrisu.opsu.ui.animations.AnimationEquation
import org.newdawn.slick.Color
import org.newdawn.slick.Image
import org.newdawn.slick.Input

enum class GameMod(
	private val category: Category,
	private val categoryIndex: Int,
	private val image: GameImage,
	private val abbrev: String,
	private val bit: Int,
	private val key: Int,
	private val multiplier: Float,
	private val modName: String,
	private val description: String,
	) {
	EASY         (Category.EASY,    0, GameImage.MOD_EASY,         "EZ",  2,    Input.KEY_Q, 0.5f,  "Easy",       "Reduces overall difficulty - larger circles, more forgiving HP drain, less accuracy required."),
	NO_FAIL      (Category.EASY,    1, GameImage.MOD_NO_FAIL,      "NF",  1,    Input.KEY_W, 0.5f,  "NoFail",     "You can't fail.  No matter what."),
	HALF_TIME    (Category.EASY,    2, GameImage.MOD_HALF_TIME,    "HT",  256,  Input.KEY_E, 0.3f,  "HalfTime",   "Less zoom."),
	HARD_ROCK    (Category.HARD,    0, GameImage.MOD_HARD_ROCK,    "HR",  16,   Input.KEY_A, 1.06f, "HardRock",   "Everything just got a bit harder..."),
	SUDDEN_DEATH (Category.HARD,    1, GameImage.MOD_SUDDEN_DEATH, "SD",  32,   Input.KEY_S, 1f,    "SuddenDeath","Miss a note and fail."),
	DOUBLE_TIME  (Category.HARD,    2, GameImage.MOD_DOUBLE_TIME,  "DT",  64,   Input.KEY_D, 1.12f, "DoubleTime", "Zoooooooooom."),
	HIDDEN       (Category.HARD,    3, GameImage.MOD_HIDDEN,       "HD",  8,    Input.KEY_F, 1.06f, "Hidden",     "Play with no approach circles and fading notes for a slight score advantage."),
	FLASHLIGHT   (Category.HARD,    4, GameImage.MOD_FLASHLIGHT,   "FL",  1024, Input.KEY_G, 1.12f, "Flashlight", "Restricted view area."),
	RELAX        (Category.SPECIAL, 0, GameImage.MOD_RELAX,        "RL",  128,  Input.KEY_Z, 0f,    "Relax",      "You don't need to click.\nGive your clicking/tapping finger a break from the heat of things.\n**UNRANKED**"),
	AUTOPILOT    (Category.SPECIAL, 1, GameImage.MOD_AUTOPILOT,    "AP",  8192, Input.KEY_X, 0f,    "Relax2",     "Automatic cursor movement - just follow the rhythm.\n**UNRANKED**"),
	SPUN_OUT     (Category.SPECIAL, 2, GameImage.MOD_SPUN_OUT,     "SO",  4096, Input.KEY_C, 0.9f,  "SpunOut",    "Spinners will be automatically completed."),
	AUTO         (Category.SPECIAL, 3, GameImage.MOD_AUTO,         "",    2048, Input.KEY_V, 1f,    "Autoplay",   "Watch a perfect automated play through the song.");

	// ── Category ──────────────────────────────────────────────────────────────

	enum class Category(
		private val index: Int,
		private val categoryName: String,
		private val color: Color,
		) {
		EASY   (0, "Difficulty Reduction", Color.green),
		HARD   (1, "Difficulty Increase",  Color.red),
		SPECIAL(2, "Special",              Color.white);

		var x: Float = 0f; private set
		var y: Float = 0f; private set

		fun init(width: Int, height: Int) {
			// TODO: LibGDX — пересчитать координаты без Fonts.LARGE
			val offsetY = GameImage.MOD_EASY.getImage().height * 1.5f
			x = width / 30f
			y = 0f // placeholder
		}

		fun getName()  = categoryName
		fun getColor() = color
		fun getX()     = x
		fun getY()     = y
	}

	// ── Instance state ────────────────────────────────────────────────────────

	var active: Boolean = false
	private set

	// TODO: LibGDX — MenuButton зависит от Slick Image
	private var button: MenuButton? = null

	// ── Instance accessors ────────────────────────────────────────────────────

	fun getAbbreviation() = abbrev
	fun getBit()          = bit
	fun getKey()          = key
	fun getMultiplier()   = multiplier
	fun getName()         = modName
	fun getDescription()  = description
	fun isActive()        = active

	// TODO: LibGDX
	fun getImage(): Image = image.getImage()

	fun draw()                              { button?.draw() }
	fun contains(x: Float, y: Float)        = button?.contains(x, y) ?: false
	fun resetHover()                        { button?.resetHover() }
	fun hoverUpdate(delta: Int, x: Float, y: Float)       { button?.hoverUpdate(delta, x, y) }
	fun hoverUpdate(delta: Int, isHover: Boolean)         { button?.hoverUpdate(delta, isHover) }

	fun toggle(checkInverse: Boolean) {
		active = !active
		scoreMultiplier     = -1f
		speedMultiplier     = -1f
		difficultyMultiplier = -1f

		if (!checkInverse) return

		if (AUTO.active) {
			if (this == AUTO) {
				SPUN_OUT.active      = false
				SUDDEN_DEATH.active  = false
				RELAX.active         = false
				AUTOPILOT.active     = false
			} else if (this == SPUN_OUT || this == SUDDEN_DEATH || this == RELAX || this == AUTOPILOT)
				active = false
		}
		if (active && (this == SUDDEN_DEATH || this == NO_FAIL || this == RELAX || this == AUTOPILOT)) {
			SUDDEN_DEATH.active = false
			NO_FAIL.active      = false
			RELAX.active        = false
			AUTOPILOT.active    = false
			active = true
		}
		if (AUTOPILOT.active && SPUN_OUT.active) {
			if (this == AUTOPILOT) SPUN_OUT.active = false else AUTOPILOT.active = false
		}
		if (EASY.active && HARD_ROCK.active) {
			if (this == EASY) HARD_ROCK.active = false else EASY.active = false
		}
		if (HALF_TIME.active && DOUBLE_TIME.active) {
			if (this == HALF_TIME) DOUBLE_TIME.active = false else HALF_TIME.active = false
		}
	}

	// ── Companion (static) ────────────────────────────────────────────────────

	companion object {
		val SIZE = values().size
		val VALUES_REVERSED: Array<GameMod> = entries.toTypedArray().reversedArray()

		private var scoreMultiplier      = -1f
		private var speedMultiplier      = -1f
		private var difficultyMultiplier = -1f

		@JvmStatic
		fun init(width: Int, height: Int) {
			for (c in Category.values()) c.init(width, height)

			// TODO: LibGDX — пересчитать позиции кнопок без Slick Image.getWidth()
			val baseX   = Category.EASY.x
			val offsetX = GameImage.MOD_EASY.getImage().width * 2.1f
			for (mod in entries) {
				val img = mod.image.getImage()
				mod.button = MenuButton(
					img,
					baseX + offsetX * mod.categoryIndex + img.width / 2f,
					mod.category.y
				).also {
					it.setHoverAnimationDuration(300)
					it.setHoverAnimationEquation(AnimationEquation.IN_OUT_BACK)
					it.setHoverExpand(1.2f)
					it.setHoverRotate(10f)
				}
				mod.active = false
			}

			scoreMultiplier = -1f
			speedMultiplier = -1f
			difficultyMultiplier = -1f
		}

		@JvmStatic
		fun getScoreMultiplier(): Float {
			if (scoreMultiplier < 0f)
				scoreMultiplier = entries.filter { it.isActive() }
                    .fold(1f) { acc, mod -> acc * mod.getMultiplier() }
			return scoreMultiplier
		}

		@JvmStatic
		fun getSpeedMultiplier(): Float {
			if (speedMultiplier < 0f) {
				speedMultiplier = when {
					DOUBLE_TIME.active -> 1.5f
					HALF_TIME.active   -> 0.75f
                    else               -> 1f
				}
			}
			return speedMultiplier
		}

		@JvmStatic
		fun getDifficultyMultiplier(): Float {
			if (difficultyMultiplier < 0f) {
				difficultyMultiplier = when {
					HARD_ROCK.active -> 1.4f
					EASY.active      -> 0.5f
                    else             -> 1f
				}
			}
			return difficultyMultiplier
		}

		@JvmStatic
		fun getModState(): Int =
			entries.filter { it.isActive() }.fold(0) { acc, mod -> acc or mod.getBit() }

		@JvmStatic
		fun loadModState(state: Int) {
			scoreMultiplier = -1f
			speedMultiplier = -1f
			difficultyMultiplier = -1f
			for (mod in entries) mod.active = (state and mod.getBit()) > 0
		}

		@JvmStatic
		fun getModString(state: Int): String {
			val sb = entries
				.filter { (state and it.getBit()) > 0 }
                .joinToString(",") { it.getName() }
			return sb.ifEmpty { "None" }
		}
	}
}
