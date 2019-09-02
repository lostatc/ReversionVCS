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

import com.google.gson.GsonBuilder
import io.github.lostatc.reversion.storage.PathTypeAdapter
import io.github.lostatc.reversion.storage.WorkDirectory
import io.github.lostatc.reversion.storage.fromJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.rmi.Remote
import java.rmi.RemoteException

/**
 * A daemon which watches a working directory.
 *
 * This class watches a set of directories for changes.
 */
// Use [URI] instead of [Path] because all parameters must be [Serializable].
interface WatchDaemon : Remote {
    /**
     * Returns whether the given [directory] is being watched.
     *
     * @param [directory] The URI of the directory.
     */
    @Throws(RemoteException::class)
    operator fun contains(directory: URI): Boolean

    /**
     * Starts watching the given [directory].
     *
     * @param [directory] The URI of the directory.
     *
     * @return `true` if the watch was added, `false` if the directory was already being watched.
     */
    @Throws(RemoteException::class)
    fun addWatch(directory: URI): Boolean

    /**
     * Stops watching the given [directory].
     *
     * @param [directory] The URI of the directory.
     *
     * @return `true` if the watch was removed, `false` if the directory wasn't being watched.
     */
    @Throws(RemoteException::class)
    fun removeWatch(directory: URI): Boolean
}

/**
 * Returns whether the given [directory] is being watched.
 */
operator fun WatchDaemon.contains(directory: Path): Boolean = directory.toUri() in this

/**
 * Starts watching the given [directory].
 *
 * @return `true` if the watch was added, `false` if the directory was already being watched.
 */
fun WatchDaemon.addWatch(directory: Path): Boolean = addWatch(directory.toUri())

/**
 * Stops watching the given [directory].
 *
 * @return `true` if the watch was removed, `false` if the directory wasn't being watched.
 */
fun WatchDaemon.removeWatch(directory: Path): Boolean = removeWatch(directory.toUri())

/**
 * A daemon which watches a working directory and commits any changes.
 *
 * This class watches a set of directories for changes. Each time a file is created or modified in a watched directory,
 * it is committed and past versions of it are cleaned up. The set of watched directories is persisted to [persistFile].
 *
 * @param [persistFile] The file containing the set of watched directories.
 */
data class PersistentWatchDaemon(
    val persistFile: Path
) : WatchDaemon, CoroutineScope by CoroutineScope(Dispatchers.Default) {
    /**
     * A map of directories being watched to the jobs which are watching them.
     */
    private val watchJobs: MutableMap<Path, Job> = mutableMapOf()

    init {
        // Start watching all directories in the [persistFile].
        val watchedDirectories = Files.newBufferedReader(persistFile).use { gson.fromJson<Set<Path>>(it) }
        for (directory in watchedDirectories) {
            watchJobs[directory] = launch { watch(directory) }
        }
    }

    @Throws(RemoteException::class)
    override fun contains(directory: URI): Boolean = Paths.get(directory) in watchJobs.keys

    @Throws(RemoteException::class)
    override fun addWatch(directory: URI): Boolean {
        val directoryPath = Paths.get(directory)

        if (directoryPath in watchJobs) return false
        watchJobs[directoryPath] = launch { watch(directoryPath) }

        val newWatches = watchJobs.keys.plusElement(directoryPath)
        Files.newBufferedWriter(persistFile).use { gson.toJson(newWatches, it) }

        return true
    }

    @Throws(RemoteException::class)
    override fun removeWatch(directory: URI): Boolean {
        val directoryPath = Paths.get(directory)

        watchJobs.remove(directoryPath)?.cancel() ?: return false

        val newWatches = watchJobs.keys.minusElement(directoryPath)
        Files.newBufferedWriter(persistFile).use { gson.toJson(newWatches, it) }

        return true
    }


    companion object {
        /**
         * The logger for the daemon.
         */
        private val logger: Logger = LoggerFactory.getLogger(PersistentWatchDaemon::class.java)

        /**
         * An object for serializing/de-serializing objects as JSON.
         */
        private val gson = GsonBuilder()
            .setPrettyPrinting()
            .registerTypeHierarchyAdapter(Path::class.java, PathTypeAdapter)
            .create()

        /**
         * Watches the given [directory] for changes and commits them.
         */
        private fun watch(directory: Path) {
            val workDirectory = WorkDirectory.open(directory)

            // We only exclude the default ignored paths because reading the ignore pattern file on each watch event
            // would be expensive.
            FileSystemWatcher(
                directory,
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
}
