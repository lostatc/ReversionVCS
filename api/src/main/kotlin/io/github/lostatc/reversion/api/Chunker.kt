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

package io.github.lostatc.reversion.api

import java.nio.channels.SeekableByteChannel

/**
 * An object which splits data into chunks.
 */
interface Chunker {

    /**
     * The boundary of a chunk of data.
     *
     * @param [position] The starting position of the chunk in the data.
     * @param [size] The size of the chunk in bytes.
     */
    data class Chunk(val position: Long, val size: Long)

    /**
     * Reads data from [source] and finds chunk boundaries.
     *
     * This function closes [source] once the end of the returned sequence is reached.
     */
    fun chunk(source: SeekableByteChannel): Sequence<Chunk>
}


/**
 * A [Chunker] which chunks data into fixed-size chunks of the given [chunkSize].
 */
class FixedSizeChunker(val chunkSize: Long) : Chunker {
    override fun chunk(source: SeekableByteChannel): Sequence<Chunker.Chunk> = source.use { channel ->
        val size = channel.size()
        (0..size step chunkSize)
            .map { Chunker.Chunk(position = it, size = minOf(chunkSize, size - it)) }
            .asSequence()
    }
}
