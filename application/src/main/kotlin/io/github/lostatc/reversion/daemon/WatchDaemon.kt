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

import io.github.lostatc.reversion.storage.WorkDirectory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY


/**
 * A daemon which watches a working directory and commits any changes.
 *
 * Each time a file is created or modified, it is committed and past versions of it are cleaned up.
 *
 * @param [workDirectory] The working directory to watch.
 */
data class WatchDaemon(val workDirectory: WorkDirectory) {

    /**
     * The logger for the daemon.
     */
    private val logger: Logger = LoggerFactory.getLogger(WatchDaemon::class.java)

    /**
     * Constructs a [WatchDaemon] from the [path] of a working directory.
     */
    constructor(path: Path) : this(WorkDirectory.open(path))

    fun run() {
        FileSystemWatcher(workDirectory.path, recursive = true).use {
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
