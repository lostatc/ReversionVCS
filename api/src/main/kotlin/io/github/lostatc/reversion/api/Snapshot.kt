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
     * A snapshot's revision number is always greater than the revision number of the previous snapshot. Revision
     * numbers are unique within the same timeline, but not across timelines.
     */
    val revision: Int

    /**
     * The user-provided name of the snapshot.
     */
    var name: String?

    /**
     * The name to display to the user.
     *
     * If [name] is not `null`, this is equal to [name].
     */
    val displayName: String
        get() = name ?: "Revision $revision"

    /**
     * The user-provided description of the snapshot.
     */
    var description: String

    /**
     * Whether the snapshot is pinned.
     *
     * If this is `true`, the tag will not be automatically deleted.
     */
    var pinned: Boolean

    /**
     * The time the snapshot was created.
     */
    val timeCreated: Instant

    /**
     * The versions in this snapshot indexed by their [path][Version.path].
     */
    val versions: Map<Path, Version>

    /**
     * The most recent version of each file as of this snapshot indexed by their [path][Version.path].
     *
     * This returns the newest version of each file that is not newer than this snapshot.
     */
    val cumulativeVersions: Map<Path, Version>
        get() = timeline
            .snapshots
            .values
            .sortedBy { it.revision }
            .filter { it.revision <= revision }
            .flatMap { versions.values }
            .associateBy { it.path }

    /**
     * The timeline this snapshot is a part of.
     */
    val timeline: Timeline

    /**
     * The repository that this snapshot is a part of.
     */
    val repository: Repository
        get() = timeline.repository

    /**
     * Removes the version with the given [path] from this snapshot.
     *
     * @param [path] The path of the file relative to its working directory.
     *
     * @return `true` if the version was removed, `false` if it didn't exist.
     */
    fun removeVersion(path: Path): Boolean
}
