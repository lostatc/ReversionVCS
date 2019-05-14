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

package io.github.lostatc.reversion.cli

import io.github.lostatc.reversion.api.Snapshot
import io.github.lostatc.reversion.api.Version
import io.github.lostatc.reversion.storage.WorkDirectory
import java.nio.file.Path

/**
 * Get a snapshot.
 *
 * @param [workDirectory] The working directory.
 * @param [revision] The revision number of the snapshot.
 *
 * @throws [NoSuchElementException] There is not snapshot with the given revision.
 */
fun getSnapshot(workDirectory: WorkDirectory, revision: Int): Snapshot =
    workDirectory.timeline.snapshots[revision]
        ?: throw NoSuchElementException("No snapshot with the revision '$revision'.")

/**
 * Get a version.
 *
 * @param [workDirectory] The working directory.
 * @param [revision] The revision number of the snapshot.
 * @param [path] The relative path of the version.
 *
 * @throws [NoSuchElementException] There is no version with the given [path].
 */
fun getVersion(workDirectory: WorkDirectory, revision: Int, path: Path): Version =
    getSnapshot(workDirectory, revision).versions[path]
        ?: throw NoSuchElementException("No version with the path '$path'.")
