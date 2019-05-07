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

package io.github.lostatc.reversion.storage

import io.github.lostatc.reversion.api.Tag
import io.github.lostatc.reversion.schema.TagEntity
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Objects

/**
 * An implementation of [Tag] which is backed by a relational database.
 */
class DatabaseTag(val entity: TagEntity, override val repository: DatabaseRepository) : Tag {
    override var name: String
        get() = transaction { entity.name }
        set(value) {
            transaction { entity.name = value }
        }

    override var description: String
        get() = transaction { entity.description }
        set(value) {
            transaction { entity.description = value }
        }

    override var pinned: Boolean
        get() = transaction { entity.pinned }
        set(value) {
            transaction { entity.pinned = value }
        }

    override val snapshot: DatabaseSnapshot
        get() = transaction { DatabaseSnapshot(entity.snapshot, repository) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DatabaseTag) return false
        return entity.id == other.entity.id && repository == other.repository
    }

    override fun hashCode(): Int = Objects.hash(entity.id, repository)

    override fun toString(): String = "Tag(name=$name, snapshot=$snapshot)"
}
