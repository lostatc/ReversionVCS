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

import io.github.lostatc.reversion.api.*
import io.github.lostatc.reversion.schema.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.time.Instant
import java.util.*

/**
 * The revision number that snapshots start incrementing from.
 */
private const val STARTING_REVISION: Int = 1

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

    override fun createSnapshot(paths: Iterable<Path>, workDirectory: Path): DatabaseSnapshot = transaction {
        // Because this is wrapped in a transaction, the snapshot won't be committed to the database until all the
        // versions have been added to it.
        val snapshotEntity = SnapshotEntity.new {
            // Set the revision to the current highest revision plus one.
            revision = SnapshotEntity
                .find { SnapshotTable.timeline eq entity.id }
                .orderBy(SnapshotTable.revision to SortOrder.DESC)
                .firstOrNull()
                ?.revision
                ?.let { it + 1 } ?: STARTING_REVISION
            timeCreated = Instant.now()
            timeline = entity
        }

        val snapshot = DatabaseSnapshot(snapshotEntity, repository)

        for (path in paths) {
            snapshot.createVersion(path, workDirectory)
        }

        snapshot
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

    override fun listSnapshots(): List<Snapshot> = transaction {
        entity.snapshots
            .orderBy(SnapshotTable.revision to SortOrder.DESC)
            .map { DatabaseSnapshot(it, repository) }
    }

    override fun removeTag(name: String): Boolean = transaction {
        val tagEntity = TagEntity
            .find { (TagTable.timeline eq entity.id) and (TagTable.name eq name) }
            .singleOrNull()

        tagEntity?.delete()
        tagEntity != null
    }

    override fun getTag(name: String): DatabaseTag? = transaction {
        TagEntity
            .find { TagTable.name eq name }
            .singleOrNull()
            ?.let { DatabaseTag(it, repository) }
    }

    override fun listTags(): List<Tag> = transaction {
        TagEntity.all().map { DatabaseTag(it, repository) }
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

    // TODO: Optimize by storing the relationships between paths in the database.
    override fun listPaths(parent: Path?): List<Path> = transaction {
        VersionTable
            .slice(VersionTable.path)
            .selectAll()
            .withDistinct()
            .map { it[VersionTable.path] }
            .filter { if (parent == null) true else it.startsWith(parent) }
    }
}
