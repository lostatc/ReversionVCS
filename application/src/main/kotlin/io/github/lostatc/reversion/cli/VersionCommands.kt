/*
 * Copyright © 2019 Garrett Powell
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
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.github.lostatc.reversion.DEFAULT_REPO
import io.github.lostatc.reversion.api.StorageProvider
import java.nio.file.Path

class VersionCommand : CliktCommand(
    name = "version", help = """
    Manage versions of files.
"""
) {

    val repo: Path by option(help = "Use this repository instead of the default repository.")
        .path()
        .default(DEFAULT_REPO)

    init {
        subcommands(
            VersionRemoveCommand(this),
            VersionListCommand(this),
            VersionInfoCommand(this)
        )
    }

    override fun run() = Unit
}

class VersionRemoveCommand(val parent: VersionCommand) : CliktCommand(
    name = "remove", help = """
    Remove a version of a file from the timeline.
"""
) {
    val timeline: String by argument(help = "The timeline the snapshot is in.")

    val revision: Int by argument(help = "The revision number of the snapshot.")
        .int()

    val path: Path by argument(help = "The relative path of the version.")
        .path()

    override fun run() {
        val repository = StorageProvider.openRepository(parent.repo)
        val timeline = repository.getTimeline(timeline) ?: throw UsageError("No such timeline '$timeline'.")
        val snapshot = timeline.getSnapshot(revision) ?: throw UsageError("No snapshot with the revision '$revision'.")

        if (!snapshot.removeVersion(path)) throw UsageError("No version of '$path' with revision '$revision'.")
    }
}

class VersionListCommand(val parent: VersionCommand) : CliktCommand(
    name = "list", help = """
    List versions of a file in a given snapshot.
"""
) {
    val timeline: String by argument(help = "The timeline of the snapshot.")

    val revision: Int by argument(help = "The revision number of the snapshot.")
        .int()

    val info: Boolean by option("-i", "--info", help = "Show detailed information about each version.")
        .flag()

    override fun run() {
        val repository = StorageProvider.openRepository(parent.repo)
        val timeline = repository.getTimeline(timeline) ?: throw UsageError("No such timeline '$timeline'.")
        val snapshot = timeline.getSnapshot(revision) ?: throw UsageError("No snapshot with the revision '$revision'.")
        val versions = snapshot.listVersions()

        if (info) {
            echo(versions.joinToString(separator = "\n\n") { it.info })
        } else {
            echo(versions.joinToString(separator = "\n") { it.path.toString() })
        }
    }
}

class VersionInfoCommand(val parent: VersionCommand) : CliktCommand(
    name = "info", help = """
    Show information about a version of a file.
"""
) {
    val timeline: String by argument(help = "The timeline the snapshot is in.")

    val revision: Int by argument(help = "The revision number of the snapshot.")
        .int()

    val path: Path by argument(help = "The relative path of the version.")
        .path()

    override fun run() {
        val repository = StorageProvider.openRepository(parent.repo)
        val timeline = repository.getTimeline(timeline) ?: throw UsageError("No such timeline '$timeline'.")
        val snapshot = timeline.getSnapshot(revision) ?: throw UsageError("No snapshot with the revision '$revision'.")
        val version = snapshot.getVersion(path) ?: throw UsageError("No version of '$path' with revision '$revision'.")

        echo(version.info)
    }
}
