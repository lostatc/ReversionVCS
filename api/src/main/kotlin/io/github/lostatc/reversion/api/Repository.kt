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

package io.github.lostatc.reversion.api

import java.nio.file.Path
import java.util.UUID

/**
 * Information about the integrity of a repository.
 *
 * @param [corruptVersions] The set of files in the repository which are corrupt.
 */
data class IntegrityReport(val corruptVersions: Set<Version>) {
    /**
     * Whether the repository is valid (not corrupt).
     */
    val isValid: Boolean
        get() = corruptVersions.isEmpty()
}

/**
 * A repository where version history is stored.
 */
interface Repository : Configurable {
    /**
     * The absolute path of the repository.
     */
    val path: Path

    /**
     * The configuration for the repository.
     */
    override val config: Config

    /**
     * A factory for creating [RetentionPolicy] instances.
     */
    val policyFactory: RetentionPolicyFactory

    /**
     * The timelines in this repository indexed by their [ID][Timeline.id].
     */
    val timelines: Map<UUID, Timeline>

    /**
     * Creates a new timeline in this repository and returns it.
     *
     * @param [policies] The rules which govern how old snapshots in this timeline are cleaned up.
     */
    fun createTimeline(policies: Set<RetentionPolicy> = setOf()): Timeline

    /**
     * Removes the timeline with the given [id] from the repository.
     *
     * This deletes the timeline and all its snapshots, files and tags.
     *
     * @return `true` if the timeline was deleted, `false` if it didn't exist.
     */
    fun removeTimeline(id: UUID): Boolean

    /**
     * Verifies the integrity of the repository.
     */
    fun verify(): IntegrityReport
}
