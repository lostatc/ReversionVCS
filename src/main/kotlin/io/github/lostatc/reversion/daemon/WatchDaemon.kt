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

package io.github.lostatc.reversion.daemon

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.lostatc.reversion.DATA_DIR
import io.github.lostatc.reversion.api.fromJson
import io.github.lostatc.reversion.api.storage.Repository
import io.github.lostatc.reversion.api.storage.RepositoryException
import io.github.lostatc.reversion.gui.models.StorageModel.storageActor
import io.github.lostatc.reversion.gui.models.WorkDirectoryState
import io.github.lostatc.reversion.gui.wrap
import io.github.lostatc.reversion.serialization.PathTypeAdapter
import io.github.lostatc.reversion.storage.WorkDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import kotlin.text.Typography.registered

/**
 * Deserializes an object from [file] or returns [default] if [file] doesn't exist.
 */
private inline fun <reified T> Gson.fromFileOrDefault(file: Path, default: T): T = try {
    Files.newBufferedReader(file).use { fromJson(it) }
} catch (e: NoSuchFileException) {
    default
}

/**
 * A daemon which runs jobs for working directories in the background.
 *
 * Jobs running in this daemon keep running after the main thread exits.
 */
object WatchDaemon : CoroutineScope by CoroutineScope(Dispatchers.Default) {
    /**
     * The file for persistently storing [registered] paths.
     */
    private val registeredFile = DATA_DIR.resolve("registered.json")

    /**
     * The file for persistently storing [tracked] paths.
     */
    private val trackedFile = DATA_DIR.resolve("tracked.json")

    /**
     * A mutex for synchronizing collections in this daemon.
     */
    private val lock = Mutex()

    /**
     * A map of paths of working directories to running repository jobs.
     */
    private val repositoryJobs = mutableMapOf<Path, Job>()

    /**
     * A map of paths of working directories to file watch jobs.
     */
    private val watchJobs = mutableMapOf<Path, Job>()

    /**
     * The paths of working directories which have been added in the UI.
     *
     * The repository [jobs][Repository.jobs] for each of these directories are run in the background.
     */
    val registered: SynchronizedSet<Path> = object : SynchronizedSet<Path> {
        override suspend fun add(element: Path): Boolean = withLock {
            if (element in repositoryJobs) return@withLock false
            repositoryJobs[element] = launch { runJobs(element) }
            withContext(Dispatchers.IO) {
                Files.newBufferedWriter(registeredFile).use { gson.toJson(repositoryJobs.keys, it) }
            }
            true
        }

        override suspend fun remove(element: Path): Boolean = withLock {
            if (element !in repositoryJobs) return@withLock false
            repositoryJobs.remove(element)?.cancel()
            withContext(Dispatchers.IO) {
                Files.newBufferedWriter(registeredFile).use { gson.toJson(repositoryJobs.keys, it) }
            }
            true
        }

        override suspend fun contains(element: Path): Boolean = withLock { element in repositoryJobs.keys }

        override suspend fun toSet(): Set<Path> = withLock { repositoryJobs.keys.toSet() }
    }

    /**
     * The paths of working directories which have tracking changes enabled.
     *
     * Changes in each of these directories are committed in the background.
     */
    val tracked: SynchronizedSet<Path> = object : SynchronizedSet<Path> {
        override suspend fun add(element: Path): Boolean = withLock {
            if (element in watchJobs) return@withLock false
            watchJobs[element] = launch { watch(element) }
            withContext(Dispatchers.IO) {
                Files.newBufferedWriter(trackedFile).use { gson.toJson(watchJobs.keys, it) }
            }
            true
        }

        override suspend fun remove(element: Path): Boolean = withLock {
            if (element !in watchJobs) return@withLock false
            watchJobs.remove(element)?.cancel()
            withContext(Dispatchers.IO) {
                Files.newBufferedWriter(trackedFile).use { gson.toJson(watchJobs.keys, it) }
            }
            true
        }

        override suspend fun contains(element: Path): Boolean = withLock { element in watchJobs.keys }

        override suspend fun toSet(): Set<Path> = withLock { watchJobs.keys.toSet() }

    }

    /**
     * The logger for the daemon.
     */
    private val logger = LoggerFactory.getLogger(WatchDaemon::class.java)

    /**
     * An object for serializing/deserializing JSON.
     */
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeHierarchyAdapter(Path::class.java, PathTypeAdapter)
        .create()

    /**
     * Lock the given [action] with [lock] in a new coroutine context.
     */
    private suspend fun <T> withLock(action: suspend () -> T): T = withContext(Dispatchers.Default) {
        lock.withLock { action() }
    }

    /**
     * Read persisted values from storage and start watching directories and running repository jobs.
     */
    suspend fun start() {
        withContext(Dispatchers.IO) {
            registered.addAll(gson.fromFileOrDefault(registeredFile, emptySet()))
            tracked.addAll(gson.fromFileOrDefault(trackedFile, emptySet()))
        }
    }

    /**
     * Runs the repository [jobs][Repository.jobs] associated with the given working [directory].
     */
    private fun runJobs(directory: Path) {
        val workDirectory = try {
            WorkDirectory.open(directory).onFail { throw RepositoryException("Repository failed to open with $it") }
        } catch (e: RepositoryException) {
            return
        }

        for (job in workDirectory.repository.jobs) {
            launch { job.run() }
        }
    }

    /**
     * Watches the given working [directory] for changes and commits them.
     */
    private suspend fun watch(directory: Path) {
        val workDir = try {
            WorkDirectory.open(directory).onFail { throw RepositoryException("Repository failed to open with $it") }
        } catch (e: RepositoryException) {
            return
        }

        val state = storageActor.wrap(WorkDirectoryState(workDir))

        // We only exclude the default ignored paths because reading the ignore pattern file on each watch event
        // would be expensive.
        FileSystemWatcher(
            workDir.path,
            recursive = true,
            coalesce = true,
            includeMatcher = PathMatcher { !workDir.defaultIgnoreMatcher.matches(it) }
        ).use {
            for (event in it.events) {
                if (event.type == ENTRY_CREATE || event.type == ENTRY_MODIFY) {
                    val relativePath = directory.relativize(event.path)
                    try {
                        state.executeAsync {
                            workDirectory.commit(listOf(event.path))
                            workDirectory.timeline.clean(listOf(relativePath))
                        }.await()
                    } catch (e: IOException) {
                        logger.error(e.message, e)
                    }
                }
            }
        }
    }
}
