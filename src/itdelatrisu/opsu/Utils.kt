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

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Arrays
import java.util.Scanner
import java.util.jar.JarFile
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import com.sun.jna.platform.FileUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object Utils {

    // ── Illegal filename characters ───────────────────────────────────────────

    private val illegalChars = intArrayOf(
        34, 60, 62, 124, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
        11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
        24, 25, 26, 27, 28, 29, 30, 31, 58, 42, 63, 92, 47
    ).also { Arrays.sort(it) }

    // ── GC ───────────────────────────────────────────────────────────────────

    private val GC_MEMORY_THRESHOLD = 150 * 1_000_000L
    private var baselineMemoryUsed = 0L

    fun gc(force: Boolean) {
        if (!force && getUsedMemory() - baselineMemoryUsed < GC_MEMORY_THRESHOLD) return
        System.gc()
        baselineMemoryUsed = getUsedMemory()
    }

    fun getUsedMemory(): Long {
        val r = Runtime.getRuntime()
        return r.totalMemory() - r.freeMemory()
    }

    // ── Math helpers ──────────────────────────────────────────────────────────

    fun clamp(v: Int, lo: Int, hi: Int): Int = when {
        v < lo -> lo
        v > hi -> hi
        else   -> v
    }

    fun clamp(v: Float, lo: Float, hi: Float): Float = when {
        v < lo -> lo
        v > hi -> hi
        else   -> v
    }

    fun clamp(v: Double, lo: Double, hi: Double): Double = when {
        v < lo -> lo
        v > hi -> hi
        else   -> v
    }

    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a * (1 - t) + b * t

    fun mapDifficultyRange(difficulty: Float, min: Float, mid: Float, max: Float): Float = when {
        difficulty > 5f -> mid + (max - mid) * (difficulty - 5f) / 5f
        difficulty < 5f -> mid - (mid - min) * (5f - difficulty) / 5f
        else            -> mid
    }

    fun standardDeviation(list: List<Int>): Double {
        val avg = list.average().toFloat()
        val variance = list.map { (it - avg) * (it - avg) }.average().toFloat()
        return Math.sqrt(variance.toDouble())
    }

    fun parseBoolean(s: String): Boolean = s.toInt() == 1

    // ── String / file utilities ───────────────────────────────────────────────

    fun cleanFileName(name: String?, replace: Char): String? {
        if (name == null) return null
        val doReplace = replace > 0.toChar() && Arrays.binarySearch(illegalChars, replace.code) < 0
        return buildString {
            for (c in name) {
                if (Arrays.binarySearch(illegalChars, c.code) < 0) append(c)
                else if (doReplace) append(replace)
            }
        }
    }

    fun bytesToString(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return "%.1f %cB".format(bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    fun getTimeString(seconds: Int): String = when {
        seconds < 60   -> if (seconds == 1) "1 second" else "$seconds seconds"
        seconds < 3600 -> "%02d:%02d".format(seconds / 60, seconds % 60)
        else           -> "%02d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60)
    }

    // ── IO ────────────────────────────────────────────────────────────────────

    fun getMD5(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            BufferedInputStream(FileInputStream(file)).use { input ->
                val buf = ByteArray(4096)
                var len: Int
                while (input.read(buf).also { len = it } >= 0)
                    md.update(buf, 0, len)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun deleteDirectory(dir: File?) {
        if (dir == null || !dir.isDirectory) return
        dir.listFiles()?.forEach {
            if (it.isDirectory) deleteDirectory(it) else it.delete()
        }
        dir.delete()
    }

    @Throws(IOException::class)
    fun deleteToTrash(file: File): Boolean {
        if (!file.exists()) throw IOException("File '${file.absolutePath}' does not exist.")
        val fileUtils = FileUtils.getInstance()
        if (fileUtils.hasTrash()) {
            try {
                fileUtils.moveToTrash(arrayOf(file))
                return true
            } catch (e: IOException) {}
        }
        if (file.isDirectory) deleteDirectory(file) else file.delete()
        return false
    }

    fun openInFileManager(file: File) {
        var f = file
        if (f.isFile) {
            val os = System.getProperty("os.name")
            when {
                os.startsWith("Win") -> { Runtime.getRuntime().exec("explorer.exe /select,${f.absolutePath}"); return }
                os.startsWith("Mac") -> { Runtime.getRuntime().exec("open -R ${f.absolutePath}"); return }
            }
            f = f.parentFile
        }
        if (f.isDirectory) {
            val desktop = java.awt.Desktop.getDesktop()
            if (java.awt.Desktop.isDesktopSupported() && desktop.isSupported(java.awt.Desktop.Action.OPEN))
                desktop.open(f)
        }
    }

    fun convertStreamToString(input: InputStream): String =
        Scanner(input).use { if (it.hasNext()) it.useDelimiter("\\A").next() else "" }

    // ── Network ───────────────────────────────────────────────────────────────

    @Throws(IOException::class)
    fun readDataFromUrl(url: URL): String? {
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        conn.useCaches = false
        conn.setRequestProperty("User-Agent", options.Options.USER_AGENT)
        try {
            conn.connect()
        } catch (e: SocketTimeoutException) {
            throw e
        }
        if (Thread.interrupted()) return null
        return try {
            conn.inputStream.use { BufferedReader(InputStreamReader(it)).readText() }
        } catch (e: SocketTimeoutException) {
            throw e
        }
    }

    @Throws(IOException::class, JSONException::class)
    fun readJsonObjectFromUrl(url: URL): JSONObject? =
        readDataFromUrl(url)?.let { JSONObject(it) }

    @Throws(IOException::class, JSONException::class)
    fun readJsonArrayFromUrl(url: URL): JSONArray? =
        readDataFromUrl(url)?.let { JSONArray(it) }

    fun setSSLCertValidation(enabled: Boolean) {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        })
        try {
            val sc = SSLContext.getInstance("SSL")
            sc.init(null, if (enabled) null else trustAllCerts, null)
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        } catch (e: Exception) {}
    }

    // ── JAR ──────────────────────────────────────────────────────────────────

    fun isJarRunning(): Boolean =
        Opsu::class.java.getResource("${Opsu::class.java.simpleName}.class")
            .toString().startsWith("jar:")

    fun getJarFile(): JarFile? {
        if (!isJarRunning()) return null
        return try {
            JarFile(File(Opsu::class.java.protectionDomain.codeSource.location.toURI()), false)
        } catch (e: Exception) {
            null
        }
    }

    fun getRunningDirectory(): File? = try {
        File(Opsu::class.java.protectionDomain.codeSource.location.toURI().path)
    } catch (e: Exception) { null }

    fun getWorkingDirectory(): File = Paths.get(".").toAbsolutePath().normalize().toFile()

    fun getGitHash(): String? {
        if (isJarRunning()) return null
        val f = File(".git/refs/remotes/origin/master")
        if (!f.isFile) return null
        return try {
            BufferedReader(FileReader(f)).use { reader ->
                val sha = CharArray(40)
                if (reader.read(sha, 0, 40) < 40) return null
                if (sha.any { Character.digit(it, 16) == -1 }) return null
                String(sha)
            }
        } catch (e: IOException) { null }
    }

    // ── Clipboard ─────────────────────────────────────────────────────────────

    fun copyToClipboard(text: String) {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }

    fun setExecutable(file: File) {
        if (!Files.isExecutable(file.toPath()))
            file.setExecutable(true)
    }

    // ── Slick-dependent stubs ─────────────────────────────────────────────────

    // TODO: LibGDX — init(container, game)
    // TODO: LibGDX — drawCentered(anim, x, y)
    // TODO: LibGDX — getLuminance(color)
    // TODO: LibGDX — isGameKeyPressed()
    // TODO: LibGDX — takeScreenShot()
}
