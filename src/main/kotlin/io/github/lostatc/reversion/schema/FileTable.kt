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

package io.github.lostatc.reversion.schema

import io.github.lostatc.reversion.storage.Checksum
import io.github.lostatc.reversion.storage.PermissionSet
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SizedIterable
import java.nio.file.attribute.FileTime

object FileTable : IntIdTable() {
    val path: Column<EntityID<Int>> = reference("path", PathTable)

    val timeline: Column<EntityID<Int>> = reference("timeline", TimelineTable)

    val lastModifiedTime: Column<FileTime> = fileTime("lastModifiedTime")

    val permissions: Column<PermissionSet?> = filePermissions("permissions").nullable()

    val size: Column<Long> = long("size")

    val checksum: Column<Checksum> = checksum("checksum")
}

/**
 * A file in a timeline.
 *
 * This represents the data and metadata of a regular file that exists at some point in a timeline's history.
 */
class FileEntity(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The path of the file.
     */
    var path: PathEntity by PathEntity referencedOn FileTable.path

    /**
     * The timeline the file is a part of.
     */
    var timeline: TimelineEntity by TimelineEntity referencedOn FileTable.timeline

    /**
     * The time the file was last modified.
     */
    var lastModifiedTime: FileTime by FileTable.lastModifiedTime

    /**
     * The permissions of the file.
     *
     * This stores the file permissions in 'rwxrwxrwx' format. If POSIX permissions are not applicable, this is `null`.
     */
    var permissions: PermissionSet? by FileTable.permissions

    /**
     * The size of the file in bytes.
     */
    var size: Long by FileTable.size

    /**
     * The SHA-256 hash of the file contents.
     */
    var checksum: Checksum by FileTable.checksum

    /**
     * The binary objects that make up this file.
     */
    var blobs: SizedIterable<BlobEntity> by BlobEntity via FileBlobTable

    /**
     * The snapshots that this file is a part of.
     */
    var snapshots: SizedIterable<SnapshotEntity> by SnapshotEntity via VersionTable

    companion object : IntEntityClass<FileEntity>(FileTable)
}