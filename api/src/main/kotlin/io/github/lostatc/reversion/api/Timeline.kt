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

import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import java.util.*

/**
 * A rule specifying how old versions of files are cleaned up.
 *
 * For the first [timeFrame] after a new version of a file is created, this policy will only keep [maxVersions] versions
 * of that file for every [minInterval] interval.
 *
 * Each [RetentionPolicy] has a [description] that is shown in the UI.
 *
 * @param [minInterval] The interval of time.
 * @param [timeFrame] The maximum amount of time to keep files for.
 * @param [maxVersions] The maximum number of versions to keep for each interval.
 * @param [description] A human-readable description of the policy.
 */
data class RetentionPolicy(
    val minInterval: Duration,
    val timeFrame: Duration,
    val maxVersions: Int,
    val description: String
) {
    companion object {
        /**
         * The lower-case name of the unit.
         */
        private val TemporalUnit.name: String
            get() = toString().toLowerCase()

        /**
         * Creates a retention policy based on a unit of time.
         *
         * @param [amount] The maximum amount of time to keep files for in terms of [unit].
         * @param [unit] The interval of time.
         * @param [versions] The maximum number of versions to keep for each interval.
         */
        fun of(amount: Long, unit: TemporalUnit, versions: Int): RetentionPolicy = RetentionPolicy(
            minInterval = unit.duration,
            timeFrame = Duration.of(amount, unit),
            maxVersions = versions,
            description = "For the first $amount ${unit.name}, keep $versions versions every 1 ${unit.name}."
        )

        /**
         * Creates a retention policy that keeps a given number of [versions] of each file.
         */
        fun ofVersions(versions: Int): RetentionPolicy = RetentionPolicy(
            minInterval = ChronoUnit.FOREVER.duration,
            timeFrame = ChronoUnit.FOREVER.duration,
            maxVersions = versions,
            description = "Keep $versions versions of each file."
        )

        /**
         * Creates a retention policy that keeps each version for a given amount of time.
         *
         * @param [amount] The amount of time in terms of [unit].
         * @param [unit] The unit to measure the duration in.
         */
        fun ofDuration(amount: Long, unit: TemporalUnit): RetentionPolicy = RetentionPolicy(
            minInterval = Duration.of(amount, unit),
            timeFrame = Duration.of(amount, unit),
            maxVersions = Int.MAX_VALUE,
            description = "Keep each version for $amount ${unit.name}."
        )
    }
}

/**
 * A timeline in a repository.
 */
interface Timeline {
    /**
     * The unique name of the timeline.
     */
    var name: String

    /**
     * The UUID of the timeline which does not change.
     */
    val uuid: UUID

    /**
     * The time the timeline was created.
     */
    val timeCreated: Instant

    /**
     * The rules which govern how old snapshots in this timeline are cleaned up.
     */
    var retentionPolicies: Set<RetentionPolicy>

    /**
     * The repository that this timeline is a part of.
     */
    val repository: Repository

    /**
     * Creates a new snapshot in this timeline containing the given [paths] and returns it.
     *
     * @param [paths] The paths of the files relative to their working directory.
     * @param [workDirectory] The path of the working directory containing the files.
     *
     * @throws [NoSuchFileException] One of the files in [paths] doesn't exist.
     * @throws [IOException] An I/O error occurred.
     */
    fun createSnapshot(paths: Iterable<Path>, workDirectory: Path): Snapshot

    /**
     * Removes the snapshot with the given [revision] number from this timeline.
     *
     * @return `true` if the snapshot was removed, `false` if it didn't exist.
     */
    fun removeSnapshot(revision: Int): Boolean

    /**
     * Returns the snapshot in this timeline with the given [revision] number.
     *
     * @return The snapshot or `null` if it doesn't exist.
     */
    fun getSnapshot(revision: Int): Snapshot?

    /**
     * Returns a list of the snapshots in this timeline.
     *
     * @return A list of snapshots sorted from newest to oldest.
     */
    fun listSnapshots(): List<Snapshot>

    /**
     * Returns the newest snapshot in this timeline.
     *
     * @return The newest snapshot or `null` if there are no snapshots.
     */
    fun getLatestSnapshot(): Snapshot? = listSnapshots().firstOrNull()

    /**
     * Removes the tag with the given [name] from this timeline.
     *
     * @return `true` if the tag was removed, `false` if it didn't exist.
     */
    fun removeTag(name: String): Boolean = listSnapshots().any { it.removeTag(name) }

    /**
     * Returns the tag in this timeline with the given [name].
     *
     * @return The tag or `null` if it doesn't exist.
     */
    fun getTag(name: String): Tag? = listTags().find { it.name == name }

    /**
     * Returns a list of the tags that are associated with this timeline.
     */
    fun listTags(): List<Tag> = listSnapshots().flatMap { it.listTags() }

    /**
     * Returns a list of the versions in this timeline of the file with the given [path].
     *
     * @param [path] The path of the file relative to its working directory.
     *
     * @return A list of versions sorted from newest to oldest.
     */
    fun listVersions(path: Path): List<Version> = listSnapshots().mapNotNull { it.getVersion(path) }

    /**
     * Returns a list of all the distinct paths of files in this timeline.
     *
     * If [parent] is not `null`, all returned paths will be a descendant of [parent].
     *
     * @param [parent] The path of the parent file relative to its working directory.
     */
    fun listPaths(parent: Path? = null): List<Path> = listSnapshots()
        .flatMap { it.listVersions() }
        .map { it.path }
        .distinct()
        .filter { if (parent == null) true else it.startsWith(parent) }

    /**
     * Removes old versions of files with the given [paths].
     *
     * The timeline's [retentionPolicies] govern which versions are removed. By default, old versions of all files are
     * removed.
     *
     * @param [paths] The paths of the files relative to their working directory.
     *
     * @return The number of versions that were removed.
     */
    fun clean(paths: Iterable<Path> = listPaths().asIterable()): Int {
        var totalDeleted = 0

        for (policy in retentionPolicies) {
            for (path in paths) {
                // Get versions with this path sorted from newest to oldest. Skip versions that are pinned.
                val sortedVersions = listVersions(path).filter { !it.snapshot.pinned }.toList()

                val timeFrameEnd = sortedVersions.first().snapshot.timeCreated
                val timeFrameStart = timeFrameEnd.minus(policy.timeFrame)
                var intervalEnd = timeFrameEnd
                var intervalStart = timeFrameEnd.minus(policy.minInterval)

                // Iterate over each interval starting from the time the most recent version was created and going
                // backwards.
                while (intervalStart.isAfter(timeFrameStart)) {
                    val versionsInThisInterval = sortedVersions.filter {
                        val timeCreated = it.snapshot.timeCreated
                        timeCreated.isAfter(intervalStart) && timeCreated.isBefore(intervalEnd)
                    }

                    // Drop the newest files to keep them and delete the rest.
                    for (versionToDelete in versionsInThisInterval.drop(policy.maxVersions)) {
                        versionToDelete.snapshot.removeVersion(versionToDelete.path)
                        totalDeleted++
                    }

                    intervalStart = intervalStart.minus(policy.minInterval)
                    intervalEnd = intervalEnd.minus(policy.minInterval)
                }
            }
        }

        return totalDeleted
    }
}
