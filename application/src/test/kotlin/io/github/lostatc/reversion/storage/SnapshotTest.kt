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

import io.github.lostatc.reversion.api.RecordAlreadyExistsException
import io.github.lostatc.reversion.api.Timeline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Paths

interface SnapshotTest {
    var workPath: Path

    val timeline: Timeline

    @BeforeEach
    fun createFiles(@TempDir tempPath: Path) {
        workPath = tempPath

        FileCreateContext(workPath) {
            file("a")
            file("b")
            directory("c") {
                file("a")
            }
        }
    }

    @Test
    fun `get version`() {
        val snapshot = timeline.createSnapshot(emptyList(), workPath)
        val version = snapshot.createVersion(Paths.get("a"), workPath)

        assertEquals(version, snapshot.versions[version.path])
        assertNull(snapshot.versions[Paths.get("nonexistent")])
    }

    @Test
    fun `list versions`() {
        val snapshot = timeline.createSnapshot(emptyList(), workPath)
        val versions = setOf(
            snapshot.createVersion(Paths.get("a"), workPath),
            snapshot.createVersion(Paths.get("b"), workPath),
            snapshot.createVersion(Paths.get("c", "a"), workPath)
        )

        assertEquals(versions, snapshot.versions.values.toSet())
    }

    @Test
    fun `list versions with multiple snapshots`() {
        val snapshot1 = timeline.createSnapshot(emptyList(), workPath)
        val version1 = snapshot1.createVersion(Paths.get("a"), workPath)

        val snapshot2 = timeline.createSnapshot(emptyList(), workPath)
        val version2 = snapshot2.createVersion(Paths.get("a"), workPath)

        assertEquals(setOf(version1), snapshot1.versions.values.toSet())
        assertEquals(setOf(version2), snapshot2.versions.values.toSet())
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
    fun `get tag`() {
        val snapshot = timeline.createSnapshot(emptyList(), workPath)
        val tag = snapshot.addTag(name = "test")

        assertEquals(tag, snapshot.tags[tag.name])
        assertNull(snapshot.tags["nonexistent"])
    }

    @Test
    fun `list tags`() {
        val snapshot = timeline.createSnapshot(emptyList(), workPath)
        val tags = setOf(
            snapshot.addTag("test1"),
            snapshot.addTag("test2"),
            snapshot.addTag("test3")
        )

        assertEquals(tags, snapshot.tags.values.toSet())
    }

    @Test
    fun `creating a nonexistent version throws`() {
        val snapshot = timeline.createSnapshot(emptyList(), workPath)

        assertThrows<java.nio.file.NoSuchFileException> {
            snapshot.createVersion(Paths.get("nonexistent"), workPath)
        }
    }

    @Test
    fun `creating a version that already exists throws`() {
        val snapshot = timeline.createSnapshot(emptyList(), workPath)
        snapshot.createVersion(Paths.get("a"), workPath)

        assertThrows<RecordAlreadyExistsException> {
            snapshot.createVersion(Paths.get("a"), workPath)
        }
    }

    @Test
    fun `remove version`() {
        val snapshot = timeline.createSnapshot(emptyList(), workPath)
        val version = snapshot.createVersion(Paths.get("a"), workPath)

        assertFalse(snapshot.removeVersion(Paths.get("nonexistent")))
        assertTrue(snapshot.removeVersion(version.path))
        assertNull(snapshot.versions[version.path])
    }

    @Test
    fun `creating a tag that already exists throws`() {
        val snapshot = timeline.createSnapshot(emptyList(), workPath)
        snapshot.addTag("test")

        assertThrows<RecordAlreadyExistsException> {
            snapshot.addTag("test")
        }
    }

    @Test
    fun `remove tag`() {
        val snapshot = timeline.createSnapshot(emptyList(), workPath)
        val tag = snapshot.addTag("test")

        assertFalse(snapshot.removeTag("nonexistent"))
        assertTrue(snapshot.removeTag(tag.name))
        assertNull(snapshot.tags[tag.name])
    }
}