/*
 * Copyright © 2019 Wren Powell
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

package io.github.lostatc.reversion.api

import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Path
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
        get() = Hex.encodeHexString(bytes)

    override fun equals(other: Any?): Boolean = if (other is Checksum) bytes contentEquals other.bytes else false

    override fun hashCode(): Int = Objects.hash(bytes)

    override fun toString(): String = hex

    companion object {
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
         * Calculates a [Checksum] of the data from the given [inputStream].
         *
         * This accepts any [algorithm] accepted by [MessageDigest].
         *
         * @param [inputStream] The source of the data to calculate the checksum of.
         * @param [algorithm] The name of the hash algorithm to use.
         */
        fun fromInputStream(inputStream: InputStream, algorithm: String): Checksum =
            Checksum(DigestUtils(algorithm).digest(inputStream))

        /**
         * Calculates a [Checksum] of the file at the given [path].
         *
         * This accepts any [algorithm] accepted by [MessageDigest].
         *
         * @param [path] The path of the file to calculate the checksum of.
         * @param [algorithm] The name of the hash algorithm to use.
         */
        fun fromFile(path: Path, algorithm: String): Checksum =
            Checksum(DigestUtils(algorithm).digest(path.toFile()))
    }
}