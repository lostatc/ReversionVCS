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

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.lostatc.reversion.api.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Information about a [Timeline].
 *
 * @param [repositoryPath] The path of the [Repository].
 * @param [timelineID] The ID of the [Timeline].
 */
private data class TimelineInfo(val repositoryPath: Path, val timelineID: UUID)

/**
 * An exception that is thrown when a working directory is invalid.
 */
class InvalidWorkDirException(message: String) : Exception(message)

/**
 * The status of the files in a working directory.
 *
 * @param [modified] The paths that have been modified since the most recent commit.
 * @param [added] The paths that have been added since the most recent commit.
 * @param [removed] The path that have been removed since the most recent commit.
 */
data class WorkDirectoryStatus(
    val added: Set<Path>,
    val removed: Set<Path>,
    val modified: Set<Path>
)

/**
 * A working directory.
 *
 * @param [path] The path of the working directory.
 * @param [timeline] The timeline associated with this repository.
 */
data class WorkDirectory(val path: Path, val timeline: Timeline) {
    /**
     * Creates a new snapshot containing the given [paths] and returns it.
     *
     * The given [paths] can be absolute or relative to this directory. If [paths] is `null`, then every file in the
     * directory is committed.
     */
    fun commit(paths: Set<Path>? = null): Snapshot {
        TODO("not implemented")
    }

    /**
     * Updates the given [paths] in the working directory.
     *
     * This updates the [paths] in the working directory to the state they were in in the snapshot with the given
     * [revision]. Uncommitted changes will not be overwritten unless [overwrite] is `true`.
     *
     * @param [paths] The paths to update. If `null`, all paths in the directory are updated.
     * @param [revision] The revision number. If `null`, the most recent revision is selected.
     * @param [overwrite] Whether to overwrite uncommitted changes.
     */
    fun update(paths: Set<Path>? = null, revision: Int? = null, overwrite: Boolean = false) {
        TODO("not implemented")
    }

    /**
     * Returns the status of the working directory.
     */
    fun status(): WorkDirectoryStatus {
        TODO("not implemented")
    }

    companion object {
        /**
         * The relative path of the hidden directory containing metadata for the working directory.
         */
        private val relativeHiddenPath: Path = Paths.get(".reversion")

        /**
         * The relative path of the file containing information about the timeline.
         */
        private val relativeTimelineInfoPath: Path = relativeHiddenPath.resolve("timeline.json")

        /**
         * An object used for serializing data as JSON.
         */
        private val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Path::class.java, PathTypeAdapter)
            .create()

        /**
         * Opens the working directory at [path] and returns it.
         *
         * @throws [UnsupportedFormatException] There is no installed provider compatible with the repository associated
         * with this working directory.
         * @throws [InvalidWorkDirException] The timeline associated with this working directory is not in the
         * repository.
         */
        fun open(path: Path): WorkDirectory {
            val (repoPath, timelineID) = Files.newBufferedReader(path).use {
                gson.fromJson(it, TimelineInfo::class.java)
            }

            val repository = StorageProvider.openRepository(repoPath)
            val timeline = repository.getTimeline(timelineID) ?: throw InvalidWorkDirException(
                "The timeline associated with this working directory is not in the repository."
            )

            return WorkDirectory(path, timeline)
        }

        /**
         * Creates a new working directory at [path] that is associated with [timeline].
         *
         * If there is already a directory at [path], it is converted to a working directory.
         *
         * @throws [InvalidWorkDirException] This directory has already been initialized.
         */
        fun init(path: Path, timeline: Timeline): WorkDirectory {
            val timelineInfo = TimelineInfo(timeline.repository.path, timeline.uuid)

            val hiddenDirectory = path.resolve(relativeHiddenPath)
            val timelineInfoFile = path.resolve(relativeTimelineInfoPath)

            if (Files.exists(hiddenDirectory)) throw InvalidWorkDirException(
                "This directory has already been initialized."
            )
            Files.createDirectories(hiddenDirectory)

            Files.newBufferedWriter(timelineInfoFile).use {
                gson.toJson(timelineInfo, it)
            }

            return open(path)
        }
    }
}