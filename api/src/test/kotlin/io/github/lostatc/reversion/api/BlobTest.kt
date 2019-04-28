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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Returns the data in the blob as a string.
 */
private fun Blob.readString(): String = newInputStream().reader().use { it.readText() }

/**
 * Creates a [Blob] containing the given [text].
 */
private fun Blob.Companion.fromString(text: String): Blob = object : Blob {
    override fun newInputStream(): InputStream = text.toByteArray().inputStream()

    override val checksum: Checksum = Checksum(text.toByteArray())
}

class BlobTest {

    @TempDir
    lateinit var tempPath: Path

    @Test
    fun `create blob from file`() {
        val filePath = tempPath.resolve("file")
        Files.writeString(filePath, "abc")
        val blob = Blob.fromFile(filePath, "SHA-256")

        assertEquals("abc", blob.readString())

        assertEquals(
            Checksum.fromHex("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
            blob.checksum
        )
    }

    @Test
    fun `create blobs from file`() {
        val filePath = tempPath.resolve("file")
        Files.writeString(filePath, "abcdefg")
        val blobs = Blob.chunkFile(filePath, "SHA-256", 2)

        val expectedStrings = listOf("ab", "cd", "ef", "g")

        val expectedChecksums = listOf(
            Checksum.fromHex("fb8e20fc2e4c3f248c60c39bd652f3c1347298bb977b8b4d5903b85055620603"),
            Checksum.fromHex("21e721c35a5823fdb452fa2f9f0a612c74fb952e06927489c6b27a43b817bed4"),
            Checksum.fromHex("4ca669ac3713d1f4aea07dae8dcc0d1c9867d27ea82a3ba4e6158a42206f959b"),
            Checksum.fromHex("cd0aa9856147b6c5b4ff2b7dfee5da20aa38253099ef1b4a64aced233c9afe29")
        )

        assertEquals(expectedStrings, blobs.map { it.readString() })

        assertEquals(expectedChecksums, blobs.map { it.checksum })
    }

    @Test
    fun `create blob from blobs`() {
        val blobs = listOf(
            Blob.fromString("ab"),
            Blob.fromString("cd"),
            Blob.fromString("ef"),
            Blob.fromString("g")
        )
        val combinedBlob = Blob.fromBlobs(blobs, "SHA-256")

        assertEquals(
            Checksum.fromHex("7d1a54127b222502f5b79b5fb0803061152a44f92b37e23c6527baf665d4da9a"),
            combinedBlob.checksum
        )

        assertEquals("abcdefg", combinedBlob.readString())
    }
}