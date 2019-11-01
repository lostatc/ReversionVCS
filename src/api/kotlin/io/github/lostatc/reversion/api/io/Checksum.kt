/*
 * Copyright © 2019 Garrett Powell
 *
 * This file is part of Reversion.
 *
 * Reversion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Reversion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Reversion.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.lostatc.reversion.api.io

import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.file.Path
import java.security.MessageDigest

/**
 * The size of the buffer to use for I/O operations.
 */
private const val BUFFER_SIZE: Int = 4096

/**
 * A checksum of a file.
 */
class Checksum(private val bytes: ByteArray) {
    /**
     * Returns a byte array containing the bytes of the checksum.
     */
    fun toArray(): ByteArray = bytes.copyOf()

    /**
     * Returns a read-only byte buffer containing the bytes of the checksum.
     */
    fun toBuffer(): ByteBuffer = ByteBuffer.wrap(toArray()).asReadOnlyBuffer()

    /**
     * Returns a hexadecimal string representing this checksum.
     */
    fun toHex(): String = Hex.encodeHexString(bytes)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Checksum) return false
        return bytes contentEquals other.bytes
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = toHex()

    companion object {
        /**
         * The hash algorithm to use for calculating checksums.
         */
        const val algorithm: String = "SHA-256"

        /**
         * Creates a [Checksum] from the given hexadecimal [hash].
         *
         * @throws [IllegalArgumentException] The given string is not a valid hexadecimal string.
         */
        fun fromHex(hash: String): Checksum = try {
            Checksum(Hex.decodeHex(hash))
        } catch (e: DecoderException) {
            throw IllegalArgumentException("The given hexadecimal string is invalid.", e)
        }

        /**
         * Calculates a SHA-256 [Checksum] of the data from the given [channel].
         *
         * It is the responsibility of the caller to close the [channel].
         */
        fun fromChannel(channel: ReadableByteChannel): Checksum {
            val digest = MessageDigest.getInstance(algorithm)
            val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)

            while (channel.read(buffer) != -1) {
                buffer.flip()
                digest.update(buffer)
                buffer.clear()
            }

            return Checksum(digest.digest())
        }

        /**
         * Calculates a SHA-256 [Checksum] of the file at the given [path].
         *
         * @throws [IOException] An I/O error occurred.
         */
        fun fromFile(path: Path): Checksum = FileChannel.open(path).use {
            it.sharedLock()
            fromChannel(it)
        }
    }
}
