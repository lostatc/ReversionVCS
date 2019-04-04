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

package io.github.lostatc.reversion.api

import java.nio.file.Path
import java.time.Instant

/**
 * A snapshot in a timeline.
 */
interface Snapshot {
    /**
     * The revision number of the snapshot.
     *
     * This is unique with respect to other snapshots in the same timeline.
     */
    val revision: Int

    /**
     * The time the snapshot was created.
     */
    val timeCreated: Instant

    /**
     * The timeline this snapshot is a part of.
     */
    val timeline: Timeline

    /**
     * The repository that this snapshot is a part of.
     */
    val repository: Repository

    /**
     * Adds the file with the given [path] to this snapshot and returns a version.
     */
    fun createVersion(path: Path): Version

    /**
     * Removes the version with the given [path] from this snapshot.
     *
     * @return `true` if the version was removed, `false` if it didn't exist.
     */
    fun removeVersion(path: Path): Boolean

    /**
     * Returns the version in this snapshot with the given [path].
     *
     * @return The version or `null` if it doesn't exist.
     */
    fun getVersion(path: Path): Version?

    /**
     * Returns a sequence of the versions in this snapshot.
     *
     * @param [parent] If not `null`, only the version which are descendants of this relative path will be returned.
     */
    fun listVersions(parent: Path? = null): Sequence<Version>

    /**
     * Adds a tag to this snapshot and returns it.
     *
     * @param [name] The name of the tag.
     * @param [description] The description of the tag.
     * @param [pinned] Whether the tag should be kept forever.
     */
    fun addTag(name: String, description: String = "", pinned: Boolean = true): Tag

    /**
     * Removes the tag with the given [name] from this snapshot.
     *
     * @return `true` if the tag was removed, `false` if it didn't exist.
     */
    fun removeTag(name: String): Boolean

    /**
     * Returns the tag in this snapshot with the given [name].
     *
     * @return The tag or `null` if it doesn't exist.
     */
    fun getTag(name: String): Tag?

    /**
     * Returns a sequence of the tags that are associated with this snapshot.
     */
    fun listTags(): Sequence<Tag>
}
