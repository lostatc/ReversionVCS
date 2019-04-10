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

class TagCommand : CliktCommand(
    name = "tag", help = """
    Manage tags.
"""
) {
    val repo: Path by option(help = "Use this repository instead of the default repository.")
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
    val timeline: String by argument(help = "The timeline to create the tag in.")

    val revision: Int by argument(help = "The revision number of the snapshot to tag.")
        .int()

    val name: String by argument(help = "The name of the tag.")

    val description: String by option("-d", "--description", help = "The description for the tag.")
        .default("")

    val pinned: Boolean by option("--pin", help = "Never automatically delete the snapshot with this tag.")
        .flag("--no-pin", default = true)

    override fun run() {
        val repository = StorageProvider.openRepository(parent.repo)
        val timeline = repository.getTimeline(timeline) ?: throw UsageError("No such timeline '$timeline'.")
        val snapshot = timeline.getSnapshot(revision) ?: throw UsageError("No snapshot with the revision '$revision'.")

        snapshot.addTag(name = name, description = description, pinned = pinned)
    }
}

class TagRemoveCommand(val parent: TagCommand) : CliktCommand(
    name = "remove", help = """
    Delete a tag.

    This does not affect the snapshot it is applied to.
"""
) {
    val timeline: String by argument(help = "The timeline the tag is in.")

    val name: String by argument(help = "The name of the tag.")

    override fun run() {
        val repository = StorageProvider.openRepository(parent.repo)
        val timeline = repository.getTimeline(timeline) ?: throw UsageError("No such timeline '$timeline'.")

        if (!timeline.removeTag(name)) throw UsageError("No tag with the name '$name'.")
    }
}

class TagModifyCommand(val parent: TagCommand) : CliktCommand(
    name = "modify", help = """
    Modify an existing tag.
"""
) {
    val timeline: String by argument(help = "The timeline the tag is in.")

    val name: String by argument(help = "The name of the tag.")

    val newName: String? by option("-n", "--name", help = "The new name of the tag.")

    val description: String? by option("-d", "--description", help = "The new description for the tag.")

    val pinned: Boolean? by option("--pin", help = "Never automatically delete the snapshot with this tag.")
        .flag(default = false)

    val notPinned: Boolean? by option(
        "--no-pin",
        help = "Allow the snapshot with this tag to be automatically deleted."
    )
        .flag(default = true)

    override fun run() {
        val repository = StorageProvider.openRepository(parent.repo)
        val timeline = repository.getTimeline(timeline) ?: throw UsageError("No such timeline '$timeline'.")
        val tag = timeline.getTag(name) ?: throw UsageError("No tag with the name '$name'.")

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
    val timeline: String by argument(help = "The timeline to list tags from.")

    val revisions: List<Int> by option(
        "-r", "--revision",
        help = "Only list tags on the snapshot with this revision number. This can be specified multiple times."
    )
        .int()
        .multiple()

    override fun run() {
        val repository = StorageProvider.openRepository(parent.repo)
        val timeline = repository.getTimeline(timeline) ?: throw UsageError("No such timeline '$timeline'.")

        val tags = if (revisions.isEmpty()) {
            timeline.listTags()
        } else {
            timeline.listTags().filter { it.snapshot.revision in revisions }
        }

        echo(tags.joinToString(separator = "\n\n") { it.info })
    }
}

class TagInfoCommand(val parent: TagCommand) : CliktCommand(
    name = "info", help = """
    Show information about a tag.
"""
) {
    val timeline: String by argument(help = "The timeline the tag is in.")

    val name: String by argument(help = "The name of the tag.")

    override fun run() {
        val repository = StorageProvider.openRepository(parent.repo)
        val timeline = repository.getTimeline(timeline) ?: throw UsageError("No such timeline '$timeline'.")
        val tag = timeline.getTag(name) ?: throw UsageError("No tag with the name '$name'.")

        echo(tag.info)
    }
}
