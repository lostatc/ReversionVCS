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

package io.github.lostatc.reversion

import io.github.lostatc.reversion.api.StorageProvider
import io.github.lostatc.reversion.storage.DatabaseStorageProvider
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The environment variable which stores the path of the default repository.
 */
private const val DEFAULT_REPO_ENV: String = "REVERSION_DEFAULT_REPO"

/**
 * The path of the user's home directory.
 */
private val HOME_DIRECTORY: Path = Paths.get(System.getProperty("user.home"))

/**
 * Resolves this path against a list of path [segments].
 */
private fun Path.resolve(vararg segments: String): Path =
    segments.fold(this) { path, segment -> path.resolve(segment) }

/**
 * Returns the [Path] stored in the given environment [variable], or `null` if it is unset.
 */
private fun pathFromEnv(variable: String): Path? =
    System.getenv(variable)?.let { Paths.get(it) }

/**
 * A supported operating system.
 *
 * @param [dataDirectory] The directory where application data is stored.
 * @param [configDirectory] The directory where application configuration is stored.
 */
private enum class OperatingSystem(val dataDirectory: Path, val configDirectory: Path = dataDirectory) {
    WINDOWS(dataDirectory = pathFromEnv("APPDATA") ?: HOME_DIRECTORY),

    MAC(dataDirectory = HOME_DIRECTORY.resolve("Library", "Application Support")),

    LINUX(
        dataDirectory = pathFromEnv("XDG_DATA_HOME") ?: HOME_DIRECTORY.resolve(".local", "share"),
        configDirectory = pathFromEnv("XDG_CONFIG_HOME") ?: HOME_DIRECTORY.resolve(".config")
    );

    /**
     * Get the data directory for the program with the given [name].
     */
    fun getDataDirectory(name: String): Path = dataDirectory.resolve(name)

    /**
     * Get the config directory for the program with the given [name].
     */
    fun getConfigDirectory(name: String): Path = configDirectory.resolve(name)

    companion object {
        /**
         * Get the current operating system.
         *
         * @throws [UnsupportedOperationException] The current operating system is not supported.
         */
        fun current(): OperatingSystem {
            val osName = System.getProperty("os.name").toLowerCase()
            if (osName.startsWith("windows")) return WINDOWS
            if (osName.startsWith("mac")) return MAC
            if (osName.startsWith("linux")) return LINUX
            throw UnsupportedOperationException("This operating system is not supported.")
        }
    }
}

/**
 * The path of the program's data directory.
 */
val DATA_DIR: Path = OperatingSystem.current().getDataDirectory("Reversion")

/**
 * The path of the default repository.
 */
val DEFAULT_REPO: Path = pathFromEnv(DEFAULT_REPO_ENV) ?: DATA_DIR.resolve("repository")

/**
 * The default storage provider.
 */
val DEFAULT_PROVIDER: StorageProvider = DatabaseStorageProvider()
