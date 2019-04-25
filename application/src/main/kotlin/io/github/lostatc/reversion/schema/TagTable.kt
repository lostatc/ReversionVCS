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

object TagTable : IntIdTable() {
    val name: Column<String> = varchar("name", 255)

    val description: Column<String> = text("description")

    val pinned: Column<Boolean> = bool("pinned")

    val snapshot: Column<EntityID<Int>> = cascadeReference("snapshot", SnapshotTable)

    init {
        uniqueIndex(name, snapshot)
    }
}

/**
 * A tag in the timeline.
 */
class TagEntity(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The name of the tag.
     *
     * This is unique with respect to other tags on the same snapshot.
     */
    var name: String by TagTable.name

    /**
     * The description of the tag.
     */
    var description: String by TagTable.description

    /**
     * Whether the snapshot associated with this tag should be kept forever.
     */
    var pinned: Boolean by TagTable.pinned

    /**
     * The snapshot associated with this tag.
     */
    var snapshot: SnapshotEntity by SnapshotEntity referencedOn TagTable.snapshot

    companion object : IntEntityClass<TagEntity>(TagTable)
}