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
 * A table for storing tags in the timeline.
 */
object Tags : IntIdTable() {
    /**
     * The name of the tag.
     */
    val name: Column<String> = varchar("name", 255)

    /**
     * The description of the tag.
     */
    val description: Column<String> = text("description")

    /**
     * Whether the snapshot associated with this tag should be kept forever.
     */
    val pinned: Column<Boolean> = bool("pinned")

    /**
     * The snapshot associated with this tag.
     */
    val snapshot: Column<EntityID<Int>> = reference("snapshot", Snapshots)
}

/**
 * A tag in the timeline.
 */
class Tag(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The name of the tag.
     */
    var name: String by Tags.name

    /**
     * The description of the tag.
     */
    var description: String by Tags.description

    /**
     * Whether the snapshot associated with this tag should be kept forever.
     */
    var pinned: Boolean by Tags.pinned

    /**
     * The snapshot associated with this tag.
     */
    var snapshot: Snapshot by Snapshot referencedOn Tags.snapshot

    companion object : IntEntityClass<Tag>(Tags)
}