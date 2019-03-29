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

object BlockTable : IntIdTable() {
    val version: Column<EntityID<Int>> = reference("version", VersionTable).primaryKey(0)

    val blob: Column<EntityID<Int>> = reference("blob", BlobTable).primaryKey(1)

    val index: Column<Int> = integer("index").primaryKey(2)
}

/**
 * A block of data which comprises a version of a file.
 */
class BlockEntity(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The version that this block belongs to.
     */
    var version: VersionEntity by VersionEntity referencedOn BlockTable.version

    /**
     * The binary object that makes up the data in this block.
     */
    var blob: BlobEntity by BlobEntity referencedOn BlockTable.blob

    /**
     * The index of this block among all the blocks that make up the [version].
     */
    var index: Int by BlockTable.index

    companion object : IntEntityClass<BlockEntity>(BlockTable)
}
