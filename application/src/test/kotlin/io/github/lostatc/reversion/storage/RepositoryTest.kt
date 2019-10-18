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

import io.github.lostatc.reversion.api.CleanupPolicy
import io.github.lostatc.reversion.api.Repository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
            CleanupPolicy.ofStaggered(1, ChronoUnit.WEEKS),
            CleanupPolicy.ofVersions(100),
            CleanupPolicy.ofDuration(30, ChronoUnit.DAYS)
        )
        val timeline = repository.createTimeline(policies)

        assertEquals(policies, timeline.cleanupPolicies)
        assertTrue(Instant.now() >= timeline.timeCreated)
        assertTrue(timeline.snapshots.isEmpty())
    }

    @Test
    fun `get timeline`() {
        val timeline = repository.createTimeline()

        assertEquals(timeline, repository.timelines[timeline.id])
        assertNull(repository.timelines[UUID.randomUUID()])
    }

    @Test
    fun `remove timeline`() {
        val timeline = repository.createTimeline()

        assertFalse(repository.removeTimeline(UUID.randomUUID()))
        assertTrue(repository.removeTimeline(timeline.id))
        assertNull(repository.timelines[timeline.id])
    }

    @Test
    fun `list timelines`() {
        val timelines = setOf(
            repository.createTimeline(),
            repository.createTimeline(),
            repository.createTimeline()
        )

        assertEquals(timelines, repository.timelines.values.toSet())
    }
}
