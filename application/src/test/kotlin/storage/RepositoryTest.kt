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

package storage

import io.github.lostatc.reversion.api.RecordAlreadyExistsException
import io.github.lostatc.reversion.api.Repository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

interface RepositoryTest {
    /**
     * The repository to test.
     */
    val repository: Repository

    @Test
    fun `create timeline`() {
        val timeline = repository.createTimeline("test")

        assertEquals("test", timeline.name)
    }

    @Test
    fun `do not create existing timeline`() {
        repository.createTimeline("test")

        assertThrows<RecordAlreadyExistsException> {
            repository.createTimeline("test")
        }
    }

    @Test
    fun `get timeline by name`() {
        val timeline = repository.createTimeline("test")

        assertEquals(timeline.uuid, repository.getTimeline(timeline.name)?.uuid)
        assertNull(repository.getTimeline("nonexistent"))
    }

    @Test
    fun `get timeline by ID`() {
        val timeline = repository.createTimeline("test")

        assertEquals(timeline.name, repository.getTimeline(timeline.uuid)?.name)
        assertNull(repository.getTimeline(UUID.randomUUID()))
    }

    @Test
    fun `remove timeline by name`() {
        val timeline = repository.createTimeline("test")

        assertFalse(repository.removeTimeline("nonexistent"))
        assertTrue(repository.removeTimeline(timeline.name))
        assertNull(repository.getTimeline(timeline.name))
    }

    @Test
    fun `remove timeline by ID`() {
        val timeline = repository.createTimeline("test")

        assertFalse(repository.removeTimeline(UUID.randomUUID()))
        assertTrue(repository.removeTimeline(timeline.uuid))
        assertNull(repository.getTimeline(timeline.uuid))
    }

    @Test
    fun `list timelines`() {
        repository.createTimeline("test1")
        repository.createTimeline("test2")
        repository.createTimeline("test3")

        assertEquals(
            setOf("test1", "test2", "test3"),
            repository.listTimelines().map { it.name }.toSet()
        )
    }
}