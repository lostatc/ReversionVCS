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

import kotlinx.coroutines.delay
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

/**
 * Information about the integrity of a repository.
 *
 * This represents the integrity of the repository at the time it was checked for corruption. After [repair] is called,
 * this report is outdated.
 */
interface IntegrityReport {
    /**
     * The set of corrupt versions that will be repaired by [repair].
     *
     * These are versions which can be repaired without losing data. This could involve replacing corrupt data with
     * duplicate data from the working directory or elsewhere in the repository.
     */
    val repaired: Set<Version>

    /**
     * The set of corrupt versions that will be deleted by [repair].
     *
     * These are versions which cannot be repaired, and must instead be deleted.
     */
    val deleted: Set<Version>

    /**
     * The set of versions in the repository which are corrupt.
     */
    val corrupt: Set<Version>
        get() = repaired + deleted

    /**
     * Whether the repository is valid (not corrupt).
     */
    val isValid: Boolean
        get() = corrupt.isEmpty()

    /**
     * Repairs the repository.
     *
     * This repairs all versions in [repaired] and deletes all versions in [deleted]. After this is called, all corrupt
     * versions in the repository must have been repaired or deleted. Calling this method does not change the values of
     * [repaired], [deleted], [corrupt], or [isValid].
     *
     * @throws [IOException] An I/O error occurred.
     */
    fun repair()
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
     * A set of jobs to be executed in the background.
     *
     * When the daemon is running, each job in this set will be [run][Job.run] by the daemon in a background thread.
     * This can be used to schedule jobs which should be executed in regular intervals, even when the application isn't
     * running. Jobs which are added to this set while the daemon is running may not be run until the daemon is
     * restarted.
     *
     * Examples of how this could be used include cleaning up unused data or making backups.
     */
    val jobs: Set<Job>
        get() = emptySet()

    /**
     * A factory for creating [CleanupPolicy] instances.
     */
    val policyFactory: CleanupPolicyFactory

    /**
     * The timelines in this repository indexed by their [ID][Timeline.id].
     */
    val timelines: Map<UUID, Timeline>

    /**
     * The estimated amount of storage space being used by the repository in bytes.
     *
     * This is an estimate of the amount of storage space being used in the file system by the repository, which may be
     * different from the [totalSize].
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
     * Verifies the integrity of the repository and allows for repairing it.
     *
     * @param [workDirectory] The path of the working directory containing the data.
     *
     * @return An object which can be used to identify corrupt versions and optionally repair them.
     */
    fun verify(workDirectory: Path): IntegrityReport

    /**
     * Deletes this repository.
     *
     * @throws [IOException] An I/O error occurred.
     */
    fun delete()

    /**
     * An [action] which executes in the background on a fixed [interval].
     */
    data class Job(val interval: Duration, val action: () -> Unit) {
        /**
         * Run this job, executing [action] every [interval].
         */
        suspend fun run() {
            while (true) {
                delay(interval.toMillis())
                action()
            }
        }
    }
}
