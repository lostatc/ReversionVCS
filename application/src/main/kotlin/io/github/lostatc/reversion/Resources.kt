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
import io.github.lostatc.reversion.api.resolve
import io.github.lostatc.reversion.storage.DatabaseStorageProvider
import java.net.URI
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The path of the user's home directory.
 */
val HOME_DIRECTORY: Path by lazy { Paths.get(System.getProperty("user.home")) }

/**
 * Returns the [Path] stored in the given environment [variable], or `null` if it is unset.
 */
private fun pathFromEnv(variable: String): Path? = System.getenv(variable)?.let { Paths.get(it) }

/**
 * Returns the [URI] of the resource with the given [name].
 */
fun getResourceUri(name: String): URI = OperatingSystem::class.java.getResource(name)?.toURI()
    ?: error("A resource named '$name' doesn't exist.")

/**
 * Returns the [Path] of the resource with the given [name].
 */
fun getResourcePath(name: String): Path = getResourceUri(name).let {
    try {
        Paths.get(it)
    } catch (e: FileSystemNotFoundException) {
        FileSystems.newFileSystem(it, emptyMap<String, Any>())
        Paths.get(it)
    }
}

/**
 * A supported operating system.
 *
 * @param [dataDirectory] The directory where application data is stored.
 * @param [configDirectory] The directory where application configuration is stored.
 * @param [cacheDirectory] The directory where cached application data is stored.
 */
enum class OperatingSystem(
    val dataDirectory: Path,
    val configDirectory: Path,
    val cacheDirectory: Path
) {
    WINDOWS(
        dataDirectory = pathFromEnv("APPDATA") ?: HOME_DIRECTORY.resolve("AppData", "Roaming"),
        configDirectory = pathFromEnv("APPDATA") ?: HOME_DIRECTORY.resolve("AppData", "Roaming"),
        cacheDirectory = pathFromEnv("LOCALAPPDATA") ?: HOME_DIRECTORY.resolve("AppData", "Local")
    ),

    MAC(
        dataDirectory = HOME_DIRECTORY.resolve("Library", "Application Support"),
        configDirectory = HOME_DIRECTORY.resolve("Library", "Application Support"),
        cacheDirectory = HOME_DIRECTORY.resolve("Library", "Caches")
    ),

    LINUX(
        dataDirectory = pathFromEnv("XDG_DATA_HOME") ?: HOME_DIRECTORY.resolve(".local", "share"),
        configDirectory = pathFromEnv("XDG_CONFIG_HOME") ?: HOME_DIRECTORY.resolve(".config"),
        cacheDirectory = pathFromEnv("XDG_CACHE_HOME") ?: HOME_DIRECTORY.resolve(".cache")
    );

    /**
     * Returns whether this [OperatingSystem] is the current operating system.
     */
    val isCurrent: Boolean
        get() = current == this

    /**
     * Get the data directory for the program with the given [name].
     */
    fun getDataDirectory(name: String): Path = dataDirectory.resolve(name)

    /**
     * Get the config directory for the program with the given [name].
     */
    fun getConfigDirectory(name: String): Path = configDirectory.resolve(name)

    /**
     * Get the cache directory for the program with the given [name].
     */
    fun getCacheDirectory(name: String): Path = cacheDirectory.resolve(name)

    companion object {
        /**
         * The current operating system.
         *
         * @throws [UnsupportedOperationException] The current operating system is not supported.
         */
        val current: OperatingSystem by lazy {
            val osName = System.getProperty("os.name").toLowerCase()
            when {
                osName.startsWith("windows") -> WINDOWS
                osName.startsWith("mac") -> MAC
                osName.startsWith("linux") -> LINUX
                else -> throw UnsupportedOperationException("This operating system is not supported.")
            }
        }
    }
}

/**
 * The path of the program's data directory.
 */
val DATA_DIR: Path by lazy { OperatingSystem.current.getDataDirectory("Reversion") }

/**
 * The default storage provider.
 */
val DEFAULT_PROVIDER: StorageProvider by lazy { DatabaseStorageProvider() }

/**
 * A [PropertyDefiner] for getting the path of the directory where log files are stored.
 */
class LogDirectoryPropertyDefiner : PropertyDefinerBase() {
    override fun getPropertyValue(): String = DATA_DIR.resolve("logs").toString()
}
