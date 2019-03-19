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
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SizedIterable
import java.nio.file.Path

object PathTable : IntIdTable() {
    val path: Column<Path> = path("path").uniqueIndex()
}

/**
 * A file system path.
 */
class PathEntity(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The relative path with '/' used as the path separator and no trailing '/'.
     *
     * Each [path] is unique.
     */
    var path: Path by PathTable.path

    /**
     * The files that have this path.
     */
    val files: SizedIterable<FileEntity> by FileEntity referrersOn FileTable.path

    /**
     * The parents of this path.
     */
    private var parents: SizedIterable<PathEntity> by PathEntity.via(PathToPathTable.child, PathToPathTable.parent)

    /**
     * The parent of this path.
     */
    var parent: PathEntity
        get() = parents.single()
        set(value) {
            parents = SizedCollection(value)
        }

    /**
     * The immediate children of this path.
     */
    var children: SizedIterable<PathEntity> by PathEntity.via(PathToPathTable.parent, PathToPathTable.child)

    /**
     * The descendants of this path.
     */
    val descendants: Sequence<PathEntity>
        get() = sequence {
            for (child in children) {
                yield(child)
                yieldAll(child.descendants)
            }
        }

    companion object : IntEntityClass<PathEntity>(PathTable)
}
