/*
 * Copyright © 2019 Wren Powell
 *
 * This file is part of reversion.
 *
 * reversion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * reversion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with reversion.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.lostatc.reversion.cli.timeline

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path

private val COMMAND_HELP: String = """
    Get versions of files from a timeline.

    This gets a version of a file from a snapshot in a timeline and copies it to a local path. By default, the latest
    revision is chosen.
""".trimIndent()

class Checkout(val parent: Timeline) : CliktCommand(help = COMMAND_HELP) {
    val name: String by argument(help = "The name of the timeline.")

    val source: Path by argument(help = "The relative path of the file to retrieve from the timeline.")
        .path()

    val dest: Path by argument(help = "The path to copy the file to.")
        .path()

    val revision: Int? by option(help = "The revision number of the snapshot to get the file from.")
        .int()

    override fun run() {
        // TODO: Not implemented.
    }
}
