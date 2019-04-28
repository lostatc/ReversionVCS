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
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.github.lostatc.reversion.DEFAULT_REPO
import java.nio.file.Path

class TagCommand : CliktCommand(
    name = "tag", help = """
    Manage tags.
"""
) {
    val repoPath: Path by option("--repo", help = "Use this repository instead of the default repository.")
        .path()
        .default(DEFAULT_REPO)

    init {
        subcommands(
            TagCreateCommand(this),
            TagRemoveCommand(this),
            TagModifyCommand(this),
            TagListCommand(this),
            TagInfoCommand(this)
        )
    }

    override fun run() = Unit
}

class TagCreateCommand(val parent: TagCommand) : CliktCommand(
    name = "create", help = """
    Create a new tag.
"""
) {
    val description: String by option("-d", "--description", help = "The description for the tag.")
        .default("")

    val pinned: Boolean by option("--pin", help = "Never automatically delete the snapshot with this tag.")
        .flag("--no-pin", default = true)

    val timelineName: String by argument("TIMELINE", help = "The timeline to create the tag in.")

    val revision: Int by argument("REVISION", help = "The revision number of the snapshot to tag.")
        .int()

    val tagName: String by argument("NAME", help = "The name of the tag.")

    override fun run() {
        val snapshot = getSnapshot(parent.repoPath, timelineName, revision)
        snapshot.addTag(name = tagName, description = description, pinned = pinned)
    }
}

class TagRemoveCommand(val parent: TagCommand) : CliktCommand(
    name = "remove", help = """
    Delete a tag.

    This does not affect the snapshot it is applied to.
"""
) {
    val timelineName: String by argument("TIMELINE", help = "The timeline the tag is in.")

    val revision: Int by argument("REVISION", help = "The revision number of the snapshot the tag is in.")
        .int()

    val tagName: String by argument("NAME", help = "The name of the tag.")

    override fun run() {
        val snapshot = getSnapshot(parent.repoPath, timelineName, revision)
        snapshot.removeTag(tagName)
    }
}

class TagModifyCommand(val parent: TagCommand) : CliktCommand(
    name = "modify", help = """
    Modify an existing tag.
"""
) {
    val newName: String? by option("-n", "--name", help = "The new name of the tag.")

    val description: String? by option("-d", "--description", help = "The new description for the tag.")

    val pinned: Boolean? by option("--pin", help = "Never automatically delete the snapshot with this tag.")
        .flag(default = false)

    val notPinned: Boolean? by option(
        "--no-pin",
        help = "Allow the snapshot with this tag to be automatically deleted."
    )
        .flag(default = true)

    val timelineName: String by argument("TIMELINE", help = "The timeline the tag is in.")

    val revision: Int by argument("REVISION", help = "The revision number of the snapshot the tag is in.")
        .int()

    val tagName: String by argument("NAME", help = "The name of the tag.")

    override fun run() {
        val tag = getTag(parent.repoPath, timelineName, revision, tagName)

        newName?.let { tag.name = it }
        description?.let { tag.description = it }
        notPinned?.let { tag.pinned = it }
        pinned?.let { tag.pinned = it }
    }
}

class TagListCommand(val parent: TagCommand) : CliktCommand(
    name = "list", help = """
    List the tags in a timeline.
"""
) {
    val timelineName: String by argument("TIMELINE", help = "The timeline to list tags from.")

    val revision: Int by argument("REVISION", help = "The revision number of the snapshot to list tags from.")
        .int()

    override fun run() {
        val snapshot = getSnapshot(parent.repoPath, timelineName, revision)
        val tags = snapshot.tags.values

        echo(tags.joinToString(separator = "\n\n") { it.info })
    }
}

class TagInfoCommand(val parent: TagCommand) : CliktCommand(
    name = "info", help = """
    Show information about a tag.
"""
) {
    val timelineName: String by argument("TIMELINE", help = "The timeline the tag is in.")

    val revision: Int by argument("REVISION", help = "The revision number of the snapshot the tag is in.")
        .int()

    val tagName: String by argument("NAME", help = "The name of the tag.")

    override fun run() {
        val tag = getTag(parent.repoPath, timelineName, revision, tagName)
        echo(tag.info)
    }
}
