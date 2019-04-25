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
import io.github.lostatc.reversion.api.Repository
import io.github.lostatc.reversion.api.Snapshot
import io.github.lostatc.reversion.api.StorageProvider
import io.github.lostatc.reversion.api.Timeline
import io.github.lostatc.reversion.api.UnsupportedFormatException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.util.UUID
import kotlin.streams.toList

/**
 * An exception that is thrown when a working directory is invalid.
 */
class InvalidWorkDirException(message: String) : Exception(message)

/**
 * Filters out paths with are descendants of another path in the iterable.
 */
private fun Iterable<Path>.flattenPaths(): List<Path> = this
    .filterNot { this.any { other -> it != other && it.startsWith(other) } }

/**
 * A [PathMatcher] that matches paths matched by any of the given [matchers].
 */
private data class MultiPathMatcher(val matchers: Iterable<PathMatcher>) : PathMatcher {
    override fun matches(path: Path): Boolean = matchers.any { it.matches(path) }

}

/**
 * A working directory.
 *
 * @param [path] The absolute path of the working directory.
 * @param [timeline] The timeline associated with this repository.
 */
data class WorkDirectory(val path: Path, val timeline: Timeline) {
    /**
     * The path of the directory containing metadata for the working directory.
     */
    private val hiddenPath: Path = path.resolve(relativeHiddenPath)

    /**
     * The path of the file containing ignore patterns.
     */
    private val ignorePath: Path = path.resolve(relativeIgnorePath)

    /**
     * The [PathMatcher] used to match paths to ignore.
     */
    private val ignoreMatcher: PathMatcher by lazy {
        val patterns = try {
            Files.readAllLines(ignorePath)
        } catch (e: NoSuchFileException) {
            emptyList<String>()
        }

        val matchers = patterns.map { path.fileSystem.getPathMatcher(it) } + PathMatcher { it == hiddenPath }
        MultiPathMatcher(matchers)
    }

    /**
     * Finds all the descendants of each of the given [paths] in the working directory.
     *
     * This returns only the paths of regular files that exist in the working directory and are not being ignored.
     *
     * @return A sequence of distinct paths relative to the working directory.
     */
    private fun walkDirectory(paths: Iterable<Path>): List<Path> = paths
        .map { path.relativize(it.toAbsolutePath()) }
        .flattenPaths()
        .flatMap { Files.walk(it).toList() }
        .filterNot { ignoreMatcher.matches(it) }
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
        .filterNot { ignoreMatcher.matches(it) }

    /**
     * Returns only the [files] which have uncommitted changes.
     *
     * - If the file does not exist, it is not returned.
     * - If the file exists and has no previous versions, it is returned.
     * - If the file exists and has different contents from the most recent version, it is returned.
     *
     * @param [files] The paths of regular files relative to this working directory.
     */
    private fun filterModified(files: Iterable<Path>): Iterable<Path> {
        val newestVersions = timeline
            .getLatestSnapshot()
            ?.listCumulativeVersions()
            ?.associateBy { it.path }
            ?: return files

        return files.filter {
            val absolutePath = path.resolve(it)
            val newestVersion = newestVersions[it]
            val fileExists = Files.exists(absolutePath)

            fileExists && (newestVersion == null || newestVersion.isChanged(it))
        }
    }

    /**
     * Creates a new snapshot containing the given [paths] and returns it.
     *
     * Passing the path of a directory commits all the files contained in it. By default, this only commits files with
     * uncommitted changes.
     *
     * @param [paths] The paths of files to commit.
     * @param [force] If `true`, commit files that have no uncommitted changes. If `false`, don't commit them.
     */
    fun commit(paths: Iterable<Path>, force: Boolean = false): Snapshot = walkDirectory(paths).let {
        timeline.createSnapshot(if (force) it else filterModified(it), path)
    }

    /**
     * Updates the given [paths] in the working directory.
     *
     * This updates the [paths] in the working directory to the state they were in in the snapshot with the given
     * [revision]. By default, this is the most recent revision. Uncommitted changes will not be overwritten unless
     * [overwrite] is `true`. Passing the path of a directory updates all the files contained in it.
     *
     * @param [paths] The paths of files to update.
     * @param [revision] The revision number. If `null`, use the most recent revision.
     * @param [overwrite] Whether to overwrite uncommitted changes.
     */
    fun update(paths: Iterable<Path>, revision: Int? = null, overwrite: Boolean = false) {
        val targetSnapshot = if (revision == null) {
            timeline.getLatestSnapshot() ?: return
        } else {
            timeline.getSnapshot(revision)
                ?: throw IllegalArgumentException("No snapshot with the revision '$revision'.")
        }

        val newestVersions = targetSnapshot.listCumulativeVersions().associateBy { it.path }
        val modifiedFiles = filterModified(paths).toSet()

        for (file in walkTimeline(paths)) {
            val absolutePath = path.resolve(file)
            val version = newestVersions[file] ?: continue
            version.checkout(absolutePath, overwrite = overwrite || file !in modifiedFiles)
        }
    }

    /**
     * Returns the status of the working directory.
     */
    fun getStatus(): Status = Status(
        filterModified(walkDirectory(listOf(path))).toSet()
    )

    /**
     * The status of the working directory.
     *
     * @param [modifiedFiles] The set of relative paths of regular files which have uncommitted changes.
     */
    data class Status(val modifiedFiles: Set<Path>) {
        /**
         * Whether any files in the working directory have uncommitted changes.
         */
        val isModified: Boolean
            get() = modifiedFiles.isNotEmpty()
    }

    /**
     * Information about the [WorkDirectory].
     *
     * @param [repositoryPath] The path of the [Repository] containing the [timeline].
     * @param [timelineID] The ID of the [Timeline] this working directory is associated with.
     */
    private data class Info(val repositoryPath: Path, val timelineID: UUID)

    companion object {
        /**
         * The relative path of the directory containing metadata for the working directory.
         */
        private val relativeHiddenPath: Path = Paths.get(".reversion")

        /**
         * The relative path of the file containing ignore patterns.
         */
        private val relativeIgnorePath: Path = Paths.get(".rvignore")

        /**
         * The relative path of the file containing information about the working directory.
         */
        private val relativeInfoPath: Path = relativeHiddenPath.resolve("info.json")

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
            val infoFile = path.resolve(relativeInfoPath)

            val (repoPath, timelineID) = Files.newBufferedReader(infoFile).use {
                gson.fromJson(it, Info::class.java)
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
            val hiddenDirectory = path.resolve(relativeHiddenPath)
            val infoFile = path.resolve(relativeInfoPath)

            if (Files.exists(hiddenDirectory)) throw InvalidWorkDirException(
                "This directory has already been initialized."
            )
            Files.createDirectories(hiddenDirectory)

            val info = Info(timeline.repository.path, timeline.uuid)
            Files.newBufferedWriter(infoFile).use {
                gson.toJson(info, it)
            }

            return open(path)
        }
    }
}