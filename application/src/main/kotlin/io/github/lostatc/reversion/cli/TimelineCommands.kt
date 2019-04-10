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
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.lostatc.reversion.DEFAULT_REPO
import java.nio.file.Path

class TimelineCommand : CliktCommand(
    name = "timeline", help = """
    Manage timelines.
"""
) {
    val repoPath: Path by option("--repo", help = "Use this repository instead of the default repository.")
        .path()
        .default(DEFAULT_REPO)

    init {
        subcommands(
            TimelineCreateCommand(this),
            TimelineRemoveCommand(this),
            TimelineModifyCommand(this),
            TimelineListCommand(this),
            TimelineInfoCommand(this)
        )
    }

    override fun run() = Unit
}

class TimelineCreateCommand(val parent: TimelineCommand) : CliktCommand(
    name = "create", help = """
    Create a new timeline.
"""
) {
    val timelineName: String by argument("NAME", help = "The name of the timeline.")

    override fun run() {
        val repository = getRepository(parent.repoPath)
        repository.createTimeline(timelineName)
    }
}

class TimelineRemoveCommand(val parent: TimelineCommand) : CliktCommand(
    name = "remove", help = """
    Delete a timeline.

    The timeline is only deleted if it has no snapshots.
"""
) {
    val force: Boolean by option(
        "--force",
        help = "Delete the timeline even if it has snapshots. All snapshots will be deleted."
    )
        .flag()

    val timelineName: String by argument("TIMELINE", help = "The name of the timeline.")

    override fun run() {
        val timeline = getTimeline(parent.repoPath, timelineName)

        if (!force && timeline.listSnapshots().any()) {
            throw UsageError("Will not remove a timeline with snapshots. Use --force to override.")
        }
        timeline.repository.removeTimeline(timelineName)
    }
}

class TimelineModifyCommand(val parent: TimelineCommand) : CliktCommand(
    name = "modify", help = """
    Modify an existing timeline.
"""
) {
    val newName: String? by option("--name", help = "The new name of the timeline.")

    val timelineName: String by argument("NAME", help = "The name of the timeline.")

    override fun run() {
        val timeline = getTimeline(parent.repoPath, timelineName)
        newName?.let { timeline.name = it }
    }
}

class TimelineListCommand(val parent: TimelineCommand) : CliktCommand(
    name = "list", help = """
    List the timelines in a repository.
"""
) {
    override fun run() {
        val repository = getRepository(parent.repoPath)
        echo(repository.listTimelines().joinToString(separator = "\n\n") { it.info })
    }
}

class TimelineInfoCommand(val parent: TimelineCommand) : CliktCommand(
    name = "info", help = """
    Show information about a timeline.
"""
) {
    val timelineName: String by argument("NAME", help = "The name of the timeline.")

    override fun run() {
        val timeline = getTimeline(parent.repoPath, timelineName)
        echo(timeline.info)
    }
}
