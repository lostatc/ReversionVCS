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
import io.github.lostatc.reversion.api.storage.CleanupPolicy
import io.github.lostatc.reversion.api.storage.Repository
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

interface TimelineTest {
    val repository: Repository

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
    fun `create snapshot`() {
        val timeline = repository.createTimeline()
        val snapshot = timeline.createSnapshot(
            emptyList(),
            workPath,
            name = "Name",
            description = "Description",
            pinned = true
        )

        assertEquals("Name", snapshot.name)
        assertEquals("Description", snapshot.description)
        assertEquals(true, snapshot.pinned)
    }

    @Test
    fun `get snapshot`() {
        val timeline = repository.createTimeline()
        val snapshot = timeline.createSnapshot(emptyList(), workPath)

        assertEquals(snapshot, timeline.snapshots[snapshot.revision])
        assertNull(timeline.snapshots[Int.MAX_VALUE])
    }

    @Test
    fun `list snapshots`() {
        val timeline = repository.createTimeline()
        val snapshots = setOf(
            timeline.createSnapshot(emptyList(), workPath),
            timeline.createSnapshot(emptyList(), workPath),
            timeline.createSnapshot(emptyList(), workPath)
        )

        assertEquals(snapshots, timeline.snapshots.values.toSet())
    }

    @Test
    fun `list snapshots with multiple timelines`() {
        val timeline1 = repository.createTimeline()
        val snapshot1 = timeline1.createSnapshot(emptyList(), workPath)

        val timeline2 = repository.createTimeline()
        val snapshot2 = timeline2.createSnapshot(emptyList(), workPath)

        assertEquals(setOf(snapshot1), timeline1.snapshots.values.toSet())
        assertEquals(setOf(snapshot2), timeline2.snapshots.values.toSet())
    }


    @Test
    fun `get latest snapshot`() {
        val timeline = repository.createTimeline()
        timeline.createSnapshot(emptyList(), workPath)
        timeline.createSnapshot(emptyList(), workPath)
        val latest = timeline.createSnapshot(emptyList(), workPath)

        assertEquals(latest, timeline.latestSnapshot)
    }

    @Test
    fun `list paths`() {
        val timeline = repository.createTimeline()
        timeline.createSnapshot(listOf(Paths.get("a")), workPath)
        timeline.createSnapshot(listOf(Paths.get("b"), Paths.get("c", "a")), workPath)
        timeline.createSnapshot(emptyList(), workPath)

        val expectedPaths = setOf(Paths.get("a"), Paths.get("b"), Paths.get("c", "a"))

        assertEquals(expectedPaths, timeline.paths)
    }

    @Test
    fun `created snapshot contains paths`() {
        val paths = setOf(Paths.get("a"), Paths.get("c", "a"))
        val timeline = repository.createTimeline()
        val snapshot = timeline.createSnapshot(paths, workPath)

        assertEquals(paths, snapshot.versions.keys)
    }

    @Test
    fun `creating snapshot of nonexistent paths throws`() {
        val paths = setOf(Paths.get("a"), Paths.get("nonexistent"))
        val timeline = repository.createTimeline()

        assertThrows<java.nio.file.NoSuchFileException> {
            timeline.createSnapshot(paths, workPath)
        }
    }

    @Test
    fun `snapshot revisions increment`() {
        val timeline = repository.createTimeline()
        val first = timeline.createSnapshot(emptyList(), workPath)
        val second = timeline.createSnapshot(emptyList(), workPath)
        val third = timeline.createSnapshot(emptyList(), workPath)

        assertTrue(first.revision < second.revision && second.revision < third.revision)
    }

    @Test
    fun `remove snapshot`() {
        val timeline = repository.createTimeline()
        val snapshot = timeline.createSnapshot(emptyList(), workPath)

        assertFalse(timeline.removeSnapshot(Int.MAX_VALUE))
        assertTrue(timeline.removeSnapshot(snapshot.revision))
        assertNull(timeline.snapshots[snapshot.revision])
    }

    @Test
    fun `list versions`() {
        val timeline = repository.createTimeline()
        val first = timeline.createSnapshot(listOf(Paths.get("a"), Paths.get("c", "a")), workPath)
        timeline.createSnapshot(emptyList(), workPath)
        val third = timeline.createSnapshot(listOf(Paths.get("a")), workPath)

        val expectedVersions = listOf(third.versions[Paths.get("a")], first.versions[Paths.get("a")])

        assertEquals(expectedVersions, timeline.listVersions(Paths.get("a")))
    }

    @Test
    fun `clean up timeline with no cleanup policies`() {
        val timeline = repository.createTimeline(emptySet())

        val snapshot1 = timeline.createSnapshot(listOf(Paths.get("a")), workPath)
        val snapshot2 = timeline.createSnapshot(listOf(Paths.get("a")), workPath)

        assertEquals(setOf(snapshot1, snapshot2), timeline.snapshots.values.toSet())

        timeline.clean()

        assertEquals(setOf(snapshot1, snapshot2), timeline.snapshots.values.toSet())
    }

    @Test
    fun `clean up versions by total number`() {
        val policies = setOf(
            CleanupPolicy.ofVersions(1),
            CleanupPolicy.ofVersions(2)
        )
        val timeline = repository.createTimeline(policies)

        val snapshot1 = timeline.createSnapshot(listOf(Paths.get("a")), workPath)
        val snapshot2 = timeline.createSnapshot(listOf(Paths.get("a")), workPath, pinned = true)
        val snapshot3 = timeline.createSnapshot(listOf(Paths.get("a")), workPath)
        val snapshot4 = timeline.createSnapshot(listOf(Paths.get("a")), workPath)
        val snapshot5 = timeline.createSnapshot(listOf(Paths.get("a")), workPath)

        assertEquals(setOf(snapshot1, snapshot2, snapshot3, snapshot4, snapshot5), timeline.snapshots.values.toSet())

        timeline.clean()

        assertEquals(setOf(snapshot2, snapshot4, snapshot5), timeline.snapshots.values.toSet())
    }
}
