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

package io.github.lostatc.reversion.cli

import io.github.lostatc.reversion.api.Repository
import io.github.lostatc.reversion.api.Snapshot
import io.github.lostatc.reversion.api.StorageProvider
import io.github.lostatc.reversion.api.Tag
import io.github.lostatc.reversion.api.Timeline
import io.github.lostatc.reversion.api.UnsupportedFormatException
import io.github.lostatc.reversion.api.Version
import io.github.lostatc.reversion.storage.InvalidWorkDirException
import io.github.lostatc.reversion.storage.WorkDirectory
import java.nio.file.Path

/**
 * An exception that is thrown when a resource could not be found.
 */
class ResourceNotFoundException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Get the repository with the given [path].
 *
 * @throw [ResourceNotFoundException] There is no repository at [path].
 */
fun getRepository(path: Path): Repository = try {
    StorageProvider.openRepository(path)
} catch (e: UnsupportedFormatException) {
    throw ResourceNotFoundException("No repository at '$path'.", e)
}

/**
 * Get a timeline.
 *
 * @param [repo] The path of the repository.
 * @param [name] The name of the timeline.
 *
 * @throws [ResourceNotFoundException] There is no timeline with the given [name].
 */
fun getTimeline(repo: Path, name: String): Timeline =
    getRepository(repo).timelinesByName[name] ?: throw ResourceNotFoundException("No timeline named '$name'.")


/**
 * Get a snapshot.
 *
 * @param [repo] The path of the repository.
 * @param [timeline] The name of the timeline.
 * @param [revision] The revision number of the snapshot.
 *
 * @throws [ResourceNotFoundException] There is no snapshot with the given [revision].
 */
fun getSnapshot(repo: Path, timeline: String, revision: Int): Snapshot =
    getTimeline(repo, timeline).snapshots[revision]
        ?: throw ResourceNotFoundException("No snapshot with the revision '$revision'.")

/**
 * Get a version.
 *
 * @param [repo] The path of the repository.
 * @param [timeline] The name of the timeline.
 * @param [revision] The revision number of the snapshot.
 * @param [path] The relative path of the version.
 *
 * @throws [ResourceNotFoundException] There is no version with the given [path].
 */
fun getVersion(repo: Path, timeline: String, revision: Int, path: Path): Version =
    getSnapshot(repo, timeline, revision).versions[path]
        ?: throw ResourceNotFoundException("No version with the path '$path'.")

/**
 * Get a tag.
 *
 * @param [repo] The path of the repository.
 * @param [timeline] The name of the timeline.
 * @param [name] The name of the tag.
 */
fun getTag(repo: Path, timeline: String, revision: Int, name: String): Tag =
    getSnapshot(repo, timeline, revision).tags[name] ?: throw ResourceNotFoundException("No tag named '$name'.")

fun getWorkDirectory(path: Path): WorkDirectory = try {
    WorkDirectory.open(path)
} catch (e: UnsupportedFormatException) {
    throw ResourceNotFoundException("The repository associated with '$path' does not exist.", e)
} catch (e: InvalidWorkDirException) {
    throw ResourceNotFoundException("The timeline associated with '$path' does not exist.", e)
}