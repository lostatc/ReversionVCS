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

import io.github.lostatc.reversion.DATA_DIR
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * An exception handler that logs uncaught exceptions and prints them to stderr.
 */
val loggingExceptionHandler: Thread.UncaughtExceptionHandler =
    Thread.UncaughtExceptionHandler { _, throwable ->
        val logger = LoggerFactory.getLogger("io.github.lostatc.reversion.daemon")
        logger.error(throwable.message, throwable)
        System.err.println("Error: ${throwable.message}")
    }

/**
 * The path of the file where the list of watched directories is stored.
 */
val DAEMON_DATA_FILE: Path = DATA_DIR.resolve("watchedDirectories.json")

/**
 * Start the daemon.
 */
fun main() {
    // Log any uncaught exceptions and print them to stderr.
    Thread.setDefaultUncaughtExceptionHandler(loggingExceptionHandler)

    // Start the daemon.
    WatchDaemon(DAEMON_DATA_FILE).run()
}
