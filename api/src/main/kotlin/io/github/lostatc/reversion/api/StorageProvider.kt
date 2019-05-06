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
     *
     * @throws [UnsupportedFormatException] There is no compatible repository at [path].
     */
    fun openRepository(path: Path): Repository

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
     * Returns whether there is a repository compatible with this storage provider at [path].
     */
    fun checkRepository(path: Path): Boolean

    companion object {
        /**
         * Returns a sequence of all available storage providers.
         */
        fun listProviders(): Sequence<StorageProvider> =
            ServiceLoader.load(StorageProvider::class.java).asSequence()

        /**
         * Opens the repository at [path] with the first [StorageProvider] that supports it.
         *
         * @throws [UnsupportedFormatException] There is no installed provider that can open the repository.
         *
         * @see [StorageProvider.checkRepository]
         * @see [StorageProvider.openRepository]
         */
        fun openRepository(path: Path): Repository {
            return listProviders()
                .find { it.checkRepository(path) }
                ?.openRepository(path)
                ?: throw UnsupportedFormatException("No installed provider can open the repository at '$path'.")
        }

    }
}
