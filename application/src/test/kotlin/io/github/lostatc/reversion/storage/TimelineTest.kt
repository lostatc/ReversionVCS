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

import io.github.lostatc.reversion.api.Timeline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.Paths

interface TimelineTest {
    var workPath: Path

    val timeline: Timeline

    @BeforeEach
    fun createFiles(@TempDir tempPath: Path) {
        workPath = tempPath

        FileCreateContext(workPath) {
            file("a")
            directory("b") {
                file("c")
            }
        }
    }

    @Test
    fun `created snapshot contains paths`() {
        val paths = listOf(Paths.get("a"), Paths.get("b", "c"))
        val snapshot = timeline.createSnapshot(paths, workPath)

        assertEquals(paths, snapshot.listVersions().map { it.path })
    }

    @Test
    fun `snapshot revisions increment`() {
        val first = timeline.createSnapshot(emptyList(), workPath)
        val second = timeline.createSnapshot(emptyList(), workPath)
        val third = timeline.createSnapshot(emptyList(), workPath)

        assertTrue(first.revision < second.revision && second.revision < third.revision)
    }

    @Test
    fun `get snapshot`() {
        val snapshot = timeline.createSnapshot(emptyList(), workPath)

        assertEquals(snapshot, timeline.getSnapshot(snapshot.revision))
        assertNull(timeline.getSnapshot(Int.MAX_VALUE))
    }

    @Test
    fun `remove snapshot`() {
        val snapshot = timeline.createSnapshot(emptyList(), workPath)

        assertFalse(timeline.removeSnapshot(Int.MAX_VALUE))
        assertTrue(timeline.removeSnapshot(snapshot.revision))
        assertNull(timeline.getSnapshot(snapshot.revision))
    }

    @Test
    fun `list snapshots`() {
        val first = timeline.createSnapshot(emptyList(), workPath)
        val second = timeline.createSnapshot(emptyList(), workPath)
        val third = timeline.createSnapshot(emptyList(), workPath)

        assertEquals(listOf(third, second, first), timeline.listSnapshots())
    }

    @Test
    fun `get latest snapshot`() {
        timeline.createSnapshot(emptyList(), workPath)
        timeline.createSnapshot(emptyList(), workPath)
        val third = timeline.createSnapshot(emptyList(), workPath)

        assertEquals(third, timeline.getLatestSnapshot())
    }

    @Test
    fun `list versions`() {
        val first = timeline.createSnapshot(listOf(Paths.get("a"), Paths.get("b", "c")), workPath)
        timeline.createSnapshot(listOf(Paths.get("b", "c")), workPath)
        val third = timeline.createSnapshot(listOf(Paths.get("a")), workPath)

        val expectedVersions = listOf(third.getVersion(Paths.get("a")), first.getVersion(Paths.get("a")))

        assertEquals(expectedVersions, timeline.listVersions(Paths.get("a")))
    }

    @Test
    fun `list paths`() {
        timeline.createSnapshot(listOf(Paths.get("a"), Paths.get("b", "c")), workPath)
        timeline.createSnapshot(listOf(Paths.get("b", "c")), workPath)
        timeline.createSnapshot(listOf(Paths.get("a")), workPath)

        val expectedPaths = setOf(Paths.get("a"), Paths.get("b", "c"))

        assertEquals(expectedPaths, timeline.listPaths().toSet())
    }
}