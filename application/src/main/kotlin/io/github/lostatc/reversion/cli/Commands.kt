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
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.github.lostatc.reversion.DEFAULT_REPO
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The main command of the CLI.
 */
class ReversionCommand : CliktCommand(name = "reversion") {
    init {
        subcommands(
            RepoCommand(),
            TimelineCommand(),
            SnapshotCommand(),
            TagCommand(),
            VersionCommand(),
            InitCommand(),
            StatusCommand(),
            CommitCommand(),
            CheckoutCommand(),
            UpdateCommand(),
            CleanCommand(),
            VerifyCommand()
        )
    }

    override fun run() {

    }
}

class InitCommand : CliktCommand(
    name = "init", help = """
    Begin tracking changes in an existing directory.

    This associates the directory with a timeline, making it a working directory. The contents of the directory and the
    timeline are not modified.
"""
) {
    val timeline: String by argument(help = "The timeline to associate the directory with.")

    val repo: Path by option(help = "Use this repository instead of the default repository.")
        .path()
        .default(DEFAULT_REPO)

    val workDir: Path by option(
        "-w", "--work-dir", help = "Use this directory instead of the current working directory."
    )
        .path()
        .default(Paths.get("").toAbsolutePath())


    override fun run() {
        // TODO: Not implemented.
    }
}

class StatusCommand : CliktCommand(
    name = "status", help = """
    Show changes that have occurred since the most recent snapshot.
"""
) {
    val workDir: Path by option(
        "-w", "--work-dir", help = "Use this directory instead of the current working directory."
    )
        .path()
        .default(Paths.get("").toAbsolutePath())

    override fun run() {
        // TODO: Not implemented.
    }
}

class CommitCommand : CliktCommand(
    name = "commit", help = """
    Commit changes to the directory, creating a new snapshot.

    If no paths are specified, the entire working directory is committed.
"""
) {
    val paths: List<Path> by argument(help = "The paths of files to commit.")
        .path(exists = true)
        .multiple()

    val workDir: Path by option(
        "-w", "--work-dir", help = "Use this directory instead of the current working directory."
    )
        .path()
        .default(Paths.get("").toAbsolutePath())

    override fun run() {
        // TODO: Not implemented.
    }
}

class CheckoutCommand : CliktCommand(
    name = "checkout", help = """
    Get versions of files from a timeline.

    This gets a version of a file from a snapshot in a timeline and copies it to a local path. By default, the latest
    revision is chosen.
"""
) {
    val name: String by argument(help = "The name of the timeline.")

    val repo: Path by option(help = "Use this repository instead of the default repository.")
        .path()
        .default(DEFAULT_REPO)

    val source: Path by argument(help = "The relative path of the file to retrieve from the timeline.")
        .path()

    val dest: Path by argument(help = "The path to copy the file to.")
        .path()

    val revision: Int? by option(help = "The revision number of the snapshot to get the file from.")
        .int()

    override fun run() {
        // TODO: Not implemented.
    }
}

class UpdateCommand : CliktCommand(
    name = "update", help = """
    Update the working directory with data from the timeline.

    This can be used to update files to a past or future revision. By default, the latest revision is chosen.
    Uncommitted changes will not be overwritten. If no paths are specified, the entire working directory is updated.
    This does not commit anything.
"""
) {
    val paths: List<Path> by argument(help = "The paths of files to commit.")
        .path(exists = true)
        .multiple()

    val workDir: Path by option(
        "-w", "--work-dir", help = "Use this directory instead of the current working directory."
    )
        .path()
        .default(Paths.get("").toAbsolutePath())

    val revision: Int? by option("-r", "--revision", help = "The revision number of the snapshot to update files to.")
        .int()

    val overwrite: Boolean by option(help = "Overwrite uncommitted changes.")
        .flag()

    override fun run() {
        // TODO: Not implemented.
    }
}

class CleanCommand : CliktCommand(
    name = "clean", help = """
    Clean up old snapshots in a timeline.

    Old snapshots are deleted according to the rules set for the timeline.
"""
) {
    val name: String by argument(help = "The name of the timeline.")

    val repo: Path by option(help = "Use this repository instead of the default repository.")
        .path()
        .default(DEFAULT_REPO)

    override fun run() {
        // TODO: Not implemented.
    }
}

class VerifyCommand : CliktCommand(
    name = "verify", help = """
    Verify the integrity of a repository.
"""
) {
    val repo: Path by option(help = "Use this repository instead of the default repository.")
        .path()
        .default(DEFAULT_REPO)

    override fun run() {
        // TODO: Not implemented.
    }
}
