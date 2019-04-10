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

import com.github.ajalt.clikt.core.CliktError
import io.github.lostatc.reversion.api.*
import io.github.lostatc.reversion.storage.InvalidWorkDirException
import io.github.lostatc.reversion.storage.WorkDirectory
import java.nio.file.Path

/**
 * Get the repository with the given [path].
 *
 * @throw [CliktError] There is no repository at [path].
 */
fun getRepository(path: Path): Repository = try {
    StorageProvider.openRepository(path)
} catch (e: UnsupportedFormatException) {
    throw CliktError("No repository at '$path'.", e)
}

/**
 * Get a timeline.
 *
 * @param [repo] The path of the repository.
 * @param [name] The name of the timeline.
 *
 * @throws [CliktError] There is no timeline with the given [name].
 */
fun getTimeline(repo: Path, name: String): Timeline =
    getRepository(repo).getTimeline(name) ?: throw CliktError("No timeline named '$name'.")


/**
 * Get a snapshot.
 *
 * @param [repo] The path of the repository.
 * @param [timeline] The name of the timeline.
 * @param [revision] The revision number of the snapshot.
 *
 * @throws [CliktError] There is no snapshot with the given [revision].
 */
fun getSnapshot(repo: Path, timeline: String, revision: Int): Snapshot =
    getTimeline(repo, timeline).getSnapshot(revision) ?: throw CliktError("No snapshot with the revision '$revision'.")

/**
 * Get a version.
 *
 * @param [repo] The path of the repository.
 * @param [timeline] The name of the timeline.
 * @param [revision] The revision number of the snapshot.
 * @param [path] The relative path of the version.
 *
 * @throws [CliktError] There is no version with the given [path].
 */
fun getVersion(repo: Path, timeline: String, revision: Int, path: Path): Version =
    getSnapshot(repo, timeline, revision).getVersion(path) ?: throw CliktError("No version with the path '$path'.")

/**
 * Get a tag.
 *
 * @param [repo] The path of the repository.
 * @param [timeline] The name of the timeline.
 * @param [name] The name of the tag.
 */
fun getTag(repo: Path, timeline: String, name: String): Tag =
    getTimeline(repo, timeline).getTag(name) ?: throw CliktError("No tag named '$name'.")

fun getWorkDirectory(path: Path): WorkDirectory = try {
    WorkDirectory.open(path)
} catch (e: UnsupportedFormatException) {
    throw CliktError("The repository associated with '$path' does not exist.", e)
} catch (e: InvalidWorkDirException) {
    throw CliktError("The timeline associated with '$path' does not exist.", e)
}