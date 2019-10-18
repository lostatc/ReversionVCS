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
 * An action which can be taken to repair a repository.
 */
interface RepairAction {
    /**
     * A message to show the user prompting them for whether they want to attempt to repair the repository.
     */
    val message: String

    /**
     * Attempt to repair the repository and return the results.
     */
    fun repair(): Result

    /**
     * Information about an attempt to repair a repository.
     *
     * If the repository is no longer corrupt and can be used/accessed normally, [success] should be `true`. Even if
     * data was lost, [success] should only be `false` if the repository is unusable after the repair attempt.
     *
     * @param [success] Whether the repair attempt succeeded.
     * @param [message] A message to show the user after the attempt is complete.
     */
    data class Result(val success: Boolean, val message: String)
}

/**
 * An action which can be taken to verify data in a repository.
 */
interface VerifyAction {
    /**
     * A message to prompt the user with, or `null` to not prompt the user.
     */
    val message: String?

    /**
     * Verify the integrity of the repository and return a [RepairAction] or `null` if no action needs to be taken.
     */
    fun verify(): RepairAction?
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
     * @return A list of objects which can be used for verifying the repository.
     */
    fun verify(workDirectory: Path): List<VerifyAction>

    /**
     * Deletes this repository.
     *
     * @throws [IOException] An I/O error occurred.
     */
    fun delete()

    /**
     * An [action] which executes in the background on a fixed [interval].
     */
    class Job(val interval: Duration, val action: suspend () -> Unit) {
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
