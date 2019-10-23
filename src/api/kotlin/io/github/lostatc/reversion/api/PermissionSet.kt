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

package io.github.lostatc.reversion.api

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/**
 * A set of POSIX file permissions.
 */
data class PermissionSet(private val permissions: Set<PosixFilePermission>) : Set<PosixFilePermission> by permissions {
    /**
     * Returns the string representation of this set of permissions.
     *
     * It is guaranteed that the returned string can be parsed by [fromString].
     */
    override fun toString(): String = PosixFilePermissions.toString(this)

    companion object {
        /**
         * Creates a new [PermissionSet] from the given [permissions] string.
         *
         * This accepts permissions in the form 'rwxrwxrwx'.
         */
        fun fromString(permissions: String): PermissionSet =
            PermissionSet(PosixFilePermissions.fromString(permissions))

        /**
         * Creates a new [PermissionSet] from the given [path].
         *
         * @return The [PermissionSet] object or `null` if the file system doesn't support POSIX file permissions.
         */
        fun fromPath(path: Path): PermissionSet? = try {
            PermissionSet(Files.getPosixFilePermissions(path))
        } catch (e: UnsupportedOperationException) {
            null
        }
    }
}

/**
 * Attempt to set these permissions on the file with the given [path].
 *
 * @return `true` if the permissions were set, `false` if this value is `null` or POSIX file permissions are not
 * supported on the given [path].
 */
fun PermissionSet?.trySetPermissions(path: Path): Boolean {
    if (this == null) return false
    return try {
        Files.setPosixFilePermissions(path, this)
        true
    } catch (e: UnsupportedOperationException) {
        false
    }
}
