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

import io.github.lostatc.reversion.api.Blob
import io.github.lostatc.reversion.api.Checksum
import io.github.lostatc.reversion.api.PermissionSet
import io.github.lostatc.reversion.api.RecordAlreadyExistsException
import io.github.lostatc.reversion.api.Snapshot
import io.github.lostatc.reversion.schema.BlobEntity
import io.github.lostatc.reversion.schema.BlobTable
import io.github.lostatc.reversion.schema.BlockEntity
import io.github.lostatc.reversion.schema.SnapshotEntity
import io.github.lostatc.reversion.schema.SnapshotTable
import io.github.lostatc.reversion.schema.TagEntity
import io.github.lostatc.reversion.schema.TagTable
import io.github.lostatc.reversion.schema.VersionEntity
import io.github.lostatc.reversion.schema.VersionTable
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Objects

/**
 * An implementation of [Snapshot] which is backed by a relational database.
 */
data class DatabaseSnapshot(val entity: SnapshotEntity, override val repository: DatabaseRepository) : Snapshot {
    override val revision: Int
        get() = transaction { entity.revision }

    override val timeCreated: Instant
        get() = transaction { entity.timeCreated }

    override val versions: Map<Path, DatabaseVersion>
        get() = transaction {
            VersionEntity
                .find { VersionTable.snapshot eq entity.id }
                .associate { it.path to DatabaseVersion(it, repository) }
        }

    override val cumulativeVersions: Map<Path, DatabaseVersion>
        get() = transaction {
            val innerQuery = VersionTable.innerJoin(SnapshotTable)
                .slice(VersionTable.id, SnapshotTable.revision.max())
                .select { SnapshotTable.revision lessEq revision }
                .groupBy(VersionTable.path)
                .alias("versionIDs")

            val query = VersionTable.innerJoin(innerQuery)
                .slice(VersionTable.columns)
                .selectAll()

            VersionEntity
                .wrapRows(query)
                .associate { it.path to DatabaseVersion(it, repository) }
        }

    override val tags: Map<String, DatabaseTag>
        get() = transaction {
            TagEntity
                .find { (TagTable.snapshot eq entity.id) }
                .associate { it.name to DatabaseTag(it, repository) }
        }

    override val timeline: DatabaseTimeline
        get() = transaction { DatabaseTimeline(entity.timeline, repository) }

    override val pinned: Boolean
        get() = transaction {
            TagEntity
                .find { (TagTable.snapshot eq entity.id) and (TagTable.pinned eq true) }
                .any()
        }

    override fun createVersion(path: Path, workDirectory: Path): DatabaseVersion = transaction {
        if (path in versions) {
            throw RecordAlreadyExistsException("A version with the path '$path' already exists in this snapshot.")
        }

        val absolutePath = workDirectory.resolve(path)

        // Record file metadata in the database.
        val versionEntity = VersionEntity.new {
            this.path = path
            snapshot = entity
            lastModifiedTime = Files.getLastModifiedTime(absolutePath)
            permissions = PermissionSet.fromPath(absolutePath)
            size = Files.size(absolutePath)
            checksum = Checksum.fromFile(absolutePath, repository.hashAlgorithm)
        }

        // Create a list of blobs from the file.
        val blobs = Blob.chunkFile(absolutePath, repository.hashAlgorithm, repository.blockSize)

        // Add the blobs to the file system. Because this is wrapped in a transaction, records in the database won't be
        // updated until all the blobs have been added. This is to prevent corruption in case the operation is
        // interrupted.
        for ((index, blob) in blobs.withIndex()) {
            repository.addBlob(blob)

            val blobEntity = BlobEntity.find { BlobTable.checksum eq blob.checksum }.single()

            BlockEntity.new {
                version = versionEntity
                this.blob = blobEntity
                this.index = index
            }
        }

        DatabaseVersion(versionEntity, repository)
    }

    override fun removeVersion(path: Path): Boolean {
        // Remove the version from the database before modifying the file system to avoid corruption in case this
        // operation is interrupted.
        val version = versions[path] ?: return false

        // Remove the version from the database.
        transaction {
            version.entity.delete()
        }

        // Remove any blobs associated with the version which aren't referenced by any other version.
        repository.clean()

        return true
    }

    override fun addTag(name: String, description: String, pinned: Boolean): DatabaseTag = transaction {
        // if (getTag(name) != null) {
        if (name in tags) {
            throw RecordAlreadyExistsException("A tag with the name '$name' already exists in this snapshot.")
        }

        val tagEntity = TagEntity.new {
            this.name = name
            this.description = description
            this.pinned = pinned
            this.snapshot = entity
        }

        DatabaseTag(tagEntity, repository)
    }

    override fun removeTag(name: String): Boolean {
        val tag = tags[name] ?: return false

        transaction {
            tag.entity.delete()
        }

        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DatabaseSnapshot) return false
        return entity.id == other.entity.id && repository == other.repository
    }

    override fun hashCode(): Int = Objects.hash(entity.id, repository)

    override fun toString(): String = "Snapshot(revision=$revision)"
}
