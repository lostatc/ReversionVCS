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

package io.github.lostatc.reversion.api

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

    /**
     * The repository that this tag is a part og.
     */
    val repository: Repository
}
