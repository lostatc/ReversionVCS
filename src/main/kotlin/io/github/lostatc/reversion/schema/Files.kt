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

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SizedIterable
import org.joda.time.DateTime

object Files : IntIdTable() {
    val path: Column<EntityID<Int>> = reference("path", Paths)

    val timeline: Column<EntityID<Int>> = reference("timeline", Timelines)

    val lastModifiedTime: Column<DateTime> = datetime("lastModifiedTime")

    val permissions: Column<String?> = varchar("permissions", 3).nullable()

    val size: Column<Long> = long("size")

    val checksum: Column<String> = varchar("checksum", 64)
}

/**
 * A file in a timeline.
 *
 * This represents the data and metadata of a regular file that exists at some point in a timeline's history.
 */
class File(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The path of the file.
     */
    var path: Path by Path referencedOn Files.path

    /**
     * The timeline the file is a part of.
     */
    var timeline: Timeline by Timeline referencedOn Files.timeline

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
     * The SHA-256 hash of the file contents.
     */
    var checksum: String by Files.checksum

    /**
     * The binary objects that make up this file.
     */
    var blobs: SizedIterable<Blob> by Blob via FileBlobs

    /**
     * The snapshots that this file is a part of.
     */
    var snapshots: SizedIterable<Snapshot> by Snapshot via Versions

    companion object : IntEntityClass<File>(Files)
}