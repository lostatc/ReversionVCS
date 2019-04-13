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

package io.github.lostatc.reversion.cli

import io.github.lostatc.reversion.LOG_FILE_PATTERN
import java.util.logging.*

/**
 * A log formatter that formats messages to be shown to the user.
 */
private class UserFormatter : Formatter() {
    override fun format(record: LogRecord): String = "Error: ${record.message}\n"
}

/**
 * The logger to use for logging errors in the CLI.
 */
val logger: Logger = Logger.getLogger("io.github.lostatc.reversion.cli").apply {
    useParentHandlers = false

    addHandler(FileHandler(LOG_FILE_PATTERN, 10 * 1024 * 1024, 5, true).apply {
        formatter = SimpleFormatter()
    })

    addHandler(ConsoleHandler().apply {
        formatter = UserFormatter()
    })
}

/**
 * The uncaught exception handler to use in the CLI.
 */
val uncaughtExceptionHandler: Thread.UncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, throwable ->
    logger.log(Level.SEVERE, throwable.message, throwable)
}
