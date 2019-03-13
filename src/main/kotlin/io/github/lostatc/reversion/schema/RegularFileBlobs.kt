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
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

/**
 * A table for storing the relationships between regular files and binary objects.
 */
object RegularFileBlobs : Table() {
    /**
     * A regular file in the timeline.
     */
    val regularFile: Column<EntityID<Int>> = reference("regularFile", RegularFiles).primaryKey(0)

    /**
     * A binary object that makes up the file.
     */
    val blob: Column<EntityID<Int>> = reference("blob", Blobs).primaryKey(1)

    /**
     * The index of the binary object among all the binary objects that make up the file.
     */
    val index: Column<Int> = integer("index").primaryKey(2)
}
