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

import io.github.lostatc.reversion.api.PermissionSet
import io.github.lostatc.reversion.api.Timeline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

interface VersionTest {
    val timeline: Timeline

    var workPath: Path

    @BeforeEach
    fun createFiles(@TempDir tempPath: Path) {
        workPath = tempPath.resolve("work")

        FileCreateContext(workPath) {
            file("a", content = "apple")
            file("b", content = "banana")
            directory("c") {
                file("a", content = "orange")
            }
        }
    }

    @Test
    fun `file metadata is correct`() {
        val snapshot = timeline.createSnapshot(listOf(Paths.get("a")), workPath)
        val version = snapshot.versions.getValue(Paths.get("a"))
        val absolutePath = workPath.resolve("a")

        assertEquals(Paths.get("a"), version.path)
        assertEquals(Files.getLastModifiedTime(absolutePath).toMillis(), version.lastModifiedTime.toMillis())
        assertEquals(PermissionSet.fromPath(absolutePath), version.permissions)
        assertEquals(Files.size(absolutePath), version.size)
    }

    @Test
    fun `file data is correct`() {
        val snapshot = timeline.createSnapshot(listOf(Paths.get("a")), workPath)
        val version = snapshot.versions.getValue(Paths.get("a"))

        assertEquals(version.checksum, version.data.checksum)
        assertEquals("apple", version.data.newInputStream().use { it.reader().readText() })
    }

    @Test
    fun `unchanged files are identified as unchanged`() {
        val snapshot = timeline.createSnapshot(listOf(Paths.get("a")), workPath)
        val version = snapshot.versions.getValue(Paths.get("a"))
        val absolutePath = workPath.resolve("a")

        assertFalse(version.isChanged(absolutePath))
    }

    @Test
    fun `changed files are identified as changed`() {
        val snapshot = timeline.createSnapshot(listOf(Paths.get("a")), workPath)
        val version = snapshot.versions.getValue(Paths.get("a"))
        val absolutePath = workPath.resolve("b")

        assertTrue(version.isChanged(absolutePath))
    }

    @Test
    fun `checkout version`() {
        val snapshot = timeline.createSnapshot(listOf(Paths.get("a")), workPath)
        val version = snapshot.versions.getValue(Paths.get("a"))
        val targetPath = workPath.resolve("target")
        version.checkout(targetPath)

        assertFalse(version.isChanged(targetPath))
    }

    @Test
    fun `checking out doesn't overwrite`() {
        val snapshot = timeline.createSnapshot(listOf(Paths.get("a")), workPath)
        val version = snapshot.versions.getValue(Paths.get("a"))
        val targetPath = workPath.resolve("b")

        assertEquals(false, version.checkout(targetPath, overwrite = false))
        assertEquals("banana", Files.readString(targetPath))
    }

    @Test
    fun `checking out overwrites`() {
        val snapshot = timeline.createSnapshot(listOf(Paths.get("a")), workPath)
        val version = snapshot.versions.getValue(Paths.get("a"))
        val targetPath = workPath.resolve("b")

        assertEquals(true, version.checkout(targetPath, overwrite = true))
        assertEquals("apple", Files.readString(targetPath))
    }
}