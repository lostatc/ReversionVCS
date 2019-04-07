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

package io.github.lostatc.reversion.api

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission

/**
 * A version of a file stored in a repository.
 */
interface Version {
    /**
     * The relative path of the file relative to its working directory.
     */
    val path: Path

    /**
     * The time the file was last modified.
     */
    val lastModifiedTime: FileTime

    /**
     * The permissions of the file or `null` if POSIX permissions are not applicable.
     */
    val permissions: Set<PosixFilePermission>?

    /**
     * The size of the file in bytes.
     */
    val size: Long

    /**
     * The hash of the file contents.
     */
    val checksum: Checksum

    /**
     * The snapshot that this file is a part of.
     */
    val snapshot: Snapshot

    /**
     * The timeline that this file is a part of.
     */
    val timeline: Timeline

    /**
     * The repository that this file is a part of.
     */
    val repository: Repository

    /**
     * Returns the contents of this file.
     */
    fun getData(): Blob

    /**
     * Returns whether the given [file] has changed since this version.
     *
     * @param [file] The path of the current version of the file represented by this version.
     */
    fun isChanged(file: Path): Boolean

    /**
     * Checks the integrity of the data in the repository for this file.
     *
     * @return `true` if the data is valid, `false` if it is corrupt.
     */
    fun isValid(): Boolean = getData().checksum == checksum

    /**
     * Writes the file represented by this object to the file system.
     *
     * @param [target] The path to write the file to.
     * @param [overwrite] If a file already exists at [target], overwrite it.
     * @param [verify] Check the data for corruption before writing it.
     *
     * @throws [IOException] An I/O error occurred.
     */
    fun checkout(target: Path, overwrite: Boolean = false, verify: Boolean = true) {
        if (!overwrite && Files.exists(target)) throw FileAlreadyExistsException(target.toFile())

        // Check the data for corruption.
        if (verify && !isValid()) {
            // TODO: Consider using a more specific exception.
            throw IOException("The data for the file '$path' is corrupt.")
        }

        // Write the file contents to a temporary file.
        val tempFile = Files.createTempFile("reversion-", "")
        getData().newInputStream().use { Files.copy(it, tempFile) }

        // Move the temporary file to the target to safely handle the case of an existing file.
        val copyOptions = if (overwrite) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray()
        Files.move(tempFile, target, *copyOptions)

        // Set metadata.
        Files.setLastModifiedTime(target, lastModifiedTime)
        if (permissions != null) Files.setPosixFilePermissions(target, permissions)
    }
}
