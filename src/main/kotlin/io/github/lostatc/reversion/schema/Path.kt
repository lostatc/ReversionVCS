/*
 * Copyright © 2019 Garrett Powell
 *
 * This file is part of reversion.
 *
 * reversion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * reversion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with reversion.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.lostatc.reversion.schema

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SizedIterable

object Paths : IntIdTable() {
    val path: Column<String> = varchar("path", 4096).uniqueIndex()
}

/**
 * A file system path.
 */
class Path(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The relative path with '/' used as the path separator and no trailing '/'.
     *
     * Each [path] is unique.
     */
    var path: String by Paths.path

    /**
     * The files that have this path.
     */
    val files: SizedIterable<File> by File referrersOn Files.path

    companion object : IntEntityClass<Path>(Paths)
}
