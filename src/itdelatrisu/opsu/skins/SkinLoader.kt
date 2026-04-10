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

import itdelatrisu.opsu.ErrorHandler
import itdelatrisu.opsu.Utils
import org.newdawn.slick.Color
import org.newdawn.slick.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

object SkinLoader {

    private const val CONFIG_FILENAME = "skin.ini"

    fun getSkinDirectories(root: File): Array<File> =
        root.listFiles()?.filter { it.isDirectory }?.toTypedArray() ?: emptyArray()

    fun loadSkin(dir: File): Skin {
        val skinFile = File(dir, CONFIG_FILENAME)
        val skin = Skin(dir)
        if (!skinFile.isFile) return skin

        try {
            BufferedReader(InputStreamReader(FileInputStream(skinFile), "UTF-8")).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    line = line.trim()
                    if (!isValidLine(line)) { line = reader.readLine(); continue }
                    when (line) {
                        "[General]" -> {
                            line = reader.readLine()
                            while (line != null) {
                                line = line.trim()
                                if (!isValidLine(line)) { line = reader.readLine(); continue }
                                if (line[0] == '[') break
                                val tokens = tokenize(line) ?: run { line = reader.readLine(); continue }
                                try {
                                    when (tokens[0]) {
                                        "Name"                      -> skin.name = tokens[1]
                                        "Author"                    -> skin.author = tokens[1]
                                        "Version"                   -> skin.version = if (tokens[1].equals("latest", ignoreCase = true)) Skin.LATEST_VERSION else tokens[1].toFloat()
                                        "SliderBallFlip"            -> skin.sliderBallFlip = Utils.parseBoolean(tokens[1])
                                        "CursorRotate"              -> skin.cursorRotate = Utils.parseBoolean(tokens[1])
                                        "CursorExpand"              -> skin.cursorExpand = Utils.parseBoolean(tokens[1])
                                        "CursorCentre"              -> skin.cursorCentre = Utils.parseBoolean(tokens[1])
                                        "SliderBallFrames"          -> skin.sliderBallFrames = tokens[1].toInt()
                                        "HitCircleOverlayAboveNumber" -> skin.hitCircleOverlayAboveNumber = Utils.parseBoolean(tokens[1])
                                        "spinnerFrequencyModulate"  -> skin.spinnerFrequencyModulate = Utils.parseBoolean(tokens[1])
                                        "LayeredHitSounds"          -> skin.layeredHitSounds = Utils.parseBoolean(tokens[1])
                                        "SpinnerFadePlayfield"      -> skin.spinnerFadePlayfield = Utils.parseBoolean(tokens[1])
                                        "SpinnerNoBlink"            -> skin.spinnerNoBlink = Utils.parseBoolean(tokens[1])
                                        "AllowSliderBallTint"       -> skin.allowSliderBallTint = Utils.parseBoolean(tokens[1])
                                        "AnimationFramerate"        -> skin.animationFramerate = tokens[1].toInt()
                                        "CursorTrailRotate"         -> skin.cursorTrailRotate = Utils.parseBoolean(tokens[1])
                                        "CustomComboBurstSounds"    -> skin.customComboBurstSounds = tokens[1].split(",").map { it.toInt() }.toIntArray()
                                        "ComboBurstRandom"          -> skin.comboBurstRandom = Utils.parseBoolean(tokens[1])
                                        "SliderStyle"               -> skin.sliderStyle = tokens[1].toByte()
                                    }
                                } catch (e: Exception) {
                                    Log.warn("Failed to read line '$line' for file '${skinFile.absolutePath}'.", e)
                                }
                                line = reader.readLine()
                            }
                        }
                        "[Colours]" -> {
                            val colors = mutableListOf<Color>()
                            line = reader.readLine()
                            while (line != null) {
                                line = line.trim()
                                if (!isValidLine(line)) { line = reader.readLine(); continue }
                                if (line[0] == '[') break
                                val tokens = tokenize(line) ?: run { line = reader.readLine(); continue }
                                try {
                                    val rgb = tokens[1].split(",")
                                    val color = Color(rgb[0].trim().toInt(), rgb[1].trim().toInt(), rgb[2].trim().toInt())
                                    when (tokens[0]) {
                                        "Combo1", "Combo2", "Combo3", "Combo4",
                                        "Combo5", "Combo6", "Combo7", "Combo8" -> colors.add(color)
                                        "MenuGlow"             -> skin.menuGlow = color
                                        "SliderBorder"         -> skin.sliderBorder = color
                                        "SliderBall"           -> skin.sliderBall = color
                                        "SpinnerApproachCircle"-> skin.spinnerApproachCircle = color
                                        "SongSelectActiveText" -> skin.songSelectActiveText = color
                                        "SongSelectInactiveText"-> skin.songSelectInactiveText = color
                                        "StarBreakAdditive"    -> skin.starBreakAdditive = color
                                        "InputOverlayText"     -> skin.inputOverlayText = color
                                    }
                                } catch (e: Exception) {
                                    Log.warn("Failed to read color '$line' for file '${skinFile.absolutePath}'.", e)
                                }
                                line = reader.readLine()
                            }
                            if (colors.isNotEmpty()) skin.combo = colors.toTypedArray()
                        }
                        "[Fonts]" -> {
                            line = reader.readLine()
                            while (line != null) {
                                line = line.trim()
                                if (!isValidLine(line)) { line = reader.readLine(); continue }
                                if (line[0] == '[') break
                                val tokens = tokenize(line) ?: run { line = reader.readLine(); continue }
                                try {
                                    when (tokens[0]) {
                                        "HitCirclePrefix"  -> skin.hitCirclePrefix = tokens[1]
                                        "HitCircleOverlap" -> skin.hitCircleOverlap = tokens[1].toInt()
                                        "ScorePrefix"      -> skin.scorePrefix = tokens[1]
                                        "ScoreOverlap"     -> skin.scoreOverlap = tokens[1].toInt()
                                        "ComboPrefix"      -> skin.comboPrefix = tokens[1]
                                        "ComboOverlap"     -> skin.comboOverlap = tokens[1].toInt()
                                    }
                                } catch (e: Exception) {
                                    Log.warn("Failed to read font '$line' for file '${skinFile.absolutePath}'.", e)
                                }
                                line = reader.readLine()
                            }
                        }
                        else -> line = reader.readLine()
                    }
                }
            }
        } catch (e: Exception) {
            ErrorHandler.error("Failed to read file '${skinFile.absolutePath}'.", e, false)
        }

        return skin
    }

    private fun isValidLine(line: String) = line.length > 1 && !line.startsWith("//")

    private fun tokenize(line: String): Array<String>? {
        val index = line.indexOf(':')
        if (index == -1) { Log.debug("Failed to tokenize line: '$line'."); return null }
        return arrayOf(line.substring(0, index).trim(), line.substring(index + 1).trim())
    }
}
