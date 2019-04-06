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

package io.github.lostatc.reversion.cli.tag

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

private val COMMAND_HELP: String = """
    Modify an existing tag.
""".trimIndent()

class Modify(val parent: Tag) : CliktCommand(help = COMMAND_HELP) {
    val timeline: String by argument(help = "The timeline the tag is in.")

    val name: String by argument(help = "The name of the tag.")

    val newName: String? by option("-n", "--name", help = "The new name of the tag.")

    val description: String by option("-d", "--description", help = "The description for the tag.")
        .default("")

    val pinned: Boolean by option("-p", "--pinned", help = "Never delete the snapshot with this tag.")
        .flag()

    override fun run() {
        // TODO: Not implemented.
    }
}
