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

package io.github.lostatc.reversion.storage

import io.github.lostatc.reversion.api.PermissionSet
import io.github.lostatc.reversion.api.StorageProvider
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

/**
 * Decode the bytes in this buffer as a string.
 *
 * The bytes from the [position][ByteBuffer.position] to the [limit][ByteBuffer.limit] are decoded.
 */
private fun ByteBuffer.decodeAsString(charset: Charset = Charset.defaultCharset()): String {
    val array = ByteArray(limit())
    get(array)
    return array.toString(charset)
}

@EnabledOnOs(OS.LINUX)
interface FuseFileSystemTest {
    val provider: StorageProvider

    var workPath: Path

    var mountPath: Path

    var fileSystem: FuseFileSystem

    @BeforeAll
    fun mountFileSystem(@TempDir tempPath: Path) {
        workPath = tempPath.resolve("work")
        mountPath = tempPath.resolve("mnt")

        Files.createDirectory(mountPath)
        FileCreateContext(workPath) {
            file("a", content = "apple")
            file("b", content = "banana")
            directory("c") {
                file("a", content = "orange")
            }
        }

        val workDirectory = WorkDirectory.init(workPath, provider)
        val snapshot = workDirectory.commit(listOf(workPath))

        fileSystem = FuseFileSystem(snapshot).apply {
            mount(mountPath)
        }
    }

    @AfterAll
    fun unmountFileSystem() {
        fileSystem.umount()
    }

    @Test
    fun `read file metadata`() {
        assertEquals(
            Files.size(workPath.resolve("a")),
            Files.size(mountPath.resolve("a"))
        )
        assertEquals(
            Files.getLastModifiedTime(workPath.resolve("a")).toMillis(),
            Files.getLastModifiedTime(mountPath.resolve("a")).toMillis()
        )
        assertEquals(
            PermissionSet.fromPath(workPath.resolve("a")),
            PermissionSet.fromPath(mountPath.resolve("a"))
        )
    }

    @Test
    fun `list files`() {
        assertEquals(
            setOf(mountPath.resolve("a"), mountPath.resolve("b"), mountPath.resolve("c")),
            Files.list(mountPath).asSequence().toSet()
        )
    }

    @Test
    fun `read whole file`() {
        assertEquals("apple", Files.readString(mountPath.resolve("a")))
    }

    @Test
    fun `read file from offset`() {
        val buffer = ByteBuffer.allocate(256)
        Files.newByteChannel(mountPath.resolve("b")).use {
            it.position(3)
            it.read(buffer)
            buffer.flip()
        }

        assertEquals("ana", buffer.decodeAsString())
    }

    @Test
    fun `read file, rewind and read less`() {
        val buffer = ByteBuffer.allocate(256).limit(4)
        Files.newByteChannel(mountPath.resolve("b")).use {
            it.read(buffer)
            buffer.rewind()

            assertEquals("bana", buffer.decodeAsString())

            buffer.limit(3)
            it.read(buffer)
            buffer.rewind()

            assertEquals("ban", buffer.decodeAsString())
        }
    }

    @Test
    fun `read file, rewind and read more`() {
        val buffer = ByteBuffer.allocate(256).limit(4)
        Files.newByteChannel(mountPath.resolve("b")).use {
            it.read(buffer)
            buffer.rewind()

            assertEquals("bana", buffer.decodeAsString())

            buffer.limit(5)
            it.read(buffer)
            buffer.rewind()

            assertEquals("banan", buffer.decodeAsString())
        }
    }
}
