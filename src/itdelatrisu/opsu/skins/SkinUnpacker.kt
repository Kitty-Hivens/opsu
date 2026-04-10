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

import itdelatrisu.opsu.Utils
import itdelatrisu.opsu.ui.UI
import java.io.File

object SkinUnpacker {

    private var fileIndex = -1
    private var files: Array<File>? = null

    @JvmStatic
    fun unpackAllFiles(root: File, dest: File): Array<File> {
        val dirs = mutableListOf<File>()

        files = root.listFiles { _, name -> name.lowercase().endsWith(".osk") }
        if (files.isNullOrEmpty()) { files = null; return emptyArray() }

        for ((i, file) in files!!.withIndex()) {
            fileIndex = i
            val dirName = file.nameWithoutExtension
            val skinDir = File(dest, dirName)
            if (!skinDir.isDirectory) {
                skinDir.mkdir()
                Utils.unzip(file, skinDir)
                file.delete()
                dirs.add(skinDir)
            }
        }

        fileIndex = -1
        files = null

        if (dirs.isNotEmpty())
            UI.getNotificationManager().sendNotification(
                "Imported ${dirs.size} new skin${if (dirs.size == 1) "" else "s"}."
            )

        return dirs.toTypedArray()
    }

    @JvmStatic
    fun getCurrentFileName(): String? {
        val f = files ?: return null
        return if (fileIndex == -1) null else f[fileIndex].name
    }

    @JvmStatic
    fun getUnpackerProgress(): Int {
        val f = files ?: return -1
        return if (fileIndex == -1) -1 else (fileIndex + 1) * 100 / f.size
    }
}
