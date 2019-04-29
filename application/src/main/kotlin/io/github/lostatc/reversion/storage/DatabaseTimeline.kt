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

import io.github.lostatc.reversion.api.RetentionPolicy
import io.github.lostatc.reversion.api.Timeline
import io.github.lostatc.reversion.api.Version
import io.github.lostatc.reversion.schema.RetentionPolicyEntity
import io.github.lostatc.reversion.schema.SnapshotEntity
import io.github.lostatc.reversion.schema.SnapshotTable
import io.github.lostatc.reversion.schema.TimelineEntity
import io.github.lostatc.reversion.schema.VersionEntity
import io.github.lostatc.reversion.schema.VersionTable
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.time.Instant
import java.util.Objects
import java.util.UUID

/**
 * The revision number that snapshots start incrementing from.
 */
private const val STARTING_REVISION: Int = 1

/**
 * An implementation of [Timeline] which is backed by a relational database.
 */
class DatabaseTimeline(val entity: TimelineEntity, override val repository: DatabaseRepository) : Timeline {
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
                    maxVersions = it.maxVersions,
                    description = it.description
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
                        description = it.description
                    }
                }

                entity.retentionPolicies = SizedCollection(entities)
            }
        }

    override val snapshots: Map<Int, DatabaseSnapshot> = object : AbstractMap<Int, DatabaseSnapshot>() {
        override val entries: Set<Map.Entry<Int, DatabaseSnapshot>>
            get() = transaction {
            SnapshotEntity
                .find { SnapshotTable.timeline eq entity.id }
                .map { SimpleEntry(it.revision, DatabaseSnapshot(it, repository)) }
                .toSet()
            }

        override fun containsKey(key: Int): Boolean = get(key) != null

        override fun get(key: Int): DatabaseSnapshot? = transaction {
            SnapshotEntity
                .find { (SnapshotTable.timeline eq entity.id) and (SnapshotTable.revision eq key) }
                .firstOrNull()
                ?.let { DatabaseSnapshot(it, repository) }
        }
    }

    override val latestSnapshot: DatabaseSnapshot?
        get() = transaction {
            entity.snapshots
                .orderBy(SnapshotTable.revision to SortOrder.DESC)
                .limit(1)
                .map { DatabaseSnapshot(it, repository) }
                .singleOrNull()
        }


    override val paths: Set<Path>
        get() = transaction {
            entity
                .snapshots
                .flatMap { it.versions }
                .map { it.path }
                .toSet()
        }

    override fun createSnapshot(paths: Iterable<Path>, workDirectory: Path): DatabaseSnapshot = transaction {
        val snapshotEntity = SnapshotEntity.new {
            revision = snapshots.keys.max()?.let { it + 1 } ?: STARTING_REVISION
            timeCreated = Instant.now()
            timeline = entity
        }

        val snapshot = DatabaseSnapshot(snapshotEntity, repository)

        // To avoid corruption, don't commit the snapshot until all versions have been added to it.
        for (path in paths) {
            snapshot.createVersion(path, workDirectory)
        }

        snapshot
    }

    override fun removeSnapshot(revision: Int): Boolean {
        // Remove the snapshot from the database before modifying the file system to avoid corruption in case this
        // operation is interrupted.
        val snapshot = snapshots[revision] ?: return false

        // Remove the snapshot and all its versions and tags from the database.
        transaction {
            snapshot.entity.delete()
        }

        // Remove any blobs associated with the snapshot which aren't referenced by any other snapshot.
        repository.clean()

        return true
    }

    override fun listVersions(path: Path): List<Version> = transaction {
        val query = VersionTable.innerJoin(SnapshotTable)
            .slice(VersionTable.columns)
            .select { (SnapshotTable.timeline eq entity.id) and (VersionTable.path eq path) }
            .orderBy(SnapshotTable.revision to SortOrder.DESC)

        VersionEntity
            .wrapRows(query)
            .map { DatabaseVersion(it, repository) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DatabaseTimeline) return false
        return entity.id == other.entity.id && repository == other.repository
    }

    override fun hashCode(): Int = Objects.hash(entity.id, repository)

    override fun toString(): String = "Timeline(name=$name)"
}
