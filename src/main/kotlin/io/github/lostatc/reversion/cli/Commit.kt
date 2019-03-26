/*
 * Copyright © 2019 Garrett Powell
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

package io.github.lostatc.reversion.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import java.nio.file.Paths

private val COMMAND_HELP: String = """
    Commit changes to the directory, creating a new snapshot.

    If no paths are specified, the entire working directory is committed.
""".trimIndent()

class Commit : CliktCommand(help = COMMAND_HELP) {
    val paths: List<Path> by argument(help = "The paths of files to commit.")
        .path(exists = true)
        .multiple()

    val workDir: Path by option(
        "-w", "--work-dir", help = "Use this directory instead of the current working directory."
    )
        .path()
        .default(Paths.get("").toAbsolutePath())

    override fun run() {
        // TODO: Not implemented.
    }
}
