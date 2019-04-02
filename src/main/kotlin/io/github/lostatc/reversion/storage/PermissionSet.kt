/*
 * Copyright © 2019 Garrett Powell
 *
 * This file is part of reversion.
 *
 * reversion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * reversion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with reversion.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.lostatc.reversion.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/**
 * A set of POSIX file permissions.
 */
class PermissionSet(permissions: Set<PosixFilePermission>) : Set<PosixFilePermission> by permissions {
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
