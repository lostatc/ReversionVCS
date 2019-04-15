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

package io.github.lostatc.reversion

import ch.qos.logback.core.PropertyDefinerBase
import ch.qos.logback.core.spi.PropertyDefiner
import org.slf4j.LoggerFactory

/**
 * An exception handler that logs uncaught exceptions and prints them to stderr.
 */
val loggingExceptionHandler: Thread.UncaughtExceptionHandler =
    Thread.UncaughtExceptionHandler { _, throwable ->
        val logger = LoggerFactory.getLogger("io.github.lostatc.reversion")
        logger.error(throwable.message, throwable)
        System.err.println("Error: ${throwable.message}")
    }

/**
 * A [PropertyDefiner] for getting the path of the directory where log files are stored.
 */
class LogDirectoryPropertyDefiner : PropertyDefinerBase() {
    override fun getPropertyValue(): String = DATA_DIR.toString()
}
