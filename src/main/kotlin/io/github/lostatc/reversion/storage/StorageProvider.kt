/*
 * Copyright © 2019 Wren Powell
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
 * An interface for service providers that provide mechanisms for storing file version history.
 */
interface StorageProvider {
    /**
     * Returns a [Repository] for the repository located at the given [path].
     */
    fun getRepository(path: Path): Repository

    /**
     * Creates a repository at the given [path] and returns it.
     */
    fun createRepository(path: Path): Repository

    /**
     * Returns whether there is a repository compatible with this storage provider at the given [path].
     */
    fun isRepository(path: Path): Boolean

    companion object {
        fun listProviders(): Sequence<StorageProvider> =
            ServiceLoader.load(StorageProvider::class.java).asSequence()

        /**
         * Returns the first [StorageProvider] which is compatible with the repository at the given [path].
         *
         * @return The first compatible storage provider or `null` if none was found.
         */
        fun findProvider(path: Path): StorageProvider? = listProviders().find { it.isRepository(path) }
    }
}

object DatabaseStorageProvider : StorageProvider {
    override fun getRepository(path: Path): Repository = DatabaseRepository(path)

    override fun createRepository(path: Path): Repository {
        TODO("not implemented")
    }

    override fun isRepository(path: Path): Boolean = try {
        getRepository(path)
        true
    } catch (e: IOException) {
        false
    } catch (e: UnsupportedFormatException) {
        false
    }
}