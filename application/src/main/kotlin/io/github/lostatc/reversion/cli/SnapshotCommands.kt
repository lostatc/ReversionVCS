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
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.github.lostatc.reversion.DEFAULT_REPO
import io.github.lostatc.reversion.api.StorageProvider
import java.nio.file.Path

class Snapshot : CliktCommand(
    help = """
    Manage snapshots.
"""
) {
    val repo: Path by option(help = "Use this repository instead of the default repository.")
        .path()
        .default(DEFAULT_REPO)

    init {
        subcommands(
            SnapshotRemove(this),
            SnapshotList(this),
            SnapshotInfo(this)
        )
    }

    override fun run() {}
}

class SnapshotRemove(val parent: Snapshot) : CliktCommand(
    name = "remove", help = """
    Remove a snapshot from a timeline.

    This deletes any data from the repository that is not associated with another snapshot.
"""
) {
    val timeline: String by argument(help = "The timeline the snapshot is in.")

    val revision: Int by argument(help = "The revision number of the snapshot.")
        .int()

    override fun run() {
        val repository = StorageProvider.openRepository(parent.repo)
        val timeline = repository.getTimeline(timeline) ?: throw UsageError("No such timeline '$timeline'.")
        val removed = timeline.removeSnapshot(revision)
        if (!removed) throw UsageError("No snapshot with the revision '$revision'.")
    }
}

class SnapshotList(val parent: Snapshot) : CliktCommand(
    name = "list", help = """
    List the snapshots in a timeline.
"""
) {
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
        val repository = StorageProvider.openRepository(parent.repo)
        val timeline = repository.getTimeline(timeline) ?: throw UsageError("No such timeline '$timeline'.")
        echo(timeline.listSnapshots().joinToString(separator = "\n") { it.info })
    }
}

class SnapshotInfo(val parent: Snapshot) : CliktCommand(
    name = "info", help = """
    Show information about a snapshot.
"""
) {
    val timeline: String by argument(help = "The timeline the snapshot is in.")

    val revision: Int by argument(help = "The revision number of the snapshot.")
        .int()

    override fun run() {
        val repository = StorageProvider.openRepository(parent.repo)
        val timeline = repository.getTimeline(timeline) ?: throw UsageError("No such timeline '$timeline'.")
        val snapshot = timeline.getSnapshot(revision) ?: throw UsageError("No snapshot with the revision '$revision'.")
        echo(snapshot.info)
    }
}
