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
import com.github.ajalt.clikt.parameters.types.path
import io.github.lostatc.reversion.DEFAULT_REPO
import java.nio.file.Path
import java.nio.file.Paths

private val COMMAND_HELP: String = """
    Begin tracking changes in an existing directory.

    This associates the directory with a timeline, making it a working directory. The contents of the directory and the
    timeline are not modified.
""".trimIndent()

class Init : CliktCommand(help = COMMAND_HELP) {
    val timeline: String by argument(help = "The timeline to associate the directory with.")

    val repo: Path by option(help = "Use this repository instead of the default repository.")
        .path()
        .default(DEFAULT_REPO)

    val workDir: Path by option(
        "-w", "--work-dir", help = "Use this directory instead of the current working directory."
    )
        .path()
        .default(Paths.get("").toAbsolutePath())


    override fun run() {
        // TODO: Not implemented.
    }
}
