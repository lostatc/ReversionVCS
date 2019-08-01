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

import ch.qos.logback.core.PropertyDefinerBase
import ch.qos.logback.core.spi.PropertyDefiner
import io.github.lostatc.reversion.api.StorageProvider
import io.github.lostatc.reversion.storage.DatabaseStorageProvider
import java.nio.file.Path
import java.nio.file.Paths
import java.util.MissingResourceException

/**
 * The path of the user's home directory.
 */
val HOME_DIRECTORY: Path by lazy { Paths.get(System.getProperty("user.home")) }

/**
 * Resolves [firstSegment] and each of the given [segments] against this path.
 *
 * @see [Path.resolve]
 */
fun Path.resolve(firstSegment: String, vararg segments: String): Path =
    segments.fold(resolve(firstSegment)) { path, segment -> path.resolve(segment) }

/**
 * Returns the [Path] stored in the given environment [variable], or `null` if it is unset.
 */
private fun pathFromEnv(variable: String): Path? =
    System.getenv(variable)?.let { Paths.get(it) }

/**
 * An exception which signals that a resource is missing.
 *
 * @param [name] The name of the resource.
 */
data class MissingResourceException(override val message: String, val name: String) : Exception(message)

/**
 * Returns the [Path] of the resource with the given [name] or `null` if it doesn't exist.
 */
fun findResourcePath(name: String): Path? =
    OperatingSystem::class.java.getResource(name)?.let { Paths.get(it.toURI()) }

/**
 * Returns the [Path] of the resource with the given [name].
 *
 * @throws [MissingResourceException] A resource with the given [name] doesn't exist.
 */
fun getResourcePath(name: String): Path =
    findResourcePath(name) ?: throw MissingResourceException("The resource '$name' doesn't exist.", name)

/**
 * A supported operating system.
 *
 * @param [dataDirectory] The directory where application data is stored.
 * @param [configDirectory] The directory where application configuration is stored.
 */
enum class OperatingSystem(val dataDirectory: Path, val configDirectory: Path = dataDirectory) {
    WINDOWS(dataDirectory = pathFromEnv("APPDATA") ?: HOME_DIRECTORY),

    MAC(dataDirectory = HOME_DIRECTORY.resolve("Library", "Application Support")),

    LINUX(
        dataDirectory = pathFromEnv("XDG_DATA_HOME") ?: HOME_DIRECTORY.resolve(".local", "share"),
        configDirectory = pathFromEnv("XDG_CONFIG_HOME") ?: HOME_DIRECTORY.resolve(".config")
    );

    /**
     * Returns whether this [OperatingSystem] is the current operating system.
     */
    val isCurrent: Boolean
        get() = current() == this

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
val DATA_DIR: Path by lazy { OperatingSystem.current().getDataDirectory("Reversion") }

/**
 * The default storage provider.
 */
val DEFAULT_PROVIDER: StorageProvider by lazy { DatabaseStorageProvider() }

/**
 * A [PropertyDefiner] for getting the path of the directory where log files are stored.
 */
class LogDirectoryPropertyDefiner : PropertyDefinerBase() {
    override fun getPropertyValue(): String = DATA_DIR.toString()
}
