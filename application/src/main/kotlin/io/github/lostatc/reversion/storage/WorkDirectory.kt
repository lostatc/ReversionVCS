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

package io.github.lostatc.reversion.storage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.lostatc.reversion.api.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.streams.toList

/**
 * An exception that is thrown when a working directory is invalid.
 */
class InvalidWorkDirException(message: String) : Exception(message)

/**
 * Information about a [Timeline].
 *
 * @param [repositoryPath] The path of the [Repository].
 * @param [timelineID] The ID of the [Timeline].
 */
private data class TimelineInfo(val repositoryPath: Path, val timelineID: UUID)

/**
 * The status of a working directory.
 *
 * @param [modifiedFiles] The set of relative paths of regular files which have uncommitted changes.
 */
data class DirectoryStatus(val modifiedFiles: Set<Path>) {
    /**
     * Whether any files in the working directory have uncommitted changes.
     */
    val isModified: Boolean
        get() = modifiedFiles.isNotEmpty()
}

/**
 * Filters out paths with are descendants of another path in the iterable.
 */
private fun Iterable<Path>.flattenPaths(): List<Path> = this
    .filterNot { this.any { other -> it != other && it.startsWith(other) } }

/**
 * A working directory.
 *
 * @param [path] The absolute path of the working directory.
 * @param [timeline] The timeline associated with this repository.
 */
data class WorkDirectory(val path: Path, val timeline: Timeline) {
    /**
     * Finds all the descendants of each of the given [paths] in the working directory.
     *
     * This returns only the paths of regular files that exist in the working directory.
     *
     * @return A sequence of distinct paths relative to the working directory.
     */
    private fun walkDirectory(paths: Iterable<Path>): List<Path> = paths
        .map { path.relativize(it.toAbsolutePath()) }
        .flattenPaths()
        .flatMap { Files.walk(it).toList() }
        .filter { Files.isRegularFile(it) }

    /**
     * Finds all the descendants of each of the given [paths] in the timeline.
     *
     * This returns only the paths of regular files that exist in the timeline. They may or may not exist in the working
     * directory.
     *
     * @return A sequence of distinct paths relative to the working directory.
     */
    private fun walkTimeline(paths: Iterable<Path>): List<Path> = paths
        .map { path.relativize(it.toAbsolutePath()) }
        .flattenPaths()
        .flatMap { timeline.listPaths(it) }

    /**
     * Creates a new snapshot containing the given [paths] and returns it.
     *
     * Passing the path of a directory commits all the files contained in it. By default, this only commits files with
     * uncommitted changes.
     *
     * @param [paths] The paths of files to commit.
     * @param [force] If `true`, commit files that have no uncommitted changes. If `false`, don't commit them.
     */
    fun commit(paths: Iterable<Path>, force: Boolean = false): Snapshot = timeline.createSnapshot(
        walkDirectory(paths).filter { force || isModified(it) }.asIterable(), path
    )

    /**
     * Updates the given [paths] in the working directory.
     *
     * This updates the [paths] in the working directory to the state they were in in the snapshot with the given
     * [revision]. By default, this is the most recent revision. Uncommitted changes will not be overwritten unless
     * [overwrite] is `true`. Passing the path of a directory updates all the files contained in it.
     *
     * @param [paths] The paths of files to update.
     * @param [revision] The revision number.
     * @param [overwrite] Whether to overwrite uncommitted changes.
     */
    fun update(paths: Iterable<Path>, revision: Int = Int.MAX_VALUE, overwrite: Boolean = false) {
        for (relativePath in walkTimeline(paths)) {
            val absolutePath = path.resolve(relativePath)

            // Iterate over the versions of this file from newest to oldest.
            for (version in timeline.listVersions(relativePath)) {
                // Get the version from the given [revision] or the newest version that comes before it.
                if (version.snapshot.revision <= (revision)) {
                    version.checkout(absolutePath, overwrite = overwrite || !isModified(path))
                    break
                }
            }
        }
    }

    /**
     * Returns whether the given [file] has uncommitted changes.
     *
     * - If the file exists and has no previous versions, this returns `true`.
     * - If the file does not exist and has no previous versions, this returns `false`.
     * - If the file does not exist but has previous versions, this returns `false`.
     *
     * @param [file] The path of a regular file relative to this working directory.
     */
    fun isModified(file: Path): Boolean {
        val absolutePath = path.resolve(file)
        val newestVersion = timeline.listVersions(file).firstOrNull()
        val fileExists = Files.exists(absolutePath)

        return if (newestVersion == null) {
            fileExists
        } else {
            if (fileExists) newestVersion.isChanged(file) else false
        }
    }

    /**
     * Returns the status of the working directory.
     */
    fun getStatus(): DirectoryStatus = DirectoryStatus(
        walkDirectory(listOf(path)).filter { isModified(it) }.toSet()
    )

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