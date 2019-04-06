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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.github.lostatc.reversion.DEFAULT_REPO
import java.nio.file.Path

private val COMMAND_HELP: String = """
    Get versions of files from a timeline.

    This gets a version of a file from a snapshot in a timeline and copies it to a local path. By default, the latest
    revision is chosen.
""".trimIndent()

class Checkout : CliktCommand(help = COMMAND_HELP) {
    val name: String by argument(help = "The name of the timeline.")

    val repo: Path by option(help = "Use this repository instead of the default repository.")
        .path()
        .default(DEFAULT_REPO)

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
