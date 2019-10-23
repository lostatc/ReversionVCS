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

package io.github.lostatc.reversion.api.storage

import io.github.lostatc.reversion.api.Configurator
import io.github.lostatc.reversion.api.Form
import java.io.IOException
import java.nio.file.Path
import java.util.ServiceLoader

/**
 * An exception which is thrown when there is a problem with a [Repository].
 */
open class RepositoryException(
    override val message: String,
    override val cause: Throwable? = null
) : IOException(message, cause)

/**
 * An exception which is thrown when a repository cannot be opened because it's incompatible with the storage provider.
 */
class IncompatibleRepositoryException(
    message: String,
    cause: Throwable? = null
) : RepositoryException(message, cause)

/**
 * An exception which is thrown when a repository cannot be opened because it is corrupt or otherwise unreadable.
 */
class InvalidRepositoryException(
    message: String,
    cause: Throwable? = null
) : RepositoryException(message, cause)


/**
 * The result of an attempt to open something.
 */
sealed class OpenAttempt<T> {
    /**
     * The [result] which was successfully opened.
     */
    class Success<T>(val result: T) : OpenAttempt<T>()

    /**
     * The resource could not be opened and [actions] must be taken to repair it.
     */
    class Failure<T>(val actions: Sequence<RepairAction>) : OpenAttempt<T>()

    /**
     * Convert the type of this [OpenAttempt] with [block] and propagate errors.
     */
    fun <R> wrap(block: (T) -> R): OpenAttempt<R> = when (this) {
        is Success<T> -> Success(block(result))
        is Failure<T> -> Failure(actions)
    }

    /**
     * Return the [Success.result] or the result of [block] if this is a [Failure].
     */
    inline fun onFail(block: (Failure<T>) -> T): T = when (this) {
        is Success -> result
        is Failure -> block(this)
    }
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
     * Returns a [Form] for configuring the repository.
     */
    fun configure(): Form<Configurator>

    /**
     * Opens the repository at [path] and returns it.
     *
     * @throws [IncompatibleRepositoryException] There is no compatible repository at [path].
     * @throws [InvalidRepositoryException] The repository is compatible but cannot be read.
     * @throws [IOException] An I/O error occurred.
     */
    fun openRepository(path: Path): OpenAttempt<Repository>

    /**
     * Creates a repository at [path], configures it with [configurator], and returns it.
     *
     * @throws [FileAlreadyExistsException] There is already a file at [path].
     * @throws [IOException] An I/O error occurred.
     */
    fun createRepository(path: Path, configurator: Configurator = Configurator.Default): Repository

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
