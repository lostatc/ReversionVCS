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

package io.github.lostatc.reversion.api.io

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption.WRITE
import java.util.Objects


/**
 * An abstract base class for [Blob] implementations.
 */
private abstract class AbstractBlob : Blob {
    override val checksum: Checksum by lazy {
        openChannel().use { Checksum.fromChannel(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Blob) return false
        return checksum == other.checksum
    }

    override fun hashCode(): Int = Objects.hash(checksum)

    override fun toString(): String = "Blob(checksum=$checksum)"
}

/**
 * A piece of binary data.
 */
interface Blob {
    /**
     * Returns a channel for reading the bytes in this blob.
     */
    fun openChannel(): ReadableByteChannel

    /**
     * The checksum of the data in this blob.
     */
    val checksum: Checksum

    companion object {
        /**
         * Creates a [Blob] containing data from the file at the given [path].
         *
         * @throws [IOException] An I/O error occurred.
         */
        fun fromFile(path: Path): Blob = object : AbstractBlob() {
            override fun openChannel() = FileChannel.open(path).apply { sharedLock() }
        }

        /**
         * Creates a [Blob] containing the given [bytes].
         */
        fun fromBytes(bytes: ByteBuffer): Blob = object : AbstractBlob() {
            override fun openChannel(): ReadableByteChannel = BufferByteChannel(bytes.duplicate())
        }

        /**
         * Returns a sequence of [Blob] objects containing data from the given [file].
         *
         * Concatenating the contents of each blob together produces the original file.
         *
         * @param [file] The path of the file to read data from.
         * @param [chunker] The [Chunker] to use to chunk the file.
         *
         * @throws [IOException] An I/O error occurred.
         */
        fun chunkFile(file: Path, chunker: Chunker): Sequence<Blob> {
            val chunks = FileChannel.open(file).use {
                it.sharedLock()
                // Collect the sequence into a list so that we have all the chunks before closing the channel.
                chunker.chunk(it).toList().asSequence()
            }

            return chunks.map { chunk ->
                object : AbstractBlob() {
                    override fun openChannel(): ReadableByteChannel = FileChannel.open(file).let {
                        it.lock(chunk.position, chunk.size, true)

                        it.position(chunk.position)
                        BoundedByteChannel(it, chunk.size)
                    }
                }
            }
        }

        /**
         * Creates a [Blob] containing the concatenated data from all the given [blobs].
         *
         * @throws [IOException] An I/O error occurred.
         */
        fun fromBlobs(blobs: Iterable<Blob>): Blob = object : AbstractBlob() {
            // Lazily evaluate the input streams to avoid having too many open at once.
            override fun openChannel(): ReadableByteChannel = blobs
                .asSequence()
                .map { it.openChannel() }
                .let { SequenceByteChannel(it) }
        }
    }
}

/**
 * Write the contents of this blob to the given [file].
 *
 * @param [file] The path of the file to write the data to.
 * @param [options] The options to use to open the [file].
 */
fun Blob.write(file: Path, vararg options: OpenOption) {
    FileChannel.open(file, *options, WRITE).use { dest ->
        dest.lock()
        openChannel().use { source -> copyChannel(source, dest) }
    }
}
