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
import io.github.lostatc.reversion.randomBytes
import io.github.lostatc.reversion.toByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

class BoundedByteChannelTest {
    val contents: ByteArray = randomBytes(4096)

    fun createChannel(): ReadableByteChannel = BufferByteChannel(ByteBuffer.wrap(contents).asReadOnlyBuffer())

    @Test
    fun `channel does not read past limit`() {
        val boundedChannel = BoundedByteChannel(createChannel(), 200)
        assertArrayEquals(contents.take(200).toByteArray(), boundedChannel.toByteArray())
    }

    @Test
    fun `limit is past end of bytes`() {
        val boundedChannel = BoundedByteChannel(createChannel(), 5000)
        assertArrayEquals(contents, boundedChannel.toByteArray())
    }

    @Test
    fun `limit is zero`() {
        val boundedChannel = BoundedByteChannel(createChannel(), 0)
        assertEquals(0, boundedChannel.readBytes().position())
    }
}

class SequenceByteChannelTest {
    val contents: ByteArray = randomBytes(1024)

    fun createChannel(): ReadableByteChannel = BufferByteChannel(ByteBuffer.wrap(contents).asReadOnlyBuffer())

    @Test
    fun `bytes from multiple channels are read`() {
        val channels = sequenceOf(
            createChannel(),
            createChannel(),
            createChannel()
        )
        val sequenceChannel = SequenceByteChannel(channels)

        assertArrayEquals(contents + contents + contents, sequenceChannel.toByteArray())
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
