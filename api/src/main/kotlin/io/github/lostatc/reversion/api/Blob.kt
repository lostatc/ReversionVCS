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

import org.apache.commons.io.input.BoundedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*

/**
 * Returns an [Enumeration] for iterating over the contents of a [Sequence].
 */
private fun <T> Sequence<T>.asEnumeration(): Enumeration<T> = object : Enumeration<T> {
    private val iterator: Iterator<T> = this@asEnumeration.iterator()

    override fun hasMoreElements(): Boolean = iterator.hasNext()

    override fun nextElement(): T = iterator.next()
}

/**
 * A piece of binary data.
 */
interface Blob {
    /**
     * An input stream for reading the data in this blob.
     */
    fun newInputStream(): InputStream

    /**
     * The checksum of the data in this blob.
     */
    val checksum: Checksum

    companion object {
        /**
         * Creates a [Blob] containing data from the file at the given [path].
         *
         * The [checksum] is computed using the given [algorithm], which can be any accepted by [MessageDigest].
         *
         * @throws [IOException] An I/O error occurred.
         */
        fun fromFile(path: Path, algorithm: String): Blob = object : Blob {
            override fun newInputStream() = Files.newInputStream(path)

            override val checksum: Checksum by lazy { Checksum.fromFile(path, algorithm) }
        }

        /**
         * Creates a list of [Blob] objects containing data from the file at the given [path].
         *
         * Concatenating the contents of each blob together produces the original file. This accepts any [algorithm]
         * accepted by [MessageDigest].
         *
         * @param [path] The path of the file.
         * @param [algorithm] The name of the algorithm to compute the checksum with.
         * @param [blockSize] The maximum number of bytes in each blob.
         *
         * @throws [IOException] An I/O error occurred.
         */
        fun chunkFile(path: Path, algorithm: String, blockSize: Long = Long.MAX_VALUE): List<Blob> {
            val fileSize = Files.size(path)
            var position = 0L
            val blobs = mutableListOf<Blob>()

            // Iterate over each [blockSize] byte chunk of the file.
            while (position < fileSize) {
                val blob = object : Blob {
                    override fun newInputStream(): InputStream = Files.newByteChannel(path).use {
                        it.position(position)
                        BoundedInputStream(Channels.newInputStream(it), blockSize)
                    }

                    override val checksum: Checksum by lazy {
                        newInputStream().use { Checksum.fromInputStream(it, algorithm) }
                    }
                }

                blobs.add(blob)
                position += blockSize
            }

            return blobs
        }

        /**
         * Creates a [Blob] containing the concatenated data from all the given [blobs].
         *
         * The [checksum] is computed using the given [algorithm], which can be any accepted by [MessageDigest].
         *
         * @throws [IOException] An I/O error occurred.
         */
        fun fromBlobs(blobs: Iterable<Blob>, algorithm: String): Blob = object : Blob {
            // Lazily evaluate the input streams to avoid having too many open at once.
            override fun newInputStream(): InputStream = blobs
                .asSequence()
                .map { it.newInputStream() }
                .let { SequenceInputStream(it.asEnumeration()) }

            override val checksum: Checksum by lazy {
                newInputStream().use { Checksum.fromInputStream(it, algorithm) }
            }
        }
    }
}
