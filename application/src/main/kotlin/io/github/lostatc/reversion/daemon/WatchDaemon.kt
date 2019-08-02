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

import io.github.lostatc.reversion.storage.WorkDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY


/**
 * A daemon which directories a working directory and commits any changes.
 *
 * This class watches each directory in [directories] for changes. Each time a file is created or modified in a watched
 * directory, it is committed and past versions of it are cleaned up. This class automatically starts or stops watching
 * directories as they are added to or removed from [directories].
 *
 * @param [directories] The set of directories to watch.
 */
data class WatchDaemon(val directories: PersistentSet<Path>) : CoroutineScope by CoroutineScope(Dispatchers.Default) {
    /**
     * A map of directories being watched to the jobs which are watching them.
     */
    private val watches: MutableMap<Path, Job> = mutableMapOf()

    /**
     * Starts watching each directory in [directories].
     */
    fun run() {
        reload()
        directories.onChange { reload() }
    }

    /**
     * Adds and removes [watches] based on the contents of [directories].
     */
    private fun reload() {
        for (addedDirectory in directories.elements - watches.keys) {
            watches[addedDirectory] = launch { watch(addedDirectory) }
        }
        for (removedDirectory in watches.keys - directories.elements) {
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
