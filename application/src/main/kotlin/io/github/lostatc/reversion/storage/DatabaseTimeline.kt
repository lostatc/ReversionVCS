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

import io.github.lostatc.reversion.api.RetentionPolicy
import io.github.lostatc.reversion.api.Timeline
import io.github.lostatc.reversion.schema.*
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
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

    override fun createSnapshot(paths: Iterable<Path>, workDirectory: Path): DatabaseSnapshot {
        val snapshot = transaction {
            val snapshotEntity = SnapshotEntity.new {
                revision = SnapshotEntity
                    .find { SnapshotTable.timeline eq entity.id }
                    .orderBy(SnapshotTable.revision to SortOrder.DESC)
                    .firstOrNull()
                    ?.revision ?: STARTING_REVISION
                timeCreated = Instant.now()
                timeline = entity
            }

            DatabaseSnapshot(snapshotEntity, repository)
        }

        for (path in paths) {
            snapshot.createVersion(path, workDirectory)
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
            .orderBy(SnapshotTable.revision to SortOrder.DESC)
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
            .orderBy(SnapshotTable.revision to SortOrder.DESC)

        VersionEntity
            .wrapRows(query)
            .asSequence()
            .map { DatabaseVersion(it, repository) }
    }
}
