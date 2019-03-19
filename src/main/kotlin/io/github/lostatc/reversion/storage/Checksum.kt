/*
 * Copyright © 2019 Garrett Powell
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
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*

/**
 * The SHA256 checksum of a file.
 */
class Checksum(val bytes: ByteArray) {
    /**
     * Returns this checksum as a hexadecimal string.
     */
    fun toHex(): String = bytes.joinToString(separator = "") { String.format("%02x", it) }

    override fun equals(other: Any?): Boolean = if (other is Checksum) bytes contentEquals other.bytes else false

    override fun hashCode(): Int = Objects.hash(bytes)

    override fun toString(): String = toHex()

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
         */
        fun fromInputStream(inputStream: InputStream): Checksum {
            val messageDigest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(inputStream, messageDigest).use { it.readAllBytes() }
            return Checksum(messageDigest.digest())
        }
    }
}