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

import io.github.lostatc.reversion.schema.*
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * A rule specifying how old versions of files are cleaned up.
 *
 * For the first [timeFrame] after a snapshot is taken, this policy will only keep [maxVersions] versions of each file
 * for every [minInterval] interval.
 */
data class RetentionPolicy(val minInterval: Duration, val timeFrame: Duration, val maxVersions: Int) {
    /**
     * Returns only the items which can be deleted.
     *
     * @param [files] The sequence of items to be filtered.
     * @param [keyFunc] A function which accepts an item and returns the time the item was created/added/committed.
     */
    fun <T> filter(files: Sequence<T>, keyFunc: (T) -> Instant): Sequence<T> = sequence {
        val now = Instant.now()
        val timeFrameStart = now.minus(timeFrame)
        var intervalStart = now.minus(minInterval)
        var intervalEnd = now

        // Sort files from newest to oldest.
        val sortedItems = files.sortedByDescending { keyFunc(it) }

        // Iterate over each interval from now to the start of the time frame.
        while (intervalStart.isAfter(timeFrameStart)) {
            val filesInThisInterval = sortedItems.filter {
                val instant = keyFunc(it)
                instant.isAfter(intervalStart) && instant.isBefore(intervalEnd)
            }

            // Drop the newest files to keep them and delete the rest.
            yieldAll(filesInThisInterval.drop(maxVersions))

            intervalStart = intervalStart.minus(minInterval)
            intervalEnd = intervalEnd.minus(minInterval)
        }
    }
}

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
     * Creates a new snapshot in this timeline and returns it.
     *
     * @param [paths] The paths of files to include in the snapshot.
     */
    fun createSnapshot(paths: Collection<Path>): Snapshot

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
     * Returns a sequence of the snapshots in this timeline.
     *
     * @return A sequence of snapshots sorted from newest to oldest.
     */
    fun listSnapshots(): Sequence<Snapshot>

    /**
     * Returns a sequence of the files in this timeline with the given [path].
     */
    fun listVersions(path: Path): Sequence<File>

    /**
     * Removes old versions of files.
     *
     * The timeline's [retentionPolicies] govern which snapshots are removed.
     *
     * @return The number of file versions that were removed.
     */
    fun clean(): Int
}

data class DatabaseTimeline(val entity: TimelineEntity) : Timeline {
    override var name: String
        get() = transaction { entity.name }
        set(value) {
            transaction { entity.name = value }
        }

    override val uuid: UUID
        get() = transaction { entity.uuid }

    override val timeCreated: Instant
        get() = transaction { entity.timeCreated }

    override var retentionPolicies: Set<RetentionPolicy>
        get() = transaction {
            entity.retentionPolicies.map {
                RetentionPolicy(
                    minInterval = it.minInterval,
                    timeFrame = it.timeFrame,
                    maxVersions = it.maxVersions
                )
            }.toSet()
        }
        set(value) {
            transaction {
                val entities = value.map {
                    RetentionPolicyEntity.new {
                        minInterval = it.minInterval
                        timeFrame = it.timeFrame
                        maxVersions = it.maxVersions
                    }
                }

                entity.retentionPolicies = SizedCollection(entities)
            }
        }

    override fun createSnapshot(paths: Collection<Path>): Snapshot = transaction {
        val snapshot = DatabaseSnapshot(
            SnapshotEntity.new {
                revision = SnapshotEntity
                    .find { SnapshotTable.timeline eq entity.id }
                    .orderBy(SnapshotTable.revision to SortOrder.DESC)
                    .first()
                    .revision
                timeCreated = Instant.now()
                timeline = entity
            }
        )
        for (path in paths) {
            snapshot.addFile(path)
        }

        snapshot
    }

    override fun removeSnapshot(revision: Int): Boolean = transaction {
        val snapshotEntity = SnapshotEntity
            .find { (SnapshotTable.timeline eq entity.id) and (SnapshotTable.revision eq revision) }
            .singleOrNull()

        if (snapshotEntity == null) {
            false
        } else {
            snapshotEntity.delete()
            true
        }
    }

    override fun getSnapshot(revision: Int): Snapshot? = transaction {
        SnapshotEntity
            .find { (SnapshotTable.timeline eq entity.id) and (SnapshotTable.revision eq revision) }
            .map { DatabaseSnapshot(it) }
            .singleOrNull()
    }

    override fun listSnapshots(): Sequence<Snapshot> = transaction {
        entity.snapshots
            .orderBy(SnapshotTable.timeCreated to SortOrder.DESC)
            .asSequence()
            .map { DatabaseSnapshot(it) }
    }

    override fun listVersions(path: Path): Sequence<File> = transaction {
        FileEntity
            .find { (FileTable.timeline eq entity.id) and (PathTable.path eq path) }
            .asSequence()
            .map { DatabaseFile(it) }
    }

    override fun clean(): Int = transaction {
        val filesToDelete = mutableSetOf<FileEntity>()
        for (policy in retentionPolicies) {
            for (pathEntity in entity.paths) {
                val versions = pathEntity.files.asSequence()
                filesToDelete.addAll(policy.filter(versions) { it.oldestSnapshot.timeCreated })
            }
        }

        for (fileEntity in filesToDelete) {
            fileEntity.delete()
        }

        filesToDelete.size
    }
}
