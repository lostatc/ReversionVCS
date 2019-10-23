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

import io.github.lostatc.reversion.api.io.Blob
import io.github.lostatc.reversion.api.io.Checksum
import io.github.lostatc.reversion.api.storage.PermissionSet
import io.github.lostatc.reversion.api.storage.Version
import io.github.lostatc.reversion.schema.BlockTable
import io.github.lostatc.reversion.schema.VersionEntity
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.Objects

/**
 * An implementation of [Version] which is backed by a relational database.
 *
 * This must be instantiated inside a [transaction] block.
 */
class DatabaseVersion(val entity: VersionEntity, override val repository: DatabaseRepository) : Version {
    /**
     * The connection to the repository's database.
     */
    val db: Database = repository.db

    override val path: Path = entity.path

    override val lastModifiedTime: FileTime = entity.lastModifiedTime

    override val permissions: PermissionSet? = entity.permissions

    override val size: Long = entity.size

    override val checksum: Checksum = entity.checksum

    override val snapshot: DatabaseSnapshot = DatabaseSnapshot(entity.snapshot, repository)

    override val data: Blob by lazy {
        transaction(db) {
            // If a blob is missing, skip over it. It is the responsibility of the caller to check for corruption.
            entity.blocks
                .orderBy(BlockTable.index to SortOrder.ASC)
                .mapNotNull { repository.getBlob(it.blob.checksum) }
                .let { Blob.fromBlobs(it) }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DatabaseVersion) return false
        return entity.id == other.entity.id && repository == other.repository
    }

    override fun hashCode(): Int = Objects.hash(entity.id, repository)

    override fun toString(): String = "Version(path=$path, snapshot=$snapshot)"
}
