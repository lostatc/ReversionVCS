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
 * An exception which is thrown when a repository cannot be opened because it's incompatible with the storage provider.
 */
data class IncompatibleRepositoryException(
    override val message: String,
    override val cause: Throwable? = null
) : IOException(message, cause)

/**
 * An exception which is thrown when a repository cannot be opened because it is corrupt or otherwise unreadable.
 */
data class InvalidRepositoryException(
    override val message: String,
    override val cause: Throwable? = null
) : IOException(message, cause)

/**
 * Options for opening a [Repository].
 *
 * All values in this enum represent optional operations which may not be supported by a [StorageProvider]
 * implementation.
 */
enum class OpenOption {
    /**
     * Attempt to open the repository destructively.
     *
     * Without this option, a repository that cannot be opened without data loss will throw an exception. This option
     * can be used to open repositories that otherwise cannot be opened because doing so would result in data loss
     */
    DESTRUCTIVE
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
     * @param [path] The path of the repository.
     * @param [options] The options which determine how the repository is opened.
     *
     * @throws [IncompatibleRepositoryException] There is no compatible repository at [path].
     * @throws [InvalidRepositoryException] The repository is compatible but cannot be read.
     * @throws [IOException] An I/O error occurred.
     */
    fun openRepository(path: Path, options: Set<OpenOption> = emptySet()): Repository

    /**
     * Creates a repository at [path] and returns it.
     *
     * @param [path] The path of the repository.
     * @param [config] The configuration for the repository.
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
