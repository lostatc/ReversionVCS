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
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.lostatc.reversion.DEFAULT_REPO
import java.nio.file.Path

class Timeline : CliktCommand(
    help = """
    Manage timelines.
"""
) {
    val repo: Path by option(help = "Use this repository instead of the default repository.")
        .path()
        .default(DEFAULT_REPO)

    init {
        subcommands(
            TimelineCreate(this),
            TimelineRemove(this),
            TimelineModify(this),
            TimelineList(this),
            TimelineInfo(this)
        )
    }

    override fun run() {
        // TODO: Not implemented.
    }
}

class TimelineCreate(val parent: Timeline) : CliktCommand(
    name = "create", help = """
    Create a new timeline.
"""
) {
    val name: String by argument(help = "The name of the timeline.")

    override fun run() {
        // TODO: Not implemented.
    }
}

class TimelineRemove(val parent: Timeline) : CliktCommand(
    name = "remove", help = """
    Delete a timeline.

    The timeline is only deleted if it has no snapshots.
"""
) {
    val name: String by argument(help = "The name of the timeline.")

    val force: Boolean by option(help = "Delete the timeline even if it has snapshots. All snapshots will be deleted.")
        .flag()

    override fun run() {
        // TODO: Not implemented.
    }
}

class TimelineModify(val parent: Timeline) : CliktCommand(
    name = "modify", help = """
    Modify an existing timeline.
"""
) {
    val name: String by argument(help = "The name of the timeline.")

    val newName: String? by option("--name", help = "The new name of the timeline.")

    override fun run() {
        // TODO: Not implemented.
    }
}

class TimelineList(val parent: Timeline) : CliktCommand(
    name = "list", help = """
    List the timelines in a repository.
"""
) {
    val name: String by argument(help = "The name of the timeline.")

    val info: Boolean by option("-i", "--info", help = "Show detailed information about each timeline.")
        .flag()

    override fun run() {
        // TODO: Not implemented.
    }
}

class TimelineInfo(val parent: Timeline) : CliktCommand(
    name = "info", help = """
    Show information about a timeline.
"""
) {
    val name: String by argument(help = "The name of the timeline.")

    override fun run() {
        // TODO: Not implemented.
    }
}
