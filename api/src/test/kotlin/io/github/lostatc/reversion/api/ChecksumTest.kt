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

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.ReadOnlyBufferException
import java.nio.file.Files
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChecksumTest {
    @Test
    fun `checksums are immutable`() {
        val originalChecksum = Checksum("abc".toByteArray())
        val modifiedChecksum = Checksum("abc".toByteArray())

        modifiedChecksum.array[0] = 0x00

        assertEquals(originalChecksum, modifiedChecksum)
        assertThrows<ReadOnlyBufferException> {
            modifiedChecksum.buffer.put(0x00)
        }
    }

    @Test
    fun `get bytes`() {
        val checksum = Checksum("abc".toByteArray())

        assertArrayEquals("abc".toByteArray(), checksum.array)
        assertEquals(ByteBuffer.wrap("abc".toByteArray()), checksum.buffer)
        assertEquals("616263", checksum.hex)
    }

    @Test
    fun `get checksum from hex`() {
        assertEquals(Checksum("abc".toByteArray()), Checksum.fromHex("616263"))
    }

    @Test
    fun `get checksum from input stream`(@TempDir tempDir: Path) {
        val tempFile = tempDir.resolve("file")
        Files.createFile(tempFile)
        Files.writeString(tempFile, "abc")

        assertEquals(
            Checksum.fromHex("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
            Files.newInputStream(tempFile).use { Checksum.fromInputStream(it, "SHA-256") }
        )
    }

    @Test
    fun `get checksum from file`(@TempDir tempDir: Path) {
        val tempFile = tempDir.resolve("file")
        Files.createFile(tempFile)
        Files.writeString(tempFile, "abc")

        assertEquals(
            Checksum.fromHex("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
            Checksum.fromFile(tempFile, "SHA-256")
        )
    }
}