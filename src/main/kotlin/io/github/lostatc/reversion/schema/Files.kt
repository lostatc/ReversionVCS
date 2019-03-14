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

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SizedIterable
import org.joda.time.DateTime

/**
 * A table for storing metadata associated with files in the timeline.
 */
object Files : IntIdTable() {
    /**
     * The relative path of the file with '/' used as the path separator.
     */
    val path: Column<String> = varchar("path", 4096).uniqueIndex()

    /**
     * The time the file was last modified.
     */
    val lastModifiedTime: Column<DateTime> = datetime("lastModifiedTime")

    /**
     * The permissions of the file.
     *
     * This stores the file permissions in octal notation. If POSIX permissions are not applicable, this is `null`.
     */
    val permissions: Column<String?> = varchar("permissions", 3).nullable()

    /**
     * The size of the file in bytes.
     */
    val size: Column<Long> = long("size")
}

/**
 * Metadata associated with a file in the timeline.
 */
class File(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The relative path of the file with '/' used as the path separator.
     */
    var path: String by Files.path

    /**
     * The time the file was last modified.
     */
    var lastModifiedTime: DateTime by Files.lastModifiedTime

    /**
     * The permissions of the file.
     *
     * This stores the file permissions in octal notation. If POSIX permissions are not applicable, this is `null`.
     */
    var permissions: String? by Files.permissions

    /**
     * The size of the file in bytes.
     */
    var size: Long by Files.size

    /**
     * The binary objects that make up this file.
     *
     * A directory should have no binary objects.
     */
    var blobs: SizedIterable<Blob> by Blob via FileBlobs

    /**
     * The snapshots that this file is a part of.
     */
    var snapshots: SizedIterable<Snapshot> by Snapshot via FileSnapshots

    /**
     * The parents of this file.
     *
     * A file should never have more than one parent.
     */
    var parents: SizedIterable<File> by File.via(FileToFiles.child, FileToFiles.parent)

    /**
     * The children of this file.
     *
     * A file with [blobs] should not also have [children].
     */
    var children: SizedIterable<File> by File.via(FileToFiles.parent, FileToFiles.child)

    companion object : IntEntityClass<File>(Files)
}