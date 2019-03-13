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

/**
 * A table for storing regular files in the timeline.
 */
object RegularFiles : IntIdTable() {
    /**
     * The metadata associated with this file.
     */
    val file: Column<EntityID<Int>> = reference("file", Files).uniqueIndex()
}

/**
 * A regular file in the timeline.
 */
class RegularFile(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The metadata associated with this file.
     */
    var file: File by File referencedOn RegularFiles.file

    /**
     * The binary objects that make up this file.
     */
    var blobs: SizedIterable<Blob> by Blob via RegularFileBlobs

    companion object : IntEntityClass<RegularFile>(RegularFiles)
}