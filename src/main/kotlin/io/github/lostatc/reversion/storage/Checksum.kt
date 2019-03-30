/*
 * Copyright © 2019 Wren Powell
 *
 * This file is part of reversion.
 *
 * reversion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * reversion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with reversion.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.lostatc.reversion.storage

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*

/**
 * A checksum of a file.
 */
class Checksum(private val bytes: ByteArray) {
    /**
     * A byte array containing the bytes of the checksum.
     */
    val array: ByteArray
        get() = bytes.copyOf()

    /**
     * A read-only byte buffer containing the bytes of the checksum.
     */
    val buffer: ByteBuffer
        get() = ByteBuffer.wrap(array).asReadOnlyBuffer()

    /**
     * A hexadecimal string representing this checksum.
     */
    val hex: String
        get() = bytes.joinToString(separator = "") { String.format("%02x", it) }

    override fun equals(other: Any?): Boolean = if (other is Checksum) bytes contentEquals other.bytes else false

    override fun hashCode(): Int = Objects.hash(bytes)

    override fun toString(): String = hex

    companion object {
        /**
         * Creates a [Checksum] from the given hexadecimal [hash].
         *
         * @throws [IllegalArgumentException] The given string is not a valid hexadecimal string.
         */
        fun fromHex(hash: String): Checksum = Checksum(
            hash
                .chunked(2)
                .map { it.toInt(radix = 16) }
                .map { it.toByte() }
                .toByteArray()
        )

        /**
         * Calculates a [Checksum] of the data from the given [inputStream].
         *
         * This accepts any [algorithm] accepted by [MessageDigest].
         *
         * @param [inputStream] The source of the data to calculate the checksum of.
         * @param [algorithm] The name of the hash algorithm to use.
         */
        fun fromInputStream(inputStream: InputStream, algorithm: String = "SHA-256"): Checksum {
            val messageDigest = MessageDigest.getInstance(algorithm)
            DigestInputStream(inputStream, messageDigest).use { it.readAllBytes() }
            return Checksum(messageDigest.digest())
        }

        /**
         * Calculates a [Checksum] of the file at the given [path].
         *
         * This accepts any [algorithm] accepted by [MessageDigest].
         *
         * @param [path] The path of the file to calculate the checksum of.
         * @param [algorithm] The name of the hash algorithm to use.
         */
        fun fromFile(path: Path, algorithm: String = "SHA-256"): Checksum =
            fromInputStream(Files.newInputStream(path), algorithm)
    }
}