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
import io.github.lostatc.reversion.api.Configurator
import io.github.lostatc.reversion.api.fromJson
import io.github.lostatc.reversion.api.storage.IncompatibleRepositoryException
import io.github.lostatc.reversion.api.storage.InvalidRepositoryException
import io.github.lostatc.reversion.api.storage.OpenAttempt
import io.github.lostatc.reversion.api.storage.Repository
import io.github.lostatc.reversion.api.storage.Snapshot
import io.github.lostatc.reversion.api.storage.StorageProvider
import io.github.lostatc.reversion.api.storage.Timeline
import io.github.lostatc.reversion.api.storage.Version
import io.github.lostatc.reversion.serialization.RelativePathTypeAdapter
import io.github.lostatc.reversion.serialization.RuntimeTypeAdapterFactory
import org.apache.commons.io.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.util.UUID
import kotlin.streams.toList

/**
 * An exception indicating that a directory is not a working directory.
 */
class NotAWorkDirException(message: String) : Exception(message)

/**
 * Filters out paths with are descendants of another path in the iterable.
 */
private fun Iterable<Path>.flattenPaths(): List<Path> =
    filterNot { any { other -> it != other && it.startsWith(other) } }

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
     * The path of the file containing paths to ignore.
     */
    private val ignoreFile: Path = path.resolve(relativeIgnorePath)

    /**
     * The list of matchers for paths which are always ignored regardless of the contents of the ignore file.
     */
    val defaultIgnoreMatcher: PathMatcher = PrefixIgnoreMatcher(hiddenPath).toPathMatcher(path)

    /**
     * The repository associated with this working directory.
     */
    val repository: Repository
        get() = timeline.repository

    /**
     * Get the list of matchers for files that should be ignored from storage.
     */
    fun readMatchers(): List<IgnoreMatcher> = if (Files.exists(ignoreFile)) {
        Files.newBufferedReader(ignoreFile).use { matcherGson.fromJson<List<IgnoreMatcher>>(it) }
    } else {
        writeMatchers(emptyList())
        emptyList()
    }

    /**
     * Write the list of matchers for files that should be ignored to storage.
     */
    fun writeMatchers(matchers: List<IgnoreMatcher>) {
        Files.newBufferedWriter(ignoreFile, CREATE, TRUNCATE_EXISTING).use { matcherGson.toJson(matchers, it) }
    }

    /**
     * Get the [PathMatcher] for matching paths to be ignored.
     *
     * This combines the [defaultIgnoreMatcher] and the matchers read from [readMatchers].
     */
    private fun getIgnoreMatcher(): PathMatcher = MultiPathMatcher(
        readMatchers().map { it.toPathMatcher(path) }.plusElement(defaultIgnoreMatcher)
    )

    /**
     * Finds all the descendants of each of the given [paths] in the working directory.
     *
     * This returns only the paths of regular files that exist in the working directory and are not being ignored.
     *
     * @return A list of distinct paths relative to the working directory.
     */
    private fun walkDirectory(paths: Iterable<Path>): List<Path> {
        val ignoreMatcher = getIgnoreMatcher()
        return paths
            .map { it.toAbsolutePath().normalize() }
            .flattenPaths()
            .filter { Files.exists(it) }
            .flatMap { Files.walk(it).toList() }
            .filterNot { ignoreMatcher.matches(it) }
            .filter { Files.isRegularFile(it) }
            .map { path.relativize(it) }
    }

    /**
     * Finds all the descendants of each of the given [paths] in the timeline.
     *
     * This returns only the paths of regular files that exist in the timeline. They may or may not exist in the working
     * directory.
     *
     * @return A list of distinct paths relative to the working directory.
     */
    private fun walkTimeline(paths: Iterable<Path>): List<Path> {
        val ignoreMatcher = getIgnoreMatcher()
        return paths
            .map { it.toAbsolutePath().normalize() }
            .flattenPaths()
            .flatMap { parent -> timeline.paths.filter { path.resolve(it).startsWith(parent) } }
            .map { path.resolve(it) }
            .filterNot { ignoreMatcher.matches(it) }
            .map { path.relativize(it) }
    }

    /**
     * Returns only the [files] which have uncommitted changes.
     *
     * - If the file does not exist, it is not returned.
     * - If the file exists and has no previous versions, it is returned.
     * - If the file exists and has different contents from the most recent version, it is returned.
     *
     * @param [files] The paths of regular files relative to this working directory.
     */
    private fun filterModified(files: Iterable<Path>): List<Path> {
        val newestVersions = timeline
            .latestSnapshot
            ?.cumulativeVersions
            ?: return files.toList()

        return files.filter {
            val absolutePath = path.resolve(it)
            val newestVersion = newestVersions[it]
            val fileExists = Files.exists(absolutePath)

            fileExists && (newestVersion == null || newestVersion.isChanged(absolutePath))
        }
    }

    /**
     * Returns a list of all the tracked files in the working directory.
     */
    fun listFiles(): List<Path> = walkDirectory(listOf(path))

    /**
     * Creates a new snapshot containing the given [paths] and returns it.
     *
     * Passing the path of a directory commits all the files contained in it. By default, this only commits files with
     * uncommitted changes. Ignored files are not committed. If there are no files to commit, a snapshot is not created.
     *
     * @param [paths] The paths of files to commit.
     * @param [force] If `true`, commit files that have no uncommitted changes. If `false`, don't commit them.
     * @param [name] The initial name of the snapshot.
     * @param [description] The initial description of the snapshot.
     * @param [pinned] Whether the snapshot is pinned.
     *
     * @return The new snapshot or `null` if there were no files to commit.
     */
    fun commit(
        paths: Iterable<Path>,
        force: Boolean = false,
        name: String? = null,
        description: String = "",
        pinned: Boolean = false
    ): Snapshot? {
        val allFiles = walkDirectory(paths)
        val filesToCommit = if (force) allFiles else filterModified(allFiles)
        if (filesToCommit.isEmpty()) return null
        return timeline.createSnapshot(
            paths = filesToCommit,
            workDirectory = path,
            name = name,
            description = description,
            pinned = pinned
        )
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
            timeline.latestSnapshot ?: return
        } else {
            timeline.snapshots[revision]
                ?: throw IllegalArgumentException("No snapshot with the revision '$revision'.")
        }

        val modifiedFiles = filterModified(walkTimeline(paths)).toSet()

        for (file in walkTimeline(paths)) {
            val absolutePath = path.resolve(file)
            val version = targetSnapshot.cumulativeVersions[file] ?: continue
            version.checkout(absolutePath, overwrite = overwrite || file !in modifiedFiles)
        }

        logger.info("Updating files to snapshot $targetSnapshot.")
    }

    /**
     * Returns the status of the working directory.
     *
     * This shows which files have been modified since the last commit:
     * - If the file does not exist, it is not considered modified.
     * - If the file exists and has no previous versions, it is considered modified.
     * - If the file exists and has different contents from the most recent version, it is considered modified.
     */
    fun getStatus(): Status = Status(
        filterModified(walkDirectory(listOf(path))).toSet()
    )

    /**
     * [Commits][commit] the given [paths] and then [updates][update] them to the given [revision].
     *
     * This commits any uncommitted changes that would be overwritten and then restores those files. Passing the path of
     * a directory restores all the files contained in it.
     *
     * @param [paths] The paths of files to update.
     * @param [revision] The revision number. If `null`, use the most recent revision.
     */
    fun restore(paths: Iterable<Path>, revision: Int? = null) {
        commit(
            paths = paths,
            description = "This version was created to save the file before it was overwritten by a restore."
        )
        update(paths = paths, revision = revision)
    }

    /**
     * Opens the given [version] in the default application for its file type.
     *
     * This opens the file in a temporary directory and does not attempt to clean it up afterwards.
     */
    fun openInApplication(version: Version) {
        val tempDirectory = Files.createTempDirectory("reversion-")
        val targetPath = tempDirectory.resolve(version.path.fileName)
        version.checkout(targetPath)
        Desktop.getDesktop().open(targetPath.toFile())
    }

    /**
     * Deletes all version history for this working directory.
     *
     * This method de-initializes the working directory and deletes the repository associated with it. It does not
     * delete the current version of any file in the directory. After this method is called, [isWorkDirectory] should
     * return false for this [path].
     */
    fun delete() {
        repository.delete()
        FileUtils.deleteDirectory(hiddenPath.toFile())
        instanceCache.remove(path)
    }

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
     * @param [timeline] The [ID][Timeline.id] of the [Timeline] this working directory is associated with.
     */
    private data class Info(val timeline: UUID)

    companion object {
        /**
         * The logger for this class.
         */
        private val logger: Logger = LoggerFactory.getLogger(WorkDirectory::class.java)

        /**
         * The relative path of the directory containing metadata for the working directory.
         */
        private val relativeHiddenPath: Path = Paths.get(".reversion")

        /**
         * The relative path of the file containing paths to ignore.
         */
        private val relativeIgnorePath: Path = relativeHiddenPath.resolve("ignore.json")

        /**
         * The relative path of the file containing information about the working directory.
         */
        private val relativeInfoPath: Path = relativeHiddenPath.resolve("info.json")

        /**
         * The relative path of the repository associated with this working directory.
         */
        private val relativeRepoPath: Path = relativeHiddenPath.resolve("repository")

        /**
         * A cache of [WorkDirectory] instances.
         *
         * This allows previously-created [WorkDirectory] instances to be re-used.
         */
        private val instanceCache: MutableMap<Path, WorkDirectory> = mutableMapOf()

        /**
         * An object used for serializing data as JSON.
         */
        private val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .create()

        /**
         * An object used for serializing [IgnoreMatcher] objects as JSON.
         */
        private val matcherGson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .registerTypeHierarchyAdapter(Path::class.java, RelativePathTypeAdapter)
            .registerTypeAdapterFactory(
                RuntimeTypeAdapterFactory(IgnoreMatcher::class).apply {
                    registerSubtype(PrefixIgnoreMatcher::class)
                    registerSubtype(GlobIgnoreMatcher::class)
                    registerSubtype(RegexIgnoreMatcher::class)
                    registerSubtype(SizeIgnoreMatcher::class)
                    registerSubtype(ExtensionIgnoreMatcher::class)
                    registerSubtype(CategoryIgnoreMatcher::class)
                }
            )
            .create()

        /**
         * Returns whether the directory at the given [path] is a working directory.
         */
        fun isWorkDirectory(path: Path): Boolean = Files.isDirectory(path.resolve(relativeHiddenPath))

        /**
         * Opens the working directory at [path] and returns it.
         *
         * @param [path] The path of the working directory.
         *
         * @throws [IncompatibleRepositoryException] There is no installed provider compatible with the repository
         * associated with this working directory.
         * @throws [InvalidRepositoryException] There is an installed provider compatible with the repository associated
         * with this working directory but the repository cannot be read.
         * @throws [NotAWorkDirException] The working directory has not been initialized.
         */
        fun open(path: Path): OpenAttempt<WorkDirectory> = OpenAttempt.Success(
            instanceCache.getOrPut(path) { openNew(path).onFail { return@open it } }
        )

        /**
         * Opens the working directory associated with the file at the given [path].
         *
         * The working directory associated with [path] is the working directory that [path] is a descendant of.
         *
         * @param [path] The path of the working directory.
         *
         * @throws [IncompatibleRepositoryException] There is no installed provider compatible with the repository
         * associated with this working directory.
         * @throws [InvalidRepositoryException] There is an installed provider compatible with the repository associated
         * with this working directory but the repository cannot be read.
         * @throws [NotAWorkDirException] There is no working directory associated with the given [path].
         */
        fun openFromDescendant(path: Path): OpenAttempt<WorkDirectory> {
            var directory = path

            do {
                directory = directory.parent
                    ?: throw NotAWorkDirException("There is no working directory associated with this path.")
            } while (!isWorkDirectory(directory))

            return open(directory)
        }

        /**
         * Opens the working directory at [path] whether or not it already exists.
         *
         * @throws [IncompatibleRepositoryException] There is no installed provider compatible with the repository
         * associated with this working directory.
         * @throws [InvalidRepositoryException] There is an installed provider compatible with the repository associated
         * with this working directory but the repository cannot be read.
         * @throws [NotAWorkDirException] The working directory has not been initialized.
         */
        private fun openNew(path: Path): OpenAttempt<WorkDirectory> {
            val infoFile = path.resolve(relativeInfoPath)
            val repositoryPath = path.resolve(relativeRepoPath)

            if (!isWorkDirectory(path)) {
                throw NotAWorkDirException("This directory has not been initialized.")
            }

            val (timelineID) = Files.newBufferedReader(infoFile).use {
                gson.fromJson(it, Info::class.java)
            }

            val provider = StorageProvider.findProvider(repositoryPath)

            return provider.openRepository(repositoryPath).wrap {
                val timeline = it.timelines[timelineID] ?: error(
                    "The timeline associated with this working directory is not in the repository."
                )
                val workDirectory = WorkDirectory(path, timeline)

                logger.debug("Opening working directory $workDirectory.")

                workDirectory
            }
        }

        /**
         * Creates a new working directory at [path] with its own repository.
         *
         * If there is already a directory at [path], it is converted to a working directory.
         *
         * @param [path] The path of the working directory.
         * @param [provider] The storage provider to create the repository with.
         * @param [configurator] An object for configuring the repository.
         *
         * @throws [NotAWorkDirException] This directory has already been initialized.
         */
        fun init(
            path: Path,
            provider: StorageProvider,
            configurator: Configurator = Configurator.Default
        ): WorkDirectory {
            val hiddenDirectory = path.resolve(relativeHiddenPath)
            val infoFile = path.resolve(relativeInfoPath)
            val repositoryPath = path.resolve(relativeRepoPath)

            if (Files.exists(hiddenDirectory)) throw NotAWorkDirException(
                "This directory has already been initialized."
            )
            Files.createDirectories(hiddenDirectory)

            val repository = provider.createRepository(repositoryPath, configurator)
            val timeline = repository.createTimeline()

            val info = Info(timeline.id)
            Files.newBufferedWriter(infoFile).use {
                gson.toJson(info, it)
            }

            val workDirectory = open(path).onFail { error("The working directory could not be opened.") }

            logger.info("Initializing working directory '$workDirectory'.")

            return workDirectory
        }
    }
}
