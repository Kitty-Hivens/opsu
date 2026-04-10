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

package itdelatrisu.opsu.io

import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class MD5InputStreamWrapper(private val input: InputStream) : InputStream() {
    private val md: MessageDigest = MessageDigest.getInstance("MD5")
    private var eof = false
    private var md5: String? = null

    override fun read(): Int {
        val b = input.read()
        if (b >= 0) md.update(b.toByte()) else eof = true
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val bytesRead = input.read(b, off, len)
        if (bytesRead >= 0) md.update(b, off, bytesRead) else eof = true
        return bytesRead
    }

    override fun read(b: ByteArray): Int = read(b, 0, b.size)
    override fun available(): Int = input.available()
    override fun close(): Unit = input.close()
    override fun mark(readlimit: Int) = input.mark(readlimit)
    override fun markSupported(): Boolean = input.markSupported()
    override fun reset(): Unit = throw RuntimeException("reset() not implemented")
    override fun skip(n: Long): Long = throw RuntimeException("skip() not implemented")

    fun getMD5(): String {
        md5?.let { return it }
        if (!eof) {
            val buf = ByteArray(0x1000)
            while (!eof) read(buf)
        }
        return md.digest()
            .joinToString("") { "%02x".format(it) }
            .also { md5 = it }
    }
}
