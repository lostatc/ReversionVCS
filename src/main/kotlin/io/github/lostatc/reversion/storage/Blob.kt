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
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * A piece of binary data.
 */
interface Blob {
    /**
     * An input stream for reading the data in this blob.
     */
    val inputStream: InputStream

    /**
     * The checksum of the data in this blob.
     */
    val checksum: Checksum

    /**
     * Returns whether the hash of [inputStream] is equal to [checksum].
     *
     * @param [algorithm] The name of the hash algorithm to use. This accepts any algorithm accepted by [MessageDigest].
     */
    fun isValid(algorithm: String): Boolean = checksum == Checksum.fromInputStream(inputStream, algorithm)

    companion object {
        /**
         * Creates a [Blob] containing data from the given [inputStream] with the given [checksum].
         */
        fun of(inputStream: InputStream, checksum: Checksum): Blob = SimpleBlob(inputStream, checksum)

        /**
         * Creates a [Blob] containing data from the given [inputStream] with a computed checksum.
         *
         * The checksum is computed from the data in the [inputStream] using the given [algorithm]. The [algorithm] can
         * be the name of any algorithm accepted by [MessageDigest].
         */
        fun of(inputStream: InputStream, algorithm: String = "SHA-256"): Blob = LazyBlob(inputStream, algorithm)

        /**
         * Creates a list of blobs containing data from the file at the given [path].
         *
         * Concatenating the contents of each blob together produces the original file. This accepts any [algorithm]
         * accepted by [MessageDigest].
         *
         * @param [path] The path of the file.
         * @param [blockSize] The maximum number of bytes in each blob.
         * @param [algorithm] The name of the algorithm to compute the checksum with.
         */
        fun fromFile(path: Path, blockSize: Long = Long.MAX_VALUE, algorithm: String = "SHA-256"): List<Blob> {
            val blobs = mutableListOf<LazyBlob>()

            Files.newByteChannel(path).use {
                // Iterate over each [blockSize] byte chunk of the file.
                while (it.position() < it.size()) {
                    // Create a blob that starts at the current position and ends [blockSize] bytes after it.
                    val blob = LazyBlob(
                        BoundedInputStream(Channels.newInputStream(it), blockSize),
                        algorithm
                    )

                    blobs.add(blob)

                    // Advance the position of the channel by [blockSize] bytes.
                    it.position(it.position() + blockSize)
                }
            }

            return blobs
        }
    }
}

/**
 * An simple data class implementation of [Blob].
 */
private data class SimpleBlob(override val inputStream: InputStream, override val checksum: Checksum) : Blob

/**
 * An implementation of [Blob] that lazily computes the [checksum] from the [inputStream].
 */
private data class LazyBlob(override val inputStream: InputStream, val algorithm: String) : Blob {
    override val checksum: Checksum by lazy { Checksum.fromInputStream(inputStream, algorithm) }
}
