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
import io.github.lostatc.reversion.daemon.WatchDaemon.Companion.addWatch
import io.github.lostatc.reversion.daemon.WatchDaemon.Companion.removeWatch
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY


/**
 * A daemon which watches a working directory and commits any changes.
 *
 * This class stores a list of directories being watched in [dataFile] and automatically starts or stops watching them
 * when [dataFile] is updated. This file can be modified with [addWatch] and [removeWatch]. Each time a file is created
 * or modified in a watched directory, it is committed and past versions of it are cleaned up.
 *
 * @param [dataFile] The path of the file where the list of watched directories is stored.
 */
data class WatchDaemon(val dataFile: Path) : CoroutineScope by CoroutineScope(Dispatchers.Default) {
    /**
     * A map of directories being watched to the jobs which are watching them.
     */
    private val watches: MutableMap<Path, Job> = mutableMapOf()

    /**
     * Start watching directories based on the contents of the [dataFile].
     *
     * @throws [NoSuchFileException] The file [dataFile] does not exist.
     * @throws [IOException] An I/O error occurred.
     */
    fun run() {
        reload()

        FileSystemWatcher(dataFile.parent, recursive = false).use {
            for (event in it.events) {
                if (event.type == ENTRY_MODIFY && event.path == dataFile) {
                    reload()
                }
            }
        }
    }

    /**
     * Add and remove [watches] based on the contents of the [dataFile].
     */
    private fun reload() {
        val directories = getWatched(dataFile)

        for (addedDirectory in directories - watches.keys) {
            watches[addedDirectory] = launch { watch(addedDirectory) }
        }
        for (removedDirectory in watches.keys - directories) {
            watches.remove(removedDirectory)?.cancel()
        }
    }


    companion object {
        /**
         * The logger for the daemon.
         */
        private val logger: Logger = LoggerFactory.getLogger(WatchDaemon::class.java)

        /**
         * Watches the given [directory] for changes and commits them.
         */
        private fun watch(directory: Path) {
            val workDirectory = WorkDirectory.open(directory)

            FileSystemWatcher(directory, recursive = true).use {
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

        /**
         * The object for serializing/de-serializing objects as JSON.
         */
        private val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .registerTypeHierarchyAdapter(Path::class.java, PathTypeAdapter)
            .create()

        /**
         * Returns the list of directories being watched by the daemon with the given [dataFile].
         *
         * @throws [NoSuchFileException] The file [dataFile] does not exist.
         * @throws [IOException] An I/O error occurred.
         */
        private fun getWatched(dataFile: Path): Set<Path> =
            Files.newBufferedReader(dataFile).use { gson.fromJson(it) }

        /**
         * Tell the daemon with the given [dataFile] to start watching [directory].
         *
         * @return `true` if a watch was added for [directory], `false` if it was already being watched.
         */
        fun addWatch(directory: Path, dataFile: Path): Boolean {
            val directories = getWatched(dataFile)

            if (directory in directories) return false
            Files.newBufferedWriter(dataFile).use {
                gson.toJson(directories.plusElement(directory))
            }

            return true
        }

        /**
         * Tell the daemon with the given [dataFile] to stop watching [directory].
         *
         * @return `true` if a watch was stopped for [directory], `false` if it was not being watched.
         */
        fun removeWatch(directory: Path, dataFile: Path): Boolean {
            val directories = getWatched(dataFile)

            if (directory !in directories) return false
            Files.newBufferedReader(dataFile).use {
                gson.toJson(directories.minusElement(directory))
            }

            return true
        }
    }
}
