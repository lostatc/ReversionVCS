/*
 * Copyright 2019 Garrett Powell
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
     * The path URI of the file.
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
     * The path URI of the file.
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
     * The snapshots that this file is a part of.
     */
    var snapshots: SizedIterable<Snapshot> by Snapshot via FileSnapshots

    companion object : IntEntityClass<File>(Files)
}