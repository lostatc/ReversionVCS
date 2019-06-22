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

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.util.UUID

/**
 * An interval of time.
 *
 * @param [start] The starting point in the interval
 * @param [end] The ending point in the interval.
 */
private data class Interval(val start: Instant, val end: Instant) {
    /**
     * Returns whether the given [instant] is in the interval.
     */
    operator fun contains(instant: Instant): Boolean = instant in start..end

    /**
     * Returns a new interval shifted forward in time by the given [amount].
     */
    operator fun plus(amount: TemporalAmount): Interval = Interval(start = start + amount, end = end + amount)

    companion object {
        /**
         * Returns a list of intervals spanning from [start] to [end] with each interval having a length of [step].
         */
        fun step(start: Instant, end: Instant, step: TemporalAmount): List<Interval> {
            val intervals = mutableListOf<Interval>()
            var interval = Interval(start = start, end = start + step)

            while (interval.end <= end) {
                intervals.add(interval)
                interval += step
            }

            return intervals
        }
    }
}

/**
 * A timeline in a repository.
 */
interface Timeline {
    /**
     * The UUID of the timeline.
     */
    val id: UUID

    /**
     * The time the timeline was created.
     */
    val timeCreated: Instant

    /**
     * The rules which govern how old snapshots in this timeline are cleaned up.
     */
    var cleanupPolicies: Set<CleanupPolicy>

    /**
     * The snapshots in this timeline indexed by their [revision number][Snapshot.revision].
     */
    val snapshots: Map<Int, Snapshot>

    /**
     * The newest snapshot in this timeline.
     *
     * @return The newest snapshot or `null` if there are no snapshots.
     */
    val latestSnapshot: Snapshot?
        get() = snapshots.values.maxBy { it.revision }

    /**
     * A set of all the paths in this timeline.
     */
    val paths: Set<Path>
        get() = snapshots
            .values
            .flatMap { it.versions.keys }
            .toSet()


    /**
     * The repository that this timeline is a part of.
     */
    val repository: Repository

    /**
     * Creates a new snapshot in this timeline containing the given [paths] and returns it.
     *
     * @param [paths] The paths of the files relative to their working directory.
     * @param [workDirectory] The path of the working directory containing the files.
     * @param [name] The initial name of the snapshot.
     * @param [description] The initial description of the snapshot.
     * @param [pinned] Whether the snapshot is pinned.
     *
     * @throws [NoSuchFileException] One of the files in [paths] doesn't exist.
     * @throws [IOException] An I/O error occurred.
     */
    fun createSnapshot(
        paths: Iterable<Path>,
        workDirectory: Path,
        name: String? = null,
        description: String = "",
        pinned: Boolean = false
    ): Snapshot

    /**
     * Removes the snapshot with the given [revision] number from this timeline.
     *
     * @return `true` if the snapshot was removed, `false` if it didn't exist.
     */
    fun removeSnapshot(revision: Int): Boolean

    /**
     * Returns a list of the versions in this timeline of the file with the given [path].
     *
     * @param [path] The path of the file relative to its working directory.
     *
     * @return A list of versions sorted from newest to oldest.
     */
    fun listVersions(path: Path): List<Version> = snapshots.values
        .sortedByDescending { it.revision }
        .mapNotNull { it.versions[path] }

    /**
     * Removes old versions of files with the given [pathsToClean].
     *
     * The timeline's [cleanupPolicies] govern which versions are removed. By default, old versions of all files are
     * removed.
     *
     * This also removes any snapshots which do not have any versions.
     *
     * @param [pathsToClean] The paths of the files relative to their working directory.
     *
     * @return The number of versions that were removed.
     */
    fun clean(pathsToClean: Iterable<Path> = paths): Int {
        val versionsToDeletePerPolicy = mutableSetOf<Set<Version>>()

        for (policy in cleanupPolicies) {
            for (path in pathsToClean) {
                // Get versions with this path sorted from newest to oldest. Skip versions that are pinned.
                val sortedVersions = listVersions(path).filter { !it.snapshot.pinned }

                val latestVersionCreated = sortedVersions.firstOrNull()?.snapshot?.timeCreated ?: return 0
                val intervals = Interval.step(
                    start = latestVersionCreated - policy.timeFrame,
                    end = latestVersionCreated,
                    step = policy.minInterval
                )

                // Iterate over each interval starting from the time the most recent version was created and going
                // backwards.
                for (interval in intervals.reversed()) {
                    val versionsInThisInterval = sortedVersions.filter { it.snapshot.timeCreated in interval }

                    // Drop the newest files to keep them and delete the rest.
                    versionsToDeletePerPolicy.add(versionsInThisInterval.drop(policy.maxVersions).toSet())
                }
            }
        }

        val versionsToDelete = versionsToDeletePerPolicy.reduce { accumulator, it -> accumulator intersect it }
        val totalDeleted = versionsToDelete.size

        // Delete versions.
        for (version in versionsToDelete) {
            version.delete()
        }

        // Remove empty snapshots.
        for (snapshot in snapshots.values) {
            snapshot.deleteIfEmpty()
        }

        logger.info("Cleaned up $totalDeleted versions in timeline $this.")

        return totalDeleted
    }

    companion object {
        /**
         * The logger for this class.
         */
        private val logger: Logger = LoggerFactory.getLogger(Timeline::class.java)
    }
}

/**
 * Removes this timeline from its [repository][Timeline.repository].
 */
fun Timeline.delete() {
    repository.removeTimeline(id)
}
