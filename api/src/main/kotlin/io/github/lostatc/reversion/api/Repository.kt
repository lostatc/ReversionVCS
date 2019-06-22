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

package io.github.lostatc.reversion.api

import java.io.IOException
import java.nio.file.Path
import java.util.UUID

/**
 * Information about the integrity of a repository.
 *
 * @param [corruptVersions] The set of versions in the repository which are corrupt.
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
     * A factory for creating [CleanupPolicy] instances.
     */
    val policyFactory: CleanupPolicyFactory

    /**
     * The timelines in this repository indexed by their [ID][Timeline.id].
     */
    val timelines: Map<UUID, Timeline>

    /**
     * The amount of storage space being used by the repository in bytes.
     *
     * This is an estimate of the amount of storage space being used in the file system by the repository, which may be
     * different from the [totalSize]. This does not include space taken up by metadata.
     */
    val storedSize: Long

    /**
     * The total size of all the versions stored in this repository in bytes.
     *
     * This is the sum of the [Version.size] of all the versions in this repository. This may be different from the
     * [storedSize].
     */
    val totalSize: Long
        get() = timelines.values
            .flatMap { it.snapshots.values }
            .flatMap { it.versions.values }
            .map { it.size }
            .sum()


    /**
     * Creates a new timeline in this repository and returns it.
     *
     * @param [policies] The rules which govern how old snapshots in this timeline are cleaned up.
     */
    fun createTimeline(policies: Set<CleanupPolicy> = setOf()): Timeline

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
     *
     * @return A report of which versions are corrupt.
     */
    fun verify(): IntegrityReport

    /**
     * Repairs the repository.
     *
     * This attempts to repair corrupt data in the repository using files from the working directory. Any versions which
     * cannot be repaired are removed. If this returns without throwing an exception, it is guaranteed that
     * `verify().isValid` will evaluate to `true`. However, data may be lost.
     *
     * @param [workDirectory] The path of the working directory.
     */
    fun repair(workDirectory: Path)

    /**
     * Deletes this repository.
     *
     * @throws [IOException] An I/O error occurred.
     */
    fun delete()
}
