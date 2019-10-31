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

import java.nio.ByteBuffer
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
     * It is the responsibility of the caller to close [source].
     */
    fun chunk(source: SeekableByteChannel): Sequence<Chunk>
}


/**
 * A [Chunker] which chunks data into fixed-size chunks of the given [chunkSize].
 */
class FixedSizeChunker(val chunkSize: Long) : Chunker {
    override fun chunk(source: SeekableByteChannel): Sequence<Chunker.Chunk> {
        val size = source.size()
        return (0..size step chunkSize)
            .map { Chunker.Chunk(position = it, size = minOf(chunkSize, size - it)) }
            .asSequence()
    }
}


/**
 * A [Chunker] which uses a rolling hash to find chunk boundaries.
 */
abstract class RollingHashChunker : Chunker {
    interface HashState {
        /**
         * Search for a chunk boundary within the given buffer.
         *
         * The hash will be repeatedly updated with bytes from the buffer, starting at its current position. When a
         * boundary is found or the end of the buffer is reached, this method will return.
         *
         * @param [data] The buffer to search, starting from the current position.
         *
         * @return `true` if the current position of the buffer is a boundary, `false` otherwise
         */
        fun findBoundary(data: ByteBuffer): Boolean

        /**
         * Reset this state so it can be used to find another chunk boundary.
         */
        fun reset()
    }

    /**
     * Create a new [HashState].
     */
    protected abstract fun newState(): HashState

    override fun chunk(source: SeekableByteChannel): Sequence<Chunker.Chunk> = sequence {
        val state = newState()
        val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
        var lastPosition = 0L
        var size = 0L

        while (true) {
            val bytesRead = source.read(buffer)
            if (bytesRead == -1) break
            size += bytesRead

            buffer.flip()

            if (state.findBoundary(buffer)) {
                yield(Chunker.Chunk(position = lastPosition, size = size))
                lastPosition += size
                size = 0
                state.reset()
            }

            buffer.compact()
        }

        // The data between the last chunk boundary and the end of the channel is the final chunk.
        yield(Chunker.Chunk(position = lastPosition, size = size))
    }

    companion object {
        private const val BUFFER_SIZE: Int = 4 * 1024 * 1024
    }
}

/**
 * A [Chunker] which implements the ZPAQ algorithm for content-defined chunking.
 *
 * @param [bits] The number of bits that define a chunk boundary, such that an average chunk is 2^[bits] bytes long.
 */
class ZpaqChunker(private val bits: Int) : RollingHashChunker() {
    override fun newState(): HashState = ZpaqState(bits)
}

/**
 * A [RollingHashChunker.HashState] implementing the ZPAQ algorithm for content-defined chunking.
 *
 * @author Dominic Marcuse (https://github.com/dmarcuse)
 */
@UseExperimental(ExperimentalUnsignedTypes::class)
private class ZpaqState(bits: Int) : RollingHashChunker.HashState {

    init {
        require(bits <= 32) { "The number of bits must be <= 32." }
    }

    private val bits = 32 - bits

    /**
     * The previous byte.
     */
    private var c1: UByte = 0u

    /**
     * The hash state.
     */
    private val o1 = UByteArray(256)

    /**
     * The hash value.
     */
    private var h = HM

    /**
     * Update the hash with the given byte and return whether it matches a boundary.
     */
    private fun update(byte: UByte): Boolean {
        h = if (byte == o1[c1.toInt()]) {
            h * HM + byte + 1u
        } else {
            h * HM * 2u + byte + 1u
        }

        o1[c1.toInt()] = byte
        c1 = byte

        return h < (1u shl bits)
    }

    override fun findBoundary(data: ByteBuffer): Boolean {
        while (data.hasRemaining()) {
            if (update(data.get().toUByte())) {
                return true
            }
        }

        return false
    }

    override fun reset() {
        c1 = 0u
        h = HM
        o1.fill(0u)
    }

    companion object {
        private const val HM = 123456791u
    }
}
