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

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

class OsuReader(source: InputStream) {
    constructor(file: File) : this(FileInputStream(file))

    private val reader = DataInputStream(BufferedInputStream(source))

    fun getInputStream(): InputStream = reader
    fun close() = reader.close()

    fun readByte(): Byte = reader.readByte()

    fun readShort(): Short =
        ByteBuffer.wrap(ByteArray(2).also { reader.readFully(it) })
            .order(ByteOrder.LITTLE_ENDIAN).short

    fun readInt(): Int =
        ByteBuffer.wrap(ByteArray(4).also { reader.readFully(it) })
            .order(ByteOrder.LITTLE_ENDIAN).int

    fun readLong(): Long =
        ByteBuffer.wrap(ByteArray(8).also { reader.readFully(it) })
            .order(ByteOrder.LITTLE_ENDIAN).long

    fun readSingle(): Float =
        ByteBuffer.wrap(ByteArray(4).also { reader.readFully(it) })
            .order(ByteOrder.LITTLE_ENDIAN).float

    fun readDouble(): Double =
        ByteBuffer.wrap(ByteArray(8).also { reader.readFully(it) })
            .order(ByteOrder.LITTLE_ENDIAN).double

    fun readBoolean(): Boolean = reader.readBoolean()

    fun readULEB128(): Int {
        var value = 0
        for (shift in 0 until 32 step 7) {
            val b = reader.readByte()
            value = value or ((b.toInt() and 0x7F) shl shift)
            if (b >= 0) return value
        }
        throw java.io.IOException("ULEB128 too large")
    }

    fun readString(): String {
        return when (val kind = reader.readByte()) {
            0.toByte() -> ""
            0x0B.toByte() -> {
                val length = readULEB128()
                if (length == 0) ""
                else String(ByteArray(length).also { reader.readFully(it) }, Charsets.UTF_8)
            }
            else -> throw java.io.IOException("String format error: Expected 0x0B or 0x00, found 0x%02X".format(kind.toInt() and 0xFF))
        }
    }

    fun readDate(): Date {
        val ticks = readLong()
        val TICKS_AT_EPOCH = 621355968000000000L
        val TICKS_PER_MILLISECOND = 10000L
        return Date((ticks - TICKS_AT_EPOCH) / TICKS_PER_MILLISECOND)
    }
}
