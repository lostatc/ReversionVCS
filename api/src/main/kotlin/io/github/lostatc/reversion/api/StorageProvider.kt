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
import java.nio.file.Path
import java.util.ServiceLoader

/**
 * An exception which is thrown when there is a problem with a [Repository].
 *
 * @param [action] An action which can be taken to resolve the problem, or `null` if there is none.
 */
open class RepositoryException(
    override val message: String,
    override val cause: Throwable? = null,
    val action: RecoveryAction? = null
) : IOException(message, cause)

/**
 * An exception which is thrown when a repository cannot be opened because it's incompatible with the storage provider.
 */
class IncompatibleRepositoryException(
    message: String,
    cause: Throwable? = null,
    action: RecoveryAction? = null
) : RepositoryException(message, cause, action)

/**
 * An exception which is thrown when a repository cannot be opened because it is corrupt or otherwise unreadable.
 */
class InvalidRepositoryException(
    message: String,
    cause: Throwable? = null,
    action: RecoveryAction? = null
) : RepositoryException(message, cause, action)

/**
 * An action which can be taken to recover a repository.
 */
interface RecoveryAction {
    /**
     * A message to show the user prompting them for whether they want to attempt to recover the repository.
     */
    val message: String

    /**
     * Attempt to recover the repository.
     */
    fun recover(): Result

    /**
     * The result of a recovery operation.
     *
     * @param [success] Whether the recovery was successful.
     * @param [message] The message to show the user after the attempt is complete.
     */
    data class Result(val success: Boolean, val message: String)
}

/**
 * An interface for service providers that provide mechanisms for storing file version history.
 */
interface StorageProvider {
    /**
     * The human-readable name of this storage provider.
     */
    val name: String

    /**
     * The human-readable description of this storage provider.
     */
    val description: String

    /**
     * Returns the default configuration used by this storage provider.
     */
    fun getConfig(): Config

    /**
     * Opens the repository at [path] and returns it.
     *
     * If this method throws a [RepositoryException], the exception can include a [RecoveryAction], which may be used to
     * recover a repository which can't be opened. The user will be prompted before a recovery attempt is made.
     *
     * @throws [IncompatibleRepositoryException] There is no compatible repository at [path].
     * @throws [InvalidRepositoryException] The repository is compatible but cannot be read.
     * @throws [IOException] An I/O error occurred.
     */
    fun openRepository(path: Path): Repository

    /**
     * Creates a repository at [path] with the given [config] and returns it.
     *
     * @throws [FileAlreadyExistsException] There is already a file at [path].
     * @throws [IOException] An I/O error occurred.
     */
    fun createRepository(path: Path, config: Config = getConfig()): Repository

    /**
     * Returns whether there is a repository at [path] which is compatible with this storage provider.
     *
     * Just because a repository is compatible with this storage provider does not mean that it can be opened.
     */
    fun checkRepository(path: Path): Boolean

    companion object {
        /**
         * Returns a sequence of all available storage providers.
         */
        fun listProviders(): Sequence<StorageProvider> =
            ServiceLoader.load(StorageProvider::class.java).asSequence()

        /**
         * Returns the first [StorageProvider] which is compatible with the repository at [path].
         *
         * @throws [IncompatibleRepositoryException] There is not installed provider compatible with the repository at
         * [path].
         *
         * @see [StorageProvider.checkRepository]
         */
        fun findProvider(path: Path): StorageProvider = listProviders().find { it.checkRepository(path) }
            ?: throw IncompatibleRepositoryException("There is no installed provider compatible with the repository at '$path'.")
    }
}
