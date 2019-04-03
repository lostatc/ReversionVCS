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

import org.zeroturnaround.zip.ZipUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * An interface for service providers that provide mechanisms for storing file version history.
 */
interface StorageProvider {
    /**
     * Gets the repository at the given path and returns it.
     *
     * If there is not a repository at [path], an empty one will be created.
     *
     * @throws [UnsupportedFormatException] The repository at [path] is not compatible with this storage provider.
     * @throws [IOException] There was an I/O error.
     */
    fun getRepository(path: Path): Repository

    /**
     * Imports a repository from a file and returns it.
     *
     * This is guaranteed to support importing the file created by [Repository.export].
     *
     * @param [source] The file to import the repository from.
     * @param [target] The path to create the repository at.
     *
     * @throws [IOException] An I/O error occurred.
     */
    fun importRepository(source: Path, target: Path): Repository

    /**
     * Returns whether there is a repository compatible with this storage provider at the given [path].
     */
    fun isCompatibleRepository(path: Path): Boolean

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
        fun findProvider(path: Path): StorageProvider? = listProviders().find { it.isCompatibleRepository(path) }
    }
}

/**
 * A storage provider which stores data in de-duplicated blobs and metadata in a relational database.
 */
object DatabaseStorageProvider : StorageProvider {
    override fun getRepository(path: Path): Repository = DatabaseRepository(path)

    override fun importRepository(source: Path, target: Path): Repository {
        ZipUtil.unpack(source.toFile(), target.toFile())
        return DatabaseRepository(target)
    }

    override fun isCompatibleRepository(path: Path): Boolean {
        if (Files.notExists(path)) return false
        return try {
            DatabaseRepository(path)
            true
        } catch (e: UnsupportedFormatException) {
            false
        }
    }
}