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

import io.github.lostatc.reversion.FileTreeBuilder
import io.github.lostatc.reversion.TEST_FILE_SIZE
import io.github.lostatc.reversion.api.Configurator
import io.github.lostatc.reversion.api.io.resolve
import io.github.lostatc.reversion.api.storage.StorageProvider
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

interface WorkDirectoryTest {
    val provider: StorageProvider

    val configurator: Configurator

    var workPath: Path

    var workDirectory: WorkDirectory

    /**
     * The contents of each test file.
     */
    var contents: Map<Path, ByteArray>

    @BeforeEach
    fun createFiles(@TempDir tempPath: Path) {
        workPath = tempPath.resolve("work")

        val builder = FileTreeBuilder(workPath) {
            file("a", size = TEST_FILE_SIZE)
            file("b", size = TEST_FILE_SIZE)
            directory("c") {
                file("a", size = TEST_FILE_SIZE)
            }
        }

        contents = builder.contents

        workDirectory = WorkDirectory.init(workPath, provider, configurator)
    }

    /**
     * Asserts that the given [file] has the expected [contents].
     */
    fun assertExpectedContents(file: Path) = assertArrayEquals(contents[file], Files.readAllBytes(file))

    @Test
    fun `initializing an initialized directory throws`() {
        assertThrows<NotAWorkDirException> {
            WorkDirectory.init(workPath, provider)
        }
    }

    @Test
    fun `open a work directory from a descendant`() {
        val descendantWorkDirectory = WorkDirectory.openFromDescendant(workPath.resolve("c", "a")).onFail {
            fail("Work directory could not be opened successfully.")
        }

        assertEquals(workDirectory.path, descendantWorkDirectory.path)
    }

    @Test
    fun `committing a directory commits its descendants`() {
        val paths = setOf(Paths.get("a"), Paths.get("b"), Paths.get("c", "a"))
        workDirectory.commit(listOf(workPath))

        assertEquals(1, workDirectory.timeline.snapshots.size)

        val snapshot = workDirectory.timeline.latestSnapshot!!

        assertEquals(paths, snapshot.versions.keys)
    }

    @Test
    fun `unchanged files are not committed`() {
        val paths = setOf(workPath.resolve("a"), workPath.resolve("b"), workPath.resolve("c", "a"))
        workDirectory.commit(paths)
        workDirectory.commit(paths, force = false)

        assertEquals(1, workDirectory.timeline.snapshots.size)
    }

    @Test
    fun `unchanged files are committed`() {
        val relativePaths = setOf(Paths.get("a"), Paths.get("b"), Paths.get("c", "a"))
        val absolutePaths = relativePaths.map { workPath.resolve(it) }
        workDirectory.commit(absolutePaths)
        workDirectory.commit(absolutePaths, force = true)

        assertEquals(2, workDirectory.timeline.snapshots.size)

        val snapshot = workDirectory.timeline.latestSnapshot!!

        assertEquals(relativePaths, snapshot.versions.keys)
    }

    @Test
    fun `ignored files are not committed`() {
        workDirectory.writeMatchers(listOf(PrefixIgnoreMatcher(workPath.resolve("b"))))
        workDirectory.commit(listOf(workPath.resolve("a"), workPath.resolve("b")))

        assertEquals(1, workDirectory.timeline.snapshots.size)

        val snapshot = workDirectory.timeline.latestSnapshot!!

        assertEquals(setOf(Paths.get("a")), snapshot.versions.keys)
    }

    @Test
    fun `ignored files are not committed when using special file names`() {
        workDirectory.writeMatchers(listOf(PrefixIgnoreMatcher(workPath.resolve("b"))))
        workDirectory.commit(listOf(workPath.resolve(".")))

        assertEquals(1, workDirectory.timeline.snapshots.size)

        val snapshot = workDirectory.timeline.latestSnapshot!!

        assertEquals(setOf(Paths.get("a"), Paths.get("c", "a")), snapshot.versions.keys)
    }

    @Test
    fun `committing nonexistent files does not throw`() {
        workDirectory.commit(listOf(workPath.resolve("nonexistent")))
    }

    @Test
    fun `modified files are considered changed`() {
        workDirectory.commit(listOf(workPath.resolve("a"), workPath.resolve("b"), workPath.resolve("c", "a")))

        Files.writeString(workPath.resolve("a"), "new contents")
        Files.writeString(workPath.resolve("c", "a"), "new contents")

        val status = workDirectory.getStatus()
        val expectedStatus = WorkDirectory.Status(setOf(Paths.get("a"), Paths.get("c", "a")))

        assertEquals(expectedStatus, status)
    }

    @Test
    fun `missing files are not considered changed`() {
        workDirectory.commit(listOf(workPath.resolve("a"), workPath.resolve("b"), workPath.resolve("c", "a")))

        Files.delete(workPath.resolve("a"))

        val status = workDirectory.getStatus()
        val expectedStatus = WorkDirectory.Status(setOf())

        assertEquals(expectedStatus, status)
    }

    @Test
    fun `new files are considered changed`() {
        workDirectory.commit(listOf(workPath.resolve("a"), workPath.resolve("b"), workPath.resolve("c", "a")))

        Files.createFile(workPath.resolve("new"))

        val status = workDirectory.getStatus()
        val expectedStatus = WorkDirectory.Status(setOf(Paths.get("new")))

        assertEquals(expectedStatus, status)
    }

    @Test
    fun `updating does not overwrite changes`() {
        workDirectory.commit(listOf(workPath.resolve("a")))
        Files.writeString(workPath.resolve("a"), "new contents")
        workDirectory.update(listOf(workPath.resolve("a")), overwrite = false)

        assertEquals("new contents", Files.readString(workPath.resolve("a")))
    }

    @Test
    fun `updating overwrites changes`() {
        workDirectory.commit(listOf(workPath.resolve("a")))
        Files.writeString(workPath.resolve("a"), "new contents")
        workDirectory.update(listOf(workPath.resolve("a")), overwrite = true)

        assertExpectedContents(workPath.resolve("a"))
    }

    @Test
    fun `updating a directory updates its descendants`() {
        workDirectory.commit(listOf(workPath.resolve("a"), workPath.resolve("b"), workPath.resolve("c", "a")))
        Files.delete(workPath.resolve("a"))
        Files.delete(workPath.resolve("b"))
        Files.delete(workPath.resolve("c", "a"))
        workDirectory.update(listOf(workPath))

        assertExpectedContents(workPath.resolve("a"))
        assertExpectedContents(workPath.resolve("b"))
        assertExpectedContents(workPath.resolve("c", "a"))
    }

    @Test
    fun `update a file to a specified reversion`() {
        val snapshot = workDirectory.commit(listOf(workPath.resolve("a")))
        Files.writeString(workPath.resolve("a"), "new contents")
        workDirectory.commit(listOf(workPath.resolve("a")))
        workDirectory.update(listOf(workPath.resolve("a")), revision = snapshot?.revision)

        assertExpectedContents(workPath.resolve("a"))
    }

    @Test
    fun `ignored files are not updated`() {
        workDirectory.commit(listOf(workPath.resolve("b")))
        Files.delete(workPath.resolve("b"))
        workDirectory.writeMatchers(listOf(PrefixIgnoreMatcher(workPath.resolve("b"))))
        workDirectory.update(listOf(workPath.resolve("b")))

        assertTrue(Files.notExists(workPath.resolve("b")))
    }

    @Test
    fun `a deleted work directory cannot be opened`() {
        workDirectory.delete()
        assertThrows<NotAWorkDirException> {
            WorkDirectory.open(workPath)
        }
    }

    @Test
    fun `deleting a work directory doesn't affect current versions`() {
        workDirectory.delete()
        assertTrue(Files.exists(workPath.resolve("a")))
        assertTrue(Files.exists(workPath.resolve("b")))
        assertTrue(Files.exists(workPath.resolve("c", "a")))
    }
}
