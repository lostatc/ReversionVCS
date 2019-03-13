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

/**
 * A table for storing symbolic links in the timeline.
 */
object SymbolicLinks : IntIdTable() {
    /**
     * The metadata associated with this symbolic link.
     */
    val file: Column<EntityID<Int>> = reference("file", Files).uniqueIndex()

    /**
     * The path URI of the file this symbolic link points to.
     */
    val target: Column<String> = varchar("target", 4096)
}

/**
 * A symbolic link in the timeline.
 */
class SymbolicLink(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The metadata associated with this symbolic link.
     */
    var file: File by File referencedOn SymbolicLinks.file

    /**
     * The path URI of the file this symbolic link points to.
     */
    var target: String by SymbolicLinks.target

    companion object : IntEntityClass<SymbolicLink>(SymbolicLinks)
}
