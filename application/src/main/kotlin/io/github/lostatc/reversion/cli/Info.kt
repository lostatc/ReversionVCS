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

import io.github.lostatc.reversion.DEFAULT_REPO
import io.github.lostatc.reversion.api.*
import io.github.lostatc.reversion.storage.WorkDirectory
import org.apache.commons.io.FileUtils
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The string used to indent nested information.
 */
private const val INDENT: String = "  "

/**
 * Format an [Instant] as a string using the given [style].
 */
private fun Instant.format(style: FormatStyle = FormatStyle.MEDIUM): String =
    DateTimeFormatter.ofLocalizedDateTime(style).format(this)

/**
 * Human-readable information about the repository.
 */
val Repository.info: String
    get() = """
        Path: $path
        Properties:
        ${config.properties.joinToString(separator = "\n") { "${it.name} = ${config[it]}" }.prependIndent(INDENT)}
    """.trimIndent()

/**
 * Human-readable information about the timeline.
 */
val Timeline.info: String
    get() = """
        Name: $name
        Created: ${timeCreated.format()}
    """.trimIndent()

/**
 * Human-readable information about the snapshot.
 */
val Snapshot.info: String
    get() = """
        Revision: $revision
        Created: ${timeCreated.format()}
    """.trimIndent()

/**
 * Human-readable information about the tag.
 */
val Tag.info: String
    get() = """
        Name: $name
        Description: $description
        Pinned: ${if (pinned) "Yes" else "No"}
    """.trimIndent()

/**
 * Human-readable information about the version.
 */
val Version.info: String
    get() = """
        Path: $path
        Last Modified: ${lastModifiedTime.toInstant().format()}
        Size: ${FileUtils.byteCountToDisplaySize(size)}
        Permissions: ${permissions.toString()}
        Checksum: ${checksum.hex}
    """.trimIndent()

/**
 * Whether this repository is the default repository.
 */
private val Repository.isDefault: Boolean
    get() = path == DEFAULT_REPO

/**
 * Human-readable information about the working directory.
 */
val WorkDirectory.info: String
    get() = """
        Repository: ${timeline.repository.path}  ${if (timeline.repository.isDefault) "(Default)" else ""}
        Timeline: ${timeline.name}
    """.trimIndent()