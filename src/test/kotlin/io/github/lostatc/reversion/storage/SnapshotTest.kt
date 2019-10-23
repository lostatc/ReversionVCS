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

import io.github.lostatc.reversion.api.io.FileTreeBuilder
import io.github.lostatc.reversion.api.storage.Timeline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Paths

interface SnapshotTest {
    val timeline: Timeline

    var workPath: Path

    @BeforeEach
    fun createFiles(@TempDir tempPath: Path) {
        workPath = tempPath.resolve("work")

        FileTreeBuilder(workPath) {
            file("a")
            file("b")
            directory("c") {
                file("a")
            }
        }
    }

    @Test
    fun `modify snapshot`() {
        val snapshot = timeline.createSnapshot(emptyList(), workPath)
        snapshot.name = "New name"
        snapshot.description = "New description"
        snapshot.pinned = true

        assertEquals("New name", snapshot.name)
        assertEquals("New description", snapshot.description)
        assertEquals(true, snapshot.pinned)
    }

    @Test
    fun `get version`() {
        val snapshot = timeline.createSnapshot(listOf(Paths.get("a")), workPath)
        val version = snapshot.versions.getValue(Paths.get("a"))

        assertEquals(version, snapshot.versions[version.path])
        assertNull(snapshot.versions[Paths.get("nonexistent")])
    }

    @Test
    fun `list versions`() {
        val paths = setOf(Paths.get("a"), Paths.get("b"), Paths.get("c", "a"))
        val snapshot = timeline.createSnapshot(paths, workPath)

        assertEquals(paths, snapshot.versions.keys)
    }

    @Test
    fun `list versions with multiple snapshots`() {
        val paths = setOf(Paths.get("a"))
        val snapshot1 = timeline.createSnapshot(paths, workPath)
        val snapshot2 = timeline.createSnapshot(paths, workPath)

        assertEquals(paths, snapshot1.versions.keys)
        assertEquals(paths, snapshot2.versions.keys)
        assertNotEquals(snapshot1.versions, snapshot2.versions)
    }

    @Test
    fun `list cumulative versions`() {
        val snapshot1 = timeline.createSnapshot(listOf(Paths.get("a"), Paths.get("c", "a")), workPath)
        val snapshot2 = timeline.createSnapshot(listOf(Paths.get("a"), Paths.get("b")), workPath)

        val cumulativeVersions = setOf(
            snapshot1.versions[Paths.get("c", "a")],
            snapshot2.versions[Paths.get("a")],
            snapshot2.versions[Paths.get("b")]
        )

        assertEquals(cumulativeVersions, snapshot2.cumulativeVersions.values.toSet())
    }

    @Test
    fun `remove version`() {
        val snapshot = timeline.createSnapshot(listOf(Paths.get("a")), workPath)
        val version = snapshot.versions.getValue(Paths.get("a"))

        assertFalse(snapshot.removeVersion(Paths.get("nonexistent")))
        assertTrue(snapshot.removeVersion(version.path))
        assertNull(snapshot.versions[version.path])
    }
}
