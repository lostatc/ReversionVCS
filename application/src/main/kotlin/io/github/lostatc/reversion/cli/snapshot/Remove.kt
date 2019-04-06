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
import com.github.ajalt.clikt.parameters.types.int

private val COMMAND_HELP: String = """
    Remove a snapshot from a timeline.

    This deletes any data from the repository that is not associated with another snapshot.
""".trimIndent()

class Remove(val parent: Snapshot) : CliktCommand(help = COMMAND_HELP) {
    val timeline: String by argument(help = "The timeline the snapshot is in.")

    val revision: Int by argument(help = "The revision number of the snapshot.")
        .int()

    override fun run() {
        // TODO: Not implemented.
    }
}
