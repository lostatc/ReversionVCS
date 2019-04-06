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

package io.github.lostatc.reversion.cli.tag

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

private val COMMAND_HELP: String = """
    Create a new tag.
""".trimIndent()

class Create(val parent: Tag) : CliktCommand(help = COMMAND_HELP) {
    val timeline: String by argument(help = "The timeline to create the tag in.")

    val revision: String by argument(help = "The revision number of the snapshot to tag.")

    val name: String by argument(help = "The name of the tag.")

    val description: String by option("-d", "--description", help = "The description for the tag.")
        .default("")

    val pinned: Boolean by option("-p", "--pinned", help = "Never delete the snapshot with this tag.")
        .flag()

    override fun run() {
        // TODO: Not implemented.
    }
}