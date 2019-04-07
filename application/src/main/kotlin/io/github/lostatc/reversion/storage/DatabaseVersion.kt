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
import io.github.lostatc.reversion.api.Version
import io.github.lostatc.reversion.schema.BlockTable
import io.github.lostatc.reversion.schema.VersionEntity
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission

/**
 * An implementation of [Version] which is backed by a relational database.
 */
data class DatabaseVersion(val entity: VersionEntity, override val repository: DatabaseRepository) : Version {
    override val path: Path
        get() = transaction { entity.path }

    override val lastModifiedTime: FileTime
        get() = transaction { entity.lastModifiedTime }

    override val permissions: Set<PosixFilePermission>?
        get() = transaction { entity.permissions }

    override val size: Long
        get() = transaction { entity.size }

    override val checksum: Checksum
        get() = transaction { entity.checksum }

    override val snapshot: DatabaseSnapshot
        get() = transaction { DatabaseSnapshot(entity.snapshot, repository) }

    override val timeline: DatabaseTimeline
        get() = transaction { DatabaseTimeline(entity.snapshot.timeline, repository) }

    override fun getData(): Blob = transaction {
        // If a blob is missing, skip over it. It is the responsibility of the caller to check for corruption.
        entity.blocks
            .orderBy(BlockTable.index to SortOrder.ASC)
            .mapNotNull { repository.getBlob(it.blob.checksum) }
            .let { Blob.fromBlobs(it, repository.hashAlgorithm) }
    }

    override fun isChanged(file: Path): Boolean =
        Files.size(file) == size && Checksum.fromFile(file, repository.hashAlgorithm) == checksum
}
