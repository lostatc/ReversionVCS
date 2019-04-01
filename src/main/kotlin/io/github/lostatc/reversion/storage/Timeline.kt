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

package io.github.lostatc.reversion.storage

import io.github.lostatc.reversion.schema.*
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * A rule specifying how old versions of files are cleaned up.
 *
 * For the first [timeFrame] after a new version of a file is created, this policy will only keep [maxVersions] versions
 * of that file for every [minInterval] interval.
 */
data class RetentionPolicy(val minInterval: Duration, val timeFrame: Duration, val maxVersions: Int)

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
     * Snapshots are ordered from most recent to least recent.
     *
     * @return A sequence of snapshots sorted from newest to oldest.
     */
    fun listSnapshots(): Sequence<Snapshot>

    /**
     * Returns the tag in this timeline with the given [name].
     *
     * @return The tag or `null` if it doesn't exist.
     */
    fun getTag(name: String): Tag? = listTags().find { it.name == name }

    /**
     * Returns a sequence of the tags that are associated with this timeline.
     */
    fun listTags(): Sequence<Tag> = listSnapshots().flatMap { it.listTags() }

    /**
     * Returns a sequence of the versions in this timeline of the file with the given [path].
     */
    fun listVersions(path: Path): Sequence<Version> = listSnapshots().mapNotNull { it.getVersion(path) }

    /**
     * Removes old versions of files.
     *
     * The timeline's [retentionPolicies] govern which versions are removed.
     *
     * @param [paths] The paths of the files to remove old versions of.
     *
     * @return The number of versions that were removed.
     */
    fun clean(paths: Collection<Path>): Int {
        var totalDeleted = 0

        for (policy in retentionPolicies) {
            for (path in paths) {
                // Sort versions with this path from newest to oldest.
                val sortedVersions = listVersions(path).sortedByDescending { it.snapshot.timeCreated }.toList()

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

/**
 * An implementation of [Timeline] which is backed by a relational database.
 */
data class DatabaseTimeline(val entity: TimelineEntity, override val repository: DatabaseRepository) : Timeline {
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

    override fun createSnapshot(paths: Collection<Path>): DatabaseSnapshot {
        val snapshot = transaction {
            val snapshotEntity = SnapshotEntity.new {
                revision = SnapshotEntity
                    .find { SnapshotTable.timeline eq entity.id }
                    .orderBy(SnapshotTable.revision to SortOrder.DESC)
                    .firstOrNull()
                    ?.revision ?: 1
                timeCreated = Instant.now()
                timeline = entity
            }

            DatabaseSnapshot(snapshotEntity, repository)
        }

        for (path in paths) {
            snapshot.createVersion(path)
        }

        return snapshot
    }

    override fun removeSnapshot(revision: Int): Boolean = transaction {
        val snapshotEntity = SnapshotEntity
            .find { (SnapshotTable.timeline eq entity.id) and (SnapshotTable.revision eq revision) }
            .singleOrNull()

        snapshotEntity?.delete()
        snapshotEntity != null
    }

    override fun getSnapshot(revision: Int): DatabaseSnapshot? = transaction {
        SnapshotEntity
            .find { (SnapshotTable.timeline eq entity.id) and (SnapshotTable.revision eq revision) }
            .singleOrNull()
            ?.let { DatabaseSnapshot(it, repository) }
    }

    override fun listSnapshots(): Sequence<DatabaseSnapshot> = transaction {
        entity.snapshots
            .orderBy(SnapshotTable.timeCreated to SortOrder.DESC)
            .asSequence()
            .map { DatabaseSnapshot(it, repository) }
    }

    override fun getTag(name: String): DatabaseTag? = transaction {
        TagEntity
            .find { TagTable.name eq name }
            .singleOrNull()
            ?.let { DatabaseTag(it, repository) }
    }

    override fun listTags(): Sequence<DatabaseTag> = transaction {
        TagEntity.all().asSequence().map { DatabaseTag(it, repository) }
    }

    override fun listVersions(path: Path): Sequence<DatabaseVersion> = transaction {
        val query = VersionTable.innerJoin(SnapshotTable)
            .slice(VersionTable.columns)
            .select { (SnapshotTable.timeline eq entity.id) and (VersionTable.path eq path) }

        VersionEntity
            .wrapRows(query)
            .asSequence()
            .map { DatabaseVersion(it, repository) }
    }
}
