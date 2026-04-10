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
