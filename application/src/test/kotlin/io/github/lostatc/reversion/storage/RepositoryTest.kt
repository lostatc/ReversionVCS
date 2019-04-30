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
import io.github.lostatc.reversion.api.Repository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

interface RepositoryTest {
    /**
     * The repository to test.
     */
    val repository: Repository

    @Test
    fun `create timeline`() {
        val policies = setOf(
            repository.policyFactory.ofUnit(1, ChronoUnit.WEEKS, 7),
            repository.policyFactory.ofVersions(100),
            repository.policyFactory.ofDuration(30, ChronoUnit.DAYS)
        )
        val timeline = repository.createTimeline("test", policies)

        assertEquals("test", timeline.name)
        assertEquals(policies, timeline.retentionPolicies)
        assertTrue(Instant.now() >= timeline.timeCreated)
        assertTrue(timeline.snapshots.isEmpty())
    }

    @Test
    fun `already existing timeline is not created`() {
        repository.createTimeline("test")

        assertThrows<RecordAlreadyExistsException> {
            repository.createTimeline("test")
        }
    }

    @Test
    fun `get timeline by name`() {
        val timeline = repository.createTimeline("test")

        assertEquals(timeline, repository.timelinesByName[timeline.name])
        assertNull(repository.timelinesByName["nonexistent"])
    }

    @Test
    fun `get timeline by ID`() {
        val timeline = repository.createTimeline("test")

        assertEquals(timeline, repository.timelinesById[timeline.uuid])
        assertNull(repository.timelinesById[UUID.randomUUID()])
    }

    @Test
    fun `remove timeline by name`() {
        val timeline = repository.createTimeline("test")

        assertFalse(repository.removeTimeline("nonexistent"))
        assertTrue(repository.removeTimeline(timeline.name))
        assertNull(repository.timelinesByName[timeline.name])
    }

    @Test
    fun `remove timeline by ID`() {
        val timeline = repository.createTimeline("test")

        assertFalse(repository.removeTimeline(UUID.randomUUID()))
        assertTrue(repository.removeTimeline(timeline.uuid))
        assertNull(repository.timelinesById[timeline.uuid])
    }

    @Test
    fun `list timelines`() {
        val first = repository.createTimeline("first")
        val second = repository.createTimeline("second")
        val third = repository.createTimeline("third")

        assertEquals(setOf(first.name, second.name, third.name), repository.timelinesByName.keys.toSet())
        assertEquals(setOf(first.uuid, second.uuid, third.uuid), repository.timelinesById.keys.toSet())
        assertEquals(setOf(first, second, third), repository.timelinesByName.values.toSet())
    }
}