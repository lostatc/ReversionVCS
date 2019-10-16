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

package io.github.lostatc.reversion

import io.github.lostatc.reversion.daemon.WatchDaemon
import io.github.lostatc.reversion.gui.Reversion
import it.sauronsoftware.junique.AlreadyLockedException
import it.sauronsoftware.junique.JUnique
import kotlinx.coroutines.runBlocking

/**
 * A unique ID for this application used for preventing multiple processes from being spawned.
 */
private const val APP_ID: String = "io.github.lostatc.reversion"

/**
 * Start the program.
 *
 * The program consists of a UI and a daemon which run in the same process. The daemon continues to run after the UI is
 * closed. On subsequent invocations of this method, instead of starting a new process, a message is sent to the
 * already-running process to display the UI.
 */
fun main(args: Array<String>) {
    try {
        // Check if application is already running.
        JUnique.acquireLock(APP_ID) {
            // If another instance sends a message, display the UI.
            Reversion.launchOrShow()
            null
        }
    } catch (e: AlreadyLockedException) {
        // Application is already running. Signal the running process to open the UI.
        // The message content doesn't matter.
        JUnique.sendMessage(APP_ID, "")
        return
    }

    // Application is not currently running. Start the daemon and the UI.
    runBlocking { WatchDaemon.start() }
    Reversion.launchOrShow()
}
