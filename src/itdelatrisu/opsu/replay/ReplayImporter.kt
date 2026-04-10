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

package itdelatrisu.opsu.replay

import itdelatrisu.opsu.beatmap.BeatmapSetList
import itdelatrisu.opsu.db.ScoreDB
import itdelatrisu.opsu.options.Options
import itdelatrisu.opsu.ui.UI
import org.newdawn.slick.util.Log
import java.io.File
import java.io.FilenameFilter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ReplayImporter {
    private const val FAILED_IMPORT_DIR = "InvalidReplays"

    private var files: Array<File>? = null
    private var fileIndex = -1

    fun importAllReplaysFromDir(dir: File) {
        files = dir.listFiles(FilenameFilter { _, name ->
            name.lowercase().endsWith(".osr")
        })

        val filesLocal = files
        if (filesLocal.isNullOrEmpty()) {
            files = null
            return
        }

        val replayDir = Options.getReplayDir()
        if (!replayDir.isDirectory && !replayDir.mkdir()) {
            Log.error("Failed to create replay directory '${replayDir.absolutePath}'.")
            return
        }

        var importCount = 0
        filesLocal.forEachIndexed { index, file ->
            fileIndex = index
            val replay = Replay(file)
            try {
                replay.loadHeader()
            } catch (e: IOException) {
                moveToFailedDirectory(file)
                Log.error("Failed to import replay '${file.name}'.", e)
                return@forEachIndexed
            }

            val beatmap = BeatmapSetList.get().getBeatmapFromHash(replay.beatmapHash)
            if (beatmap != null) {
                ScoreDB.addScore(replay.getScoreData(beatmap))
                val dest = File(replayDir, "${replay.getReplayFilename()}.osr")
                try {
                    Files.move(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    importCount++
                } catch (e: IOException) {
                    Log.warn("Failed to move replay '${file.name}' to '$replayDir'.", e)
                }
            } else {
                moveToFailedDirectory(file)
                Log.error("Failed to import replay '${file.name}': beatmap not found.")
            }
        }

        fileIndex = -1
        files = null

        if (importCount > 0)
            UI.getNotificationManager().sendNotification(
                "Imported $importCount replay${if (importCount == 1) "" else "s"}."
            )
    }

    private fun moveToFailedDirectory(file: File) {
        val dir = File(Options.getImportDir(), FAILED_IMPORT_DIR).also { it.mkdir() }
        try {
            Files.move(file.toPath(), File(dir, file.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            Log.warn("Failed to move replay '${file.name}' to failed directory.", e)
        }
    }

    fun getCurrentFileName(): String? {
        val f = files ?: return null
        return if (fileIndex == -1) null else f[fileIndex].name
    }

    fun getLoadingProgress(): Int {
        val f = files ?: return -1
        return if (fileIndex == -1) -1 else (fileIndex + 1) * 100 / f.size
    }
}
