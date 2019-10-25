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

package io.github.lostatc.reversion.storage

import io.github.lostatc.reversion.FileTreeBuilder
import io.github.lostatc.reversion.TEST_FILE_SIZE
import io.github.lostatc.reversion.api.Configurator
import io.github.lostatc.reversion.api.io.resolve
import io.github.lostatc.reversion.api.io.toByteArray
import io.github.lostatc.reversion.api.storage.PermissionSet
import io.github.lostatc.reversion.api.storage.StorageProvider
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

/**
 * The position to seek to in files to test reading/writing in the middle of the file.
 */
private const val OFFSET: Int = TEST_FILE_SIZE / 2

@EnabledOnOs(OS.LINUX)
interface FuseFileSystemTest {
    val provider: StorageProvider

    val configurator: Configurator

    var workPath: Path

    var mountPath: Path

    /**
     * The contents of each test file.
     */
    var contents: Map<Path, ByteArray>

    @BeforeAll
    fun mountFileSystem(@TempDir tempPath: Path) {
        workPath = tempPath

        // This directory is created separately and will not be cleaned up. Attempting to do so before the file system
        // has completely unmounted will throw a [FileSystemException]. Trying to delete this directory after unmounting
        // the file system in [unmountFileSystem] sometimes fails for this reason.
        mountPath = Files.createTempDirectory("reversion-")

        val builder = FileTreeBuilder(workPath) {
            file("a", size = TEST_FILE_SIZE)
            file("b", size = TEST_FILE_SIZE)
            directory("c") {
                file("a", size = TEST_FILE_SIZE)
            }
            file("d", size = TEST_FILE_SIZE)
        }

        contents = builder.contents

        val workDirectory = WorkDirectory.init(workPath, provider, configurator)

        workDirectory.commit(listOf(workPath.resolve("d")))
        workDirectory.commit(listOf(workPath.resolve("a"), workPath.resolve("b"), workPath.resolve("c", "a")))

        SnapshotMounter.mount(workDirectory.timeline.latestSnapshot!!, mountPath)
    }

    @AfterAll
    fun unmountFileSystem() {
        SnapshotMounter.unmount(mountPath)
    }

    @Test
    fun `read file metadata from latest snapshot`() {
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
    fun `read file metadata from previous snapshot`() {
        assertEquals(
            Files.size(workPath.resolve("d")),
            Files.size(mountPath.resolve("d"))
        )
        assertEquals(
            Files.getLastModifiedTime(workPath.resolve("d")).toMillis(),
            Files.getLastModifiedTime(mountPath.resolve("d")).toMillis()
        )
        assertEquals(
            PermissionSet.fromPath(workPath.resolve("d")),
            PermissionSet.fromPath(mountPath.resolve("d"))
        )
    }

    @Test
    fun `list files in root directory`() {
        assertEquals(
            setOf(mountPath.resolve("a"), mountPath.resolve("b"), mountPath.resolve("c"), mountPath.resolve("d")),
            Files.list(mountPath).asSequence().toSet()
        )
    }

    @Test
    fun `list files in sub-directory`() {
        assertEquals(
            setOf(mountPath.resolve("c", "a")),
            Files.list(mountPath.resolve("c")).asSequence().toSet()
        )
    }

    @Test
    fun `read whole file from latest snapshot`() {
        assertArrayEquals(
            contents[workPath.resolve("a")],
            Files.readAllBytes(mountPath.resolve("a"))
        )
    }

    @Test
    fun `read whole file from previous snapshot`() {
        assertArrayEquals(
            contents[workPath.resolve("d")],
            Files.readAllBytes(mountPath.resolve("d"))
        )
    }

    @Test
    fun `read file from offset`() {
        val buffer = ByteBuffer.allocate(TEST_FILE_SIZE)
        Files.newByteChannel(mountPath.resolve("b")).use {
            it.position(OFFSET.toLong())
            it.read(buffer)
            buffer.flip()
        }

        assertArrayEquals(
            contents[workPath.resolve("b")]?.drop(OFFSET)?.toByteArray(),
            buffer.toByteArray()
        )
    }

    @Test
    fun `read file, rewind and read less`() {
        val buffer = ByteBuffer.allocate(TEST_FILE_SIZE).limit(OFFSET)
        Files.newByteChannel(mountPath.resolve("b")).use {
            it.read(buffer)
            buffer.rewind()

            assertArrayEquals(
                contents[workPath.resolve("b")]?.take(OFFSET)?.toByteArray(),
                buffer.toByteArray()
            )

            buffer.limit(OFFSET - 1)
            it.read(buffer)
            buffer.rewind()

            assertArrayEquals(
                contents[workPath.resolve("b")]?.take(OFFSET - 1)?.toByteArray(),
                buffer.toByteArray()
            )
        }
    }

    @Test
    fun `read file, rewind and read more`() {
        val buffer = ByteBuffer.allocate(TEST_FILE_SIZE).limit(OFFSET)
        Files.newByteChannel(mountPath.resolve("b")).use {
            it.read(buffer)
            buffer.rewind()

            assertArrayEquals(
                contents[workPath.resolve("b")]?.take(OFFSET)?.toByteArray(),
                buffer.toByteArray()
            )

            buffer.limit(OFFSET + 1)
            it.read(buffer)
            buffer.rewind()

            assertArrayEquals(
                contents[workPath.resolve("b")]?.take(OFFSET + 1)?.toByteArray(),
                buffer.toByteArray()
            )
        }
    }
}
