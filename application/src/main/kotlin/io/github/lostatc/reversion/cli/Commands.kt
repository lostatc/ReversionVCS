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
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.github.lostatc.reversion.DEFAULT_PROVIDER
import io.github.lostatc.reversion.api.ValueConvertException
import io.github.lostatc.reversion.storage.WorkDirectory
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The main command of the CLI.
 */
class ReversionCommand : CliktCommand(name = "reversion") {
    val workPath: Path by option(
        "-w", "--work-dir", help = "Use this directory instead of the current working directory."
    )
        .path()
        .default(Paths.get("").toAbsolutePath())

    init {
        subcommands(
            SnapshotCommand(this),
            TagCommand(this),
            VersionCommand(this),
            PolicyCommand(this),
            InitCommand(this),
            StatusCommand(this),
            CommitCommand(this),
            CheckoutCommand(this),
            UpdateCommand(this),
            CleanCommand(this),
            VerifyCommand(this),
            RepairCommand(this)
        )
    }


    override fun run() = Unit
}

class InitCommand(val parent: ReversionCommand) : CliktCommand(
    name = "init", help = """
    Begin tracking changes in an existing directory.
"""
) {
    val configure: Boolean by option("-c", "--configure", help = "Interactively configure the repository.")
        .flag()

    override fun run() {
        val provider = DEFAULT_PROVIDER
        val config = provider.getConfig()

        if (configure) {
            for (property in config.properties) {
                while (true) {
                    echo(property.description)
                    val value = TermUi.prompt(text = property.name, default = property.default)
                        ?: throw CliktError("Cannot prompt interactively because stdin is not a terminal.")

                    try {
                        config[property] = value
                        break
                    } catch (e: ValueConvertException) {
                        echo("\n${e.message}\n")
                    }
                }

                println()
            }
        }

        WorkDirectory.init(parent.workPath, DEFAULT_PROVIDER, config)
    }
}

class StatusCommand(val parent: ReversionCommand) : CliktCommand(
    name = "status", help = """
    Show changes that have occurred since the most recent snapshot.
"""
) {
    override fun run() {
        val workDirectory = WorkDirectory.open(parent.workPath)
        val modifiedFiles = workDirectory.getStatus().modifiedFiles

        echo("\nFiles with uncommitted changes:")
        echo(modifiedFiles.joinToString(separator = "\n").prependIndent("    "))
    }
}

class CommitCommand(val parent: ReversionCommand) : CliktCommand(
    name = "commit", help = """
    Commit changes to the directory, creating a new snapshot.

    By default, files are only committed if they have uncommitted changes.
"""
) {
    val force: Boolean by option("--force", help = "Commit files even if they don't have uncommitted changes.")
        .flag()

    val paths: List<Path> by argument("PATH", help = "The paths of files and directories to commit.")
        .path(exists = true)
        .multiple(required = true)

    override fun run() {
        val workDir = WorkDirectory.open(parent.workPath)
        workDir.commit(paths, force = force)
    }
}

class CheckoutCommand(val parent: ReversionCommand) : CliktCommand(
    name = "checkout", help = """
    Get versions of files from a timeline.

    This gets a version of a file from a snapshot in a timeline and copies it to a local path. By default, the latest
    revision is chosen.
"""
) {

    val revision: Int? by option("-r", "--revision", help = "The revision number of the snapshot to get the file from.")
        .int()

    val sourcePath: Path by argument("SOURCE", help = "The relative path of the file to retrieve from the timeline.")
        .path()

    val destPath: Path by argument("DESTINATION", help = "The path to copy the file to.")
        .path()

    override fun run() {
        val workDirectory = WorkDirectory.open(parent.workPath)

        val snapshot = revision?.let { getSnapshot(workDirectory, it) }
            ?: workDirectory.timeline.latestSnapshot
            ?: throw NoSuchElementException("This working directory has no snapshots.")

        val version = getVersion(workDirectory, snapshot.revision, sourcePath)

        version.checkout(destPath)
    }
}

class UpdateCommand(val parent: ReversionCommand) : CliktCommand(
    name = "update", help = """
    Update the working directory with data from the timeline.

    This updates the given files in the working directory to the state they were in in the snapshot with the given
    revision. By default, this is the most recent revision. By default, uncommitted changes will not be overwritten. It
    is possible to update files that don't currently exist in the working directory.
"""
) {
    val revision: Int? by option("-r", "--revision", help = "The revision number of the snapshot to update files to.")
        .int()

    val overwrite: Boolean by option("--overwrite", help = "Overwrite uncommitted changes.")
        .flag()

    val paths: List<Path> by argument("PATH", help = "The paths of files to commit.")
        .path()
        .multiple(required = true)

    override fun run() {
        val workDirectory = WorkDirectory.open(parent.workPath)
        workDirectory.update(paths = paths, revision = revision, overwrite = overwrite)
    }
}

class CleanCommand(val parent: ReversionCommand) : CliktCommand(
    name = "clean", help = """
    Clean up old versions of files in a timeline.

    Old versions are deleted according to the rules set for the timeline.
"""
) {
    override fun run() {
        val workDirectory = WorkDirectory.open(parent.workPath)
        workDirectory.timeline.clean()
    }
}

class VerifyCommand(val parent: ReversionCommand) : CliktCommand(
    name = "verify", help = """
    Verify the integrity of a repository.
"""
) {
    override fun run() {
        val workDirectory = WorkDirectory.open(parent.workPath)
        val report = workDirectory.repository.verify()

        if (report.isValid) {
            echo("No corruption detected.")
        } else {
            echo("The following file versions are corrupt:")
            echo(report.corruptVersions.joinToString(separator = "\n") { it.info }.prependIndent("    "))
        }
    }
}

class RepairCommand(val parent: ReversionCommand) : CliktCommand(
    name = "repair", help = """
    Repair corrupt data in the repository.

    Corrupt file versions are repaired if possible using existing data in the working directory and deleted otherwise.
"""
) {
    override fun run() {
        val workDirectory = WorkDirectory.open(parent.workPath)
        workDirectory.repository.repair(workDirectory.path)
    }
}