package itdelatrisu.opsu.io

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class OsuWriter(dest: OutputStream) {
    constructor(file: File) : this(FileOutputStream(file))

    private val writer = DataOutputStream(BufferedOutputStream(dest))

    fun getOutputStream(): OutputStream = writer
    fun close() = writer.close()

    fun write(v: Byte) = writer.writeByte(v.toInt())
    fun write(v: ByteArray) = writer.write(v)

    fun write(v: Short) =
        writer.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array())

    fun write(v: Int) =
        writer.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())

    fun write(v: Long) =
        writer.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array())

    fun write(v: Float) = writer.writeFloat(v)
    fun write(v: Double) = writer.writeDouble(v)
    fun write(v: Boolean) = writer.writeBoolean(v)

    fun writeULEB128(i: Int) {
        var value = i
        do {
            var b = (value and 0x7F).toByte()
            value = value ushr 7
            if (value != 0) b = (b.toInt() or (1 shl 7)).toByte()
            writer.writeByte(b.toInt())
        } while (value != 0)
    }

    fun write(s: String?) {
        if (s.isNullOrEmpty()) {
            writer.writeByte(0x00)
        } else {
            writer.writeByte(0x0B)
            writeULEB128(s.length)
            writer.writeBytes(s)
        }
    }

    fun write(date: Date) {
        val TICKS_AT_EPOCH = 621355968000000000L
        val TICKS_PER_MILLISECOND = 10000L
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).also { it.time = date }
        write(TICKS_AT_EPOCH + calendar.timeInMillis * TICKS_PER_MILLISECOND)
    }
}
