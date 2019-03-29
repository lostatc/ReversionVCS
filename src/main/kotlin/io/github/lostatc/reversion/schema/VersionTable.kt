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

package io.github.lostatc.reversion.schema

import io.github.lostatc.reversion.storage.Checksum
import io.github.lostatc.reversion.storage.PermissionSet
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SizedIterable
import java.nio.file.Path
import java.nio.file.attribute.FileTime

object VersionTable : IntIdTable() {
    val path: Column<Path> = path("path")

    val snapshot: Column<EntityID<Int>> = reference("snapshot", SnapshotTable)

    val lastModifiedTime: Column<FileTime> = fileTime("lastModifiedTime")

    val permissions: Column<PermissionSet?> = filePermissions("permissions").nullable()

    val size: Column<Long> = long("size")

    val checksum: Column<Checksum> = checksum("checksum")

    init {
        uniqueIndex(path, snapshot)
    }
}

/**
 * A version of a file in a timeline.
 */
class VersionEntity(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The path of the file.
     */
    var path: Path by VersionTable.path

    /**
     * The snapshot the file is a part of.
     */
    var snapshot: SnapshotEntity by SnapshotEntity referencedOn VersionTable.snapshot

    /**
     * The time the file was last modified.
     */
    var lastModifiedTime: FileTime by VersionTable.lastModifiedTime

    /**
     * The permissions of the file.
     *
     * If POSIX permissions are not applicable, this is `null`.
     */
    var permissions: PermissionSet? by VersionTable.permissions

    /**
     * The size of the file in bytes.
     */
    var size: Long by VersionTable.size

    /**
     * The hash of the file contents.
     */
    var checksum: Checksum by VersionTable.checksum

    /**
     * The blocks of data which make up this file.
     */
    val blocks: SizedIterable<BlockEntity> by BlockEntity referrersOn BlockTable.version

    companion object : IntEntityClass<VersionEntity>(VersionTable)
}