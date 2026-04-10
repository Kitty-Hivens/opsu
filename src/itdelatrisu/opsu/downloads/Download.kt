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

package itdelatrisu.opsu.downloads

import itdelatrisu.opsu.ErrorHandler
import itdelatrisu.opsu.Utils
import itdelatrisu.opsu.options.Options
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Logger

class Download(remoteURL: String, private val localPath: String, rename: String? = null) {

    enum class Status(val statusName: String) {
        WAITING("Waiting"),
        DOWNLOADING("Downloading"),
        COMPLETE("Complete"),
        CANCELLED("Cancelled"),
        ERROR("Error");

        fun getName() = statusName
    }

    interface DownloadListener {
        fun completed()
        fun error()
    }

    companion object {
        const val CONNECTION_TIMEOUT = 5000
        const val READ_TIMEOUT = 10000
        const val MAX_REDIRECTS = 3
        private const val UPDATE_INTERVAL = 1000
        private const val DOWNLOAD_SPEED_SMOOTHING = 0.25
    }

    private val log = Logger.getLogger(Download::class.java.name)

    private val url: URL?
    private val rename: String? = Utils.cleanFileName(rename, '-')
    private var listener: DownloadListener? = null
    private var requestHeaders: Map<String, String>? = null
    private var disableSSLCertValidation = false

    private var rbc: ReadableByteChannelWrapper? = null
    private var fos: FileOutputStream? = null

    private var contentLength = -1
    var status: Status = Status.WAITING
        private set

    private var lastReadSoFarTime = -1L
    private var lastReadSoFar = -1L
    private var lastDownloadSpeed: String? = null
    private var lastTimeRemaining: String? = null
    private var avgDownloadSpeed = 0L

    val isActive get() = status == Status.WAITING || status == Status.DOWNLOADING

    init {
        url = try {
            URL(remoteURL)
        } catch (e: MalformedURLException) {
            status = Status.ERROR
            ErrorHandler.error("Bad download URL: '$remoteURL'", e, true)
            null
        }
    }

    fun getRemoteURL() = url
    fun getLocalPath() = rename ?: localPath
    fun setListener(listener: DownloadListener) { this.listener = listener }
    fun setRequestHeaders(headers: Map<String, String>) { requestHeaders = headers }
    fun setSSLCertValidation(enabled: Boolean) { disableSSLCertValidation = !enabled }
    fun contentLength() = contentLength

    fun getProgress(): Float = when (status) {
        Status.WAITING    -> 0f
        Status.COMPLETE   -> 100f
        Status.DOWNLOADING -> if (rbc != null && contentLength > 0)
            rbc!!.getReadSoFar().toFloat() / contentLength * 100f else 0f
        else -> -1f
    }

    fun readSoFar(): Long = when (status) {
        Status.COMPLETE    -> rbc?.getReadSoFar() ?: contentLength.toLong()
        Status.DOWNLOADING -> rbc?.getReadSoFar() ?: 0L
        else               -> 0L
    }

    fun getDownloadSpeed(): String? { updateReadSoFar(); return lastDownloadSpeed }
    fun getTimeRemaining(): String? { updateReadSoFar(); return lastTimeRemaining }

    fun isTransferring() =
        rbc != null && rbc!!.isOpen && fos != null && fos!!.channel.isOpen

    fun start(): Thread? {
        if (status != Status.WAITING) return null

        val t = Thread {
            var conn: HttpURLConnection?
            try {
                if (disableSSLCertValidation) Utils.setSSLCertValidation(false)

                var downloadURL = url!!
                var redirectCount = 0
                var isRedirect: Boolean
                do {
                    isRedirect = false
                    conn = downloadURL.openConnection() as HttpURLConnection
                    conn.connectTimeout = CONNECTION_TIMEOUT
                    conn.readTimeout = READ_TIMEOUT
                    conn.useCaches = false
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("User-Agent", Options.USER_AGENT)
                    requestHeaders?.forEach { (k, v) -> conn.setRequestProperty(k, v) }

                    val httpStatus = conn.responseCode
                    if (httpStatus in listOf(
                            HttpURLConnection.HTTP_MOVED_TEMP,
                            HttpURLConnection.HTTP_MOVED_PERM,
                            HttpURLConnection.HTTP_SEE_OTHER,
                            HttpURLConnection.HTTP_USE_PROXY
                        )) {
                        val base = conn.url
                        val location = conn.getHeaderField("Location")
                        val target = location?.let { URL(base, it) }
                        conn.disconnect()

                        val error = when {
                            location == null -> "Redirect without 'location' header from '$base'."
                            target?.protocol !in listOf("http", "https") -> "Redirect to non-HTTP protocol from '$base'."
                            redirectCount > MAX_REDIRECTS -> "Too many redirects from '$base'."
                            else -> null
                        }
                        if (error != null) {
                            ErrorHandler.error(error, null, false)
                            throw IOException()
                        }
                        downloadURL = target!!
                        redirectCount++
                        isRedirect = true
                    }
                } while (isRedirect)

                contentLength = conn.contentLength
            } catch (e: Exception) {
                status = Status.ERROR
                log.warning("Failed to open connection. ${e.message}")
                listener?.error()
                return@Thread
            } finally {
                if (disableSSLCertValidation) Utils.setSSLCertValidation(true)
            }

            try {
                conn.inputStream.use { input ->
                    Channels.newChannel(input).use { channel ->
                        FileOutputStream(localPath).use { fos ->
                            this.rbc = ReadableByteChannelWrapper(channel)
                            this.fos = fos
                            status = Status.DOWNLOADING
                            updateReadSoFar()
                            val bytesRead = fos.channel.transferFrom(rbc, 0, Long.MAX_VALUE)
                            if (status == Status.DOWNLOADING) {
                                if (bytesRead < contentLength) {
                                    status = Status.ERROR
                                    log.warning("Download '$url' failed: $contentLength expected, $bytesRead received.")
                                    listener?.error()
                                    return@Thread
                                }
                                status = Status.COMPLETE
                                rbc!!.close()
                                fos.close()
                                if (rename != null) {
                                    val source = File(localPath).toPath()
                                    Files.move(source, source.resolveSibling(rename), StandardCopyOption.REPLACE_EXISTING)
                                }
                                listener?.completed()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                status = Status.ERROR
                log.warning("Failed to start download. ${e.message}")
                listener?.error()
            }
        }
        t.start()
        return t
    }

    fun cancel() {
        try {
            status = Status.CANCELLED
            val transferring = isTransferring()
            if (rbc?.isOpen == true) rbc!!.close()
            if (fos?.channel?.isOpen == true) fos!!.close()
            if (transferring) File(localPath).takeIf { it.isFile }?.delete()
        } catch (e: Exception) {
            status = Status.ERROR
            ErrorHandler.error("Failed to cancel download.", e, true)
        }
    }

    private fun updateReadSoFar() {
        if (status != Status.DOWNLOADING) {
            lastDownloadSpeed = null
            lastTimeRemaining = null
            avgDownloadSpeed = 0
            return
        }

        if (System.currentTimeMillis() > lastReadSoFarTime + UPDATE_INTERVAL) {
            val readSoFar = readSoFar()
            val now = System.currentTimeMillis()
            val dlspeed = (readSoFar - lastReadSoFar) * 1000 / (now - lastReadSoFarTime)
            if (dlspeed > 0) {
                avgDownloadSpeed = if (avgDownloadSpeed == 0L) dlspeed
                else (DOWNLOAD_SPEED_SMOOTHING * dlspeed + (1 - DOWNLOAD_SPEED_SMOOTHING) * avgDownloadSpeed).toLong()
                lastDownloadSpeed = "${Utils.bytesToString(avgDownloadSpeed)}/s"
                val t = (contentLength - readSoFar) / avgDownloadSpeed
                lastTimeRemaining = if (t >= 3600)
                    "%dh%dm%ds".format(t / 3600, (t / 60) % 60, t % 60)
                else
                    "%dm%ds".format(t / 60, t % 60)
            } else {
                avgDownloadSpeed = 0
                lastDownloadSpeed = "${Utils.bytesToString(0)}/s"
                lastTimeRemaining = "?"
            }
            lastReadSoFarTime = now
            lastReadSoFar = readSoFar
        } else if (lastReadSoFarTime <= 0) {
            lastReadSoFar = readSoFar()
            lastReadSoFarTime = System.currentTimeMillis()
        }
    }
}
