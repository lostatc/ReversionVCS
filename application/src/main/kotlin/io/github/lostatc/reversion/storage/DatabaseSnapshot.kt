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

import io.github.lostatc.reversion.api.*
import io.github.lostatc.reversion.schema.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * An implementation of [Snapshot] which is backed by a relational database.
 */
data class DatabaseSnapshot(val entity: SnapshotEntity, override val repository: DatabaseRepository) : Snapshot {
    override val revision: Int
        get() = transaction { entity.revision }

    override val timeCreated: Instant
        get() = transaction { entity.timeCreated }

    override val timeline: DatabaseTimeline
        get() = transaction { DatabaseTimeline(entity.timeline, repository) }

    override val pinned: Boolean
        get() = transaction {
            TagEntity
                .find { (TagTable.snapshot eq entity.id) and (TagTable.pinned eq true) }
                .any()
        }

    override fun createVersion(path: Path, workDirectory: Path): DatabaseVersion = transaction {
        if (getVersion(path) != null) {
            throw RecordAlreadyExistsException("A version with the path '$path' already exists in this snapshot.")
        }

        // Record file metadata in the database.
        val versionEntity = VersionEntity.new {
            this.path = path
            snapshot = entity
            lastModifiedTime = Files.getLastModifiedTime(path)
            permissions = PermissionSet.fromPath(path)
            size = Files.size(path)
            checksum = Checksum.fromFile(path, repository.hashAlgorithm)
        }

        val absolutePath = workDirectory.resolve(path)

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
        // Delete the version from the database before deleting the blobs from the file system to avoid corruption in
        // case the operation is interrupted.
        val checksums = transaction {
            val versionEntity = VersionEntity
                .find { (VersionTable.snapshot eq entity.id) and (VersionTable.path eq path) }
                .singleOrNull()

            val checksums = versionEntity?.blocks?.map { it.blob.checksum }
            versionEntity?.delete()

            checksums
        } ?: return false

        for (checksum in checksums) {
            repository.removeBlob(checksum)
        }

        return true
    }

    override fun getVersion(path: Path): DatabaseVersion? = transaction {
        VersionEntity
            .find { (VersionTable.snapshot eq entity.id) and (VersionTable.path eq path) }
            .singleOrNull()
            ?.let { DatabaseVersion(it, repository) }
    }

    override fun listVersions(): List<Version> = transaction {
        entity.versions.map { DatabaseVersion(it, repository) }
    }

    override fun addTag(name: String, description: String, pinned: Boolean): DatabaseTag = transaction {
        if (timeline.getTag(name) != null) {
            throw RecordAlreadyExistsException("A tag with the name '$name' already exists in this timeline.")
        }

        val tagEntity = TagEntity.new {
            this.name = name
            this.description = description
            this.pinned = pinned
            this.snapshot = entity
        }

        DatabaseTag(tagEntity, repository)
    }

    override fun removeTag(name: String): Boolean = transaction {
        val tagEntity = TagEntity
            .find { (TagTable.timeline eq entity.timeline.id) and (TagTable.name eq name) }
            .singleOrNull()

        tagEntity?.delete()
        tagEntity != null
    }

    override fun getTag(name: String): DatabaseTag? = transaction {
        TagEntity
            .find { (TagTable.timeline eq entity.timeline.id) and (TagTable.name eq name) }
            .singleOrNull()
            ?.let { DatabaseTag(it, repository) }
    }

    override fun listTags(): List<Tag> = transaction {
        entity.tags.map { DatabaseTag(it, repository) }
    }

    override fun listCumulativeVersions(): List<Version> = transaction {
        val innerQuery = VersionTable.innerJoin(SnapshotTable)
            .slice(VersionTable.id, SnapshotTable.revision.max())
            .select { SnapshotTable.revision lessEq revision }
            .groupBy(VersionTable.path)
            .alias("versionIDs")

        val query = VersionTable.join(
            innerQuery, JoinType.INNER,
            onColumn = VersionTable.id,
            otherColumn = innerQuery[VersionTable.id]
        )
            .slice(VersionTable.columns)
            .selectAll()

        VersionEntity
            .wrapRows(query)
            .map { DatabaseVersion(it, repository) }
    }
}
