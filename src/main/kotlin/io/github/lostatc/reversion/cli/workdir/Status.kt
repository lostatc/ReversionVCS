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

package io.github.lostatc.reversion.cli.workdir

import com.github.ajalt.clikt.core.CliktCommand

private val COMMAND_HELP: String = """
    Show changes that have occurred since the most recent snapshot.
""".trimIndent()

class Status(val parent: WorkDir) : CliktCommand(help = COMMAND_HELP) {
    override fun run() {
        // TODO: Not implemented.
    }
}
