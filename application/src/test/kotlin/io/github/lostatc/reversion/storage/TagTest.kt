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
import org.junit.jupiter.api.Test
import java.nio.file.Paths

interface TagTest {
    val timeline: Timeline

    @Test
    fun `create tag`() {
        val snapshot = timeline.createSnapshot(emptyList(), Paths.get(""))
        val tag = snapshot.addTag(name = "name", description = "description", pinned = true)

        assertEquals("name", tag.name)
        assertEquals("description", tag.description)
        assertEquals(true, tag.pinned)
        assertEquals(snapshot, tag.snapshot)
    }

    @Test
    fun `modify tag`() {
        val snapshot = timeline.createSnapshot(emptyList(), Paths.get(""))
        val tag = snapshot.addTag(name = "original", description = "original", pinned = true)
        tag.name = "new"
        tag.description = "new"
        tag.pinned = false

        assertEquals("new", tag.name)
        assertEquals("new", tag.description)
        assertEquals(false, tag.pinned)
    }
}