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
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.github.lostatc.reversion.storage.WorkDirectory
import java.nio.file.Path

class SnapshotCommand(parent: ReversionCommand) : CliktCommand(
    name = "snapshot", help = """
    Manage snapshots.
"""
) {

    val workPath: Path = parent.workPath

    init {
        subcommands(
            SnapshotRemoveCommand(this),
            SnapshotListCommand(this),
            SnapshotInfoCommand(this)
        )
    }

    override fun run() = Unit
}

class SnapshotRemoveCommand(val parent: SnapshotCommand) : CliktCommand(
    name = "remove", help = """
    Remove a snapshot from a timeline.

    This deletes any data from the repository that is not associated with another snapshot.
"""
) {
    val revision: Int by argument("REVISION", help = "The revision number of the snapshot.")
        .int()

    override fun run() {
        val workDirectory = WorkDirectory.open(parent.workPath)
        workDirectory.timeline.removeSnapshot(revision)
    }
}

class SnapshotListCommand(val parent: SnapshotCommand) : CliktCommand(
    name = "list", help = """
    List the snapshots in a timeline.
"""
) {
    val paths: List<Path> by option(
        "-p", "--path",
        help = "Show only snapshots containing a version with this relative path. Specified multiple times, show snapshots containing any."
    )
        .path()
        .multiple()

    override fun run() {
        val workDirectory = WorkDirectory.open(parent.workPath)

        val snapshots = workDirectory.timeline.snapshots.values.filter { snapshot ->
            if (paths.isEmpty()) true else paths.any { it in snapshot.versions }
        }

        echo(snapshots.joinToString(separator = "\n\n") { it.info })
    }
}

class SnapshotInfoCommand(val parent: SnapshotCommand) : CliktCommand(
    name = "info", help = """
    Show information about a snapshot.
"""
) {
    val revision: Int by argument("REVISION", help = "The revision number of the snapshot.")
        .int()

    override fun run() {
        val workDirectory = WorkDirectory.open(parent.workPath)
        val snapshot = getSnapshot(workDirectory, revision)
        echo(snapshot.info)
    }
}
