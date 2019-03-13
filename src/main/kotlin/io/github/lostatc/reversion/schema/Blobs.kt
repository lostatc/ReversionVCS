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

/**
 * A table for storing metadata associated with binary objects in the timeline.
 */
object Blobs : IntIdTable() {
    /**
     * The hexadecimal SHA-256 checksum of the binary object.
     */
    val checksum: Column<String> = varchar("checksum", 64).uniqueIndex()

    /**
     * The size of the binary object in bytes.
     */
    val size: Column<Long> = long("size")
}

/**
 * The metadata associated with a binary object in the timeline.
 */
class Blob(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The hexadecimal SHA-256 checksum of the binary object.
     */
    var checksum: String by Blobs.checksum

    /**
     * The size of the binary object in bytes.
     */
    var size: Long by Blobs.size

    /**
     * The regular files that this blob is a part of.
     */
    var files: SizedIterable<File> by File via FileBlobs

    companion object : IntEntityClass<Blob>(Blobs)
}