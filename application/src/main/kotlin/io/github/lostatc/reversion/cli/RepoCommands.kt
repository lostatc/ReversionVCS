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
import io.github.lostatc.reversion.DEFAULT_PROVIDER
import io.github.lostatc.reversion.DEFAULT_REPO
import io.github.lostatc.reversion.api.StorageProvider
import java.nio.file.Path

class RepoCommand : CliktCommand(
    name = "repo", help = """
    Manage repositories.
"""
) {
    val repoPath: Path by option("--repo", help = "Use this repository instead of the default repository.")
        .path()
        .default(DEFAULT_REPO)

    init {
        subcommands(
            RepoCreateCommand(this),
            RepoInfoCommand(this),
            RepoExportCommand(this),
            RepoImportCommand(this)
        )
    }

    override fun run() {}
}

class RepoCreateCommand(val parent: RepoCommand) : CliktCommand(
    name = "create", help = """
    Create a new empty repository.

    By default, the repository is created using the default storage provider with the default configuration.
"""
) {
    // TODO: Not implemented.
    val configure by option("-c", "--configure", help = "Interactively configure the repository.")
        .flag()

    override fun run() {
        DEFAULT_PROVIDER.createRepository(parent.repoPath)
    }
}

class RepoInfoCommand(val parent: RepoCommand) : CliktCommand(
    name = "info", help = """
    Get information about a repository.
"""
) {
    override fun run() {
        val repository = getRepository(parent.repoPath)
        echo(repository.info)
    }
}

class RepoExportCommand(val parent: RepoCommand) : CliktCommand(
    name = "export", help = """
    Export the repository to a file.
"""
) {
    val destPath by argument("DESTINATION", help = "The file to export the repository to.")
        .path(fileOkay = false, folderOkay = false)

    override fun run() {
        val repository = getRepository(parent.repoPath)
        repository.export(destPath)
    }
}

class RepoImportCommand(val parent: RepoCommand) : CliktCommand(
    name = "import", help = """
    Import the repository from a file.
"""
) {
    val sourcePath by argument("SOURCE", help = "The file to import the repository from.")
        .path(exists = true)

    override fun run() {
        StorageProvider.importRepository(source = sourcePath, target = parent.repoPath)
    }
}
