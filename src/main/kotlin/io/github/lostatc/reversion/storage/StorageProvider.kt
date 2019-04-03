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

import java.io.IOException
import java.nio.file.Path
import java.util.*

/**
 * An exception which is thrown when the format of a repository isn't supported by the storage provider.
 *
 * @param [message] A message describing the exception
 */
class UnsupportedFormatException(message: String? = null) : IllegalArgumentException(message)

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
     * The default repository configuration for this storage provider.
     */
    val config: RepositoryConfig

    /**
     * Opens the repository at [path] and returns it.
     *
     * @param [path] The path of the repository.
     * @param [config] The configuration for the repository.
     *
     * @throws [UnsupportedFormatException] There is no compatible repository at [path].
     */
    fun openRepository(path: Path, config: RepositoryConfig = this.config): Repository

    /**
     * Creates a repository at [path] and returns it.
     *
     * @param [path] The path of the repository.
     * @param [config] The configuration for the repository.
     *
     * @throws [FileAlreadyExistsException] There is already a file at [path].
     * @throws [IOException] An I/O error occurred.
     */
    fun createRepository(path: Path, config: RepositoryConfig = this.config): Repository

    /**
     * Imports a repository from a file and returns it.
     *
     * This is guaranteed to support importing the file created by [Repository.export].
     *
     * @param [source] The file to import the repository from.
     * @param [target] The path to create the repository at.
     * @param [config] The configuration for the repository.
     *
     * @throws [IOException] An I/O error occurred.
     */
    fun importRepository(source: Path, target: Path, config: RepositoryConfig = this.config): Repository

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
         * Returns the first [StorageProvider] which is compatible with the repository at the given [path].
         *
         * @return The first compatible storage provider or `null` if none was found.
         */
        fun findProvider(path: Path): StorageProvider? = listProviders().find { it.checkRepository(path) }
    }
}

/**
 * A storage provider which stores data in de-duplicated blobs and metadata in a relational database.
 */
object DatabaseStorageProvider : StorageProvider {
    override val name: String = "De-duplicated repository"

    override val description: String = """
        A repository format that can de-duplicate data at the file or block level. Data cannot be easily recovered
        without this program.
    """.trimIndent()

    override val config: RepositoryConfig = RepositoryConfig(DatabaseRepository.attributes)

    override fun openRepository(path: Path, config: RepositoryConfig): DatabaseRepository =
        DatabaseRepository.open(path, config)

    override fun createRepository(path: Path, config: RepositoryConfig): Repository =
        DatabaseRepository.create(path, config)

    override fun importRepository(source: Path, target: Path, config: RepositoryConfig): DatabaseRepository =
        DatabaseRepository.import(source, target, config)

    override fun checkRepository(path: Path): Boolean =
        DatabaseRepository.check(path)
}