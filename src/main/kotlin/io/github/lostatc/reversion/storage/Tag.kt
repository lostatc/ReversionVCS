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

package io.github.lostatc.reversion.storage

import io.github.lostatc.reversion.schema.TagEntity
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * A tag on a snapshot.
 */
interface Tag {
    /**
     * The name of the tag.
     *
     * This must be unique with respect to other tags in the same timeline.
     */
    var name: String

    /**
     * The description of the tag.
     */
    var description: String

    /**
     * Whether the tag should be kept forever.
     *
     * If this is `true`, the tag will not be automatically deleted.
     */
    var pinned: Boolean

    /**
     * The snapshot this tag is associated with.
     */
    val snapshot: Snapshot

    /**
     * The timeline this tag is a part of.
     */
    val timeline: Timeline
}

/**
 * An implementation of [Tag] which is backed by a relational database.
 */
data class DatabaseTag(val entity: TagEntity) : Tag {
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
        get() = transaction { DatabaseSnapshot(entity.snapshot) }

    override val timeline: DatabaseTimeline
        get() = transaction { DatabaseTimeline(entity.timeline) }
}