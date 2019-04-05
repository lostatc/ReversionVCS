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

package io.github.lostatc.reversion.cli.repo

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

private val COMMAND_HELP: String = """
    Create a new empty repository.

    By default, the repository is created using the default storage provider with the default configuration.
""".trimIndent()

class Create(val parent: Repo) : CliktCommand(help = COMMAND_HELP) {
    val interactive: Boolean by option("-i", "--interactive", help = "Interactively configure the repository.")
        .flag()

    override fun run() {
        // TODO: Not implemented.
    }
}