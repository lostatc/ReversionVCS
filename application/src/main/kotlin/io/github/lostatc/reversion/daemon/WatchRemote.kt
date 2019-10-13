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

package io.github.lostatc.reversion.daemon

import com.google.gson.GsonBuilder
import io.github.lostatc.reversion.DATA_DIR
import io.github.lostatc.reversion.api.Repository
import io.github.lostatc.reversion.api.RepositoryException
import io.github.lostatc.reversion.storage.PathTypeAdapter
import io.github.lostatc.reversion.storage.WorkDirectory
import io.github.lostatc.reversion.storage.fromJson
import javafx.beans.value.WritableValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.rmi.Remote
import java.rmi.RemoteException

/**
 * An object for serializing/deserializing objects as JSON.
 */
private val gson = GsonBuilder()
    .setPrettyPrinting()
    .registerTypeHierarchyAdapter(Path::class.java, PathTypeAdapter)
    .create()

/**
 * A [Remote] for accessing a [WatchDaemon] with Java RMI.
 */
interface WatchRemote : Remote {
    @Throws(RemoteException::class)
    fun getRegistered(): SerializableValue<String>

    @Throws(RemoteException::class)
    fun getTracked(): SerializableValue<String>
}

/**
 * Convert this object to a [WatchDaemon] which deserializes the values.
 */
fun WatchRemote.asDaemon(): WatchDaemon = object : WatchDaemon {
    override val registered: WritableValue<Set<Path>> = getRegistered().deserialized(gson, emptySet())

    override val tracked: WritableValue<Set<Path>> = getTracked().deserialized(gson, emptySet())

}

/**
 * A daemon which asynchronously runs jobs associated with working directories.
 */
interface WatchDaemon {
    /**
     * The set of paths of working directories which have been added in the UI.
     *
     * The repository [jobs][Repository.jobs] of each working directory in this set are run asynchronously.
     */
    val registered: WritableValue<Set<Path>>

    /**
     * The set of paths of working directories which have tracking changes enabled.
     *
     * Modified files in these directories are committed.
     */
    val tracked: WritableValue<Set<Path>>

    /**
     * Convert this object to a [Remote] which can be used with Java RMI.
     */
    fun asRemote(): WatchRemote = object : WatchRemote {
        @Throws(RemoteException::class)
        override fun getRegistered(): SerializableValue<String> = registered.serialized(gson)

        @Throws(RemoteException::class)
        override fun getTracked(): SerializableValue<String> = tracked.serialized(gson)
    }
}

/**
 * A [WatchDaemon] which persistently stores values in a file.
 */
object PersistentWatchDaemon : WatchDaemon, CoroutineScope by CoroutineScope(Dispatchers.Default) {
    /**
     * The file for persistently storing [registered] paths.
     */
    private val registeredFile = DATA_DIR.resolve("registered.json")

    /**
     * The file for persistently storing [tracked] paths.
     */
    private val trackedFile = DATA_DIR.resolve("tracked.json")

    /**
     * A map of paths of working directories to running repository jobs.
     */
    private val repositoryJobs = mutableMapOf<Path, Job>()

    /**
     * A map of paths of working directories to file watch jobs.
     */
    private val watchJobs = mutableMapOf<Path, Job>()

    override val registered: WritableValue<Set<Path>> = object : WritableValue<Set<Path>> {
        override fun setValue(value: Set<Path>) {
            for (newPath in value - repositoryJobs.keys) {
                repositoryJobs[newPath] = launch { runJobs(newPath) }
            }

            for (oldPath in repositoryJobs.keys - value) {
                repositoryJobs.remove(oldPath)?.cancel()
            }

            Files.newBufferedWriter(registeredFile).use { gson.toJson(value, it) }
        }

        override fun getValue(): Set<Path> = repositoryJobs.keys
    }

    override val tracked: WritableValue<Set<Path>> = object : WritableValue<Set<Path>> {
        override fun setValue(value: Set<Path>) {
            for (newPath in value - watchJobs.keys) {
                watchJobs[newPath] = launch { watch(newPath) }
            }

            for (oldPath in watchJobs.keys - value) {
                watchJobs.remove(oldPath)?.cancel()
            }

            Files.newBufferedWriter(trackedFile).use { gson.toJson(value, it) }
        }

        override fun getValue(): Set<Path> = watchJobs.keys
    }

    /**
     * The logger for the daemon.
     */
    private val logger: Logger = LoggerFactory.getLogger(PersistentWatchDaemon::class.java)

    /**
     * Read persisted values from storage and start watching directories and running jobs.
     */
    fun start() {
        registered.value = Files.newBufferedReader(registeredFile).use { gson.fromJson(it) }
        tracked.value = Files.newBufferedReader(trackedFile).use { gson.fromJson(it) }
    }

    /**
     * Runs the repository [jobs][Repository.jobs] associated with the given working [directory].
     */
    private suspend fun runJobs(directory: Path) = coroutineScope {
        val workDirectory = try {
            WorkDirectory.open(directory)
        } catch (e: RepositoryException) {
            return@coroutineScope
        }

        for (job in workDirectory.repository.jobs) {
            launch { job.run() }
        }
    }

    /**
     * Watches the given working [directory] for changes and commits them.
     */
    private fun watch(directory: Path) {
        val workDirectory = try {
            WorkDirectory.open(directory)
        } catch (e: RepositoryException) {
            return
        }

        // We only exclude the default ignored paths because reading the ignore pattern file on each watch event
        // would be expensive.
        FileSystemWatcher(
            workDirectory.path,
            recursive = true,
            coalesce = true,
            includeMatcher = PathMatcher { path -> workDirectory.defaultIgnoredPaths.all { !path.startsWith(it) } }
        ).use {
            for (event in it.events) {
                if (event.type == ENTRY_CREATE || event.type == ENTRY_MODIFY) {
                    try {
                        workDirectory.commit(listOf(event.path))
                        workDirectory.timeline.clean(listOf(event.path))
                    } catch (e: IOException) {
                        logger.error(e.message, e)
                    }
                }
            }
        }
    }
}
