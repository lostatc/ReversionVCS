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

package io.github.lostatc.reversion.cli.snapshot

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path

private val COMMAND_HELP: String = """
    List the snapshots in a timeline.
""".trimIndent()

class List(val parent: Snapshot) : CliktCommand(help = COMMAND_HELP) {
    val timeline: String by argument(help = "The timeline to list snapshots from.")

    val info: Boolean by option("-i", "--info", help = "Show detailed information about each snapshot.")
        .flag()

    val paths: kotlin.collections.List<Path> by option(
        "-p", "--path",
        help = "Show only snapshots containing a file with this path. This can be specified multiple times."
    )
        .path()
        .multiple()

    override fun run() {
        // TODO: Not implemented.
    }
}
