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

import org.slf4j.LoggerFactory
import java.rmi.registry.LocateRegistry
import java.rmi.server.UnicastRemoteObject

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
 * The port on which the RMI registry accepts requests.
 */
private const val REGISTRY_PORT: Int = 1099

/**
 * The name to bind the [WatchRemote] stub to in the RMI registry.
 */
const val STUB_NAME: String = "io.github.lostatc.reversiond"

/**
 * Start the daemon.
 */
fun main() {
    // Log any uncaught exceptions and print them to stderr.
    Thread.setDefaultUncaughtExceptionHandler(loggingExceptionHandler)

    // Start the daemon.
    PersistentWatchDaemon.start()

    // Make the daemon accessible through Java RMI.
    val daemonStub = UnicastRemoteObject.exportObject(PersistentWatchDaemon.asRemote(), 0) as WatchRemote
    val registry = LocateRegistry.createRegistry(REGISTRY_PORT)
    registry.bind(STUB_NAME, daemonStub)
}
