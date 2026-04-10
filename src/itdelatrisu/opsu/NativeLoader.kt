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
