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

import io.github.lostatc.reversion.api.io.BoundedByteChannel
import io.github.lostatc.reversion.api.io.BufferByteChannel
import io.github.lostatc.reversion.api.io.SequenceByteChannel
import io.github.lostatc.reversion.api.io.readBytes
import io.github.lostatc.reversion.api.io.readString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

class BoundedByteChannelTest {
    fun createChannel(): ReadableByteChannel =
        BufferByteChannel(ByteBuffer.wrap("abcdefghijk".toByteArray()))

    @Test
    fun `channel does not read past limit`() {
        val boundedChannel = BoundedByteChannel(createChannel(), 5)
        assertEquals("abcde", boundedChannel.readBytes().readString())
    }

    @Test
    fun `limit is past end of bytes`() {
        val boundedChannel = BoundedByteChannel(createChannel(), 20)
        assertEquals("abcdefghijk", boundedChannel.readBytes().readString())
    }

    @Test
    fun `limit is zero`() {
        val boundedChannel = BoundedByteChannel(createChannel(), 0)
        assertEquals(0, boundedChannel.readBytes().position())
    }
}

class SequenceByteChannelTest {
    fun createChannel(): ReadableByteChannel =
        BufferByteChannel(ByteBuffer.wrap("abcd".toByteArray()))

    @Test
    fun `bytes from multiple channels are read`() {
        val channels = sequenceOf(
            createChannel(),
            createChannel(),
            createChannel()
        )
        val sequenceChannel = SequenceByteChannel(channels)

        assertEquals("abcdabcdabcd", sequenceChannel.readBytes().readString())
    }

    @Test
    fun `all channels are closed`() {
        val channels = sequenceOf(
            createChannel(),
            createChannel(),
            createChannel()
        )
        val sequenceChannel = SequenceByteChannel(channels)
        sequenceChannel.readBytes()

        assertTrue(channels.none { it.isOpen })
    }

    @Test
    fun `empty sequence returns no bytes`() {
        val channels = emptySequence<ReadableByteChannel>()
        val sequenceChannel = SequenceByteChannel(channels)

        assertEquals(0, sequenceChannel.readBytes().position())
    }
}
