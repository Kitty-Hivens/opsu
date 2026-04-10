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

import itdelatrisu.opsu.video.FFmpeg
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarFile

class NativeLoader(private val nativeDir: File) {

    fun loadNatives() {
        if (!nativeDir.exists()) nativeDir.mkdir()

        val jarFile = Utils.getJarFile() ?: return

        jarFile.use { jar ->
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement() ?: break
                val file = File(nativeDir, entry.name)
                if (isNativeFile(entry.name) && !entry.isDirectory
                    && !entry.name.contains('/') && !file.exists()
                ) {
                    jar.getInputStream(jar.getEntry(entry.name)).use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun isNativeFile(name: String): Boolean {
        val lower = name.lowercase()
        val os = System.getProperty("os.name")
        return when {
            os.startsWith("Win")                        -> lower.endsWith(".dll")
            os.startsWith("Linux")                      -> lower.endsWith(".so")
            os.startsWith("Mac") || os.startsWith("Darwin") -> lower.endsWith(".dylib") || lower.endsWith(".jnilib")
            else                                        -> false
        } || lower == FFmpeg.DEFAULT_NATIVE_FILENAME.lowercase()
    }
}
