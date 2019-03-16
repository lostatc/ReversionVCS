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

package io.github.lostatc.reversion.cli.timeline

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

private val COMMAND_HELP: String = """
    Delete a timeline.

    The timeline is only deleted if it has no snapshots.
""".trimIndent()

class Remove(val parent: Timeline) : CliktCommand(help = COMMAND_HELP) {
    val name: String by argument(help = "The name of the timeline.")

    val force: Boolean by option(help = "Delete the timeline even if it has snapshots. All snapshots will be deleted.")
        .flag()

    override fun run() {
        // TODO: Not implemented.
    }
}
