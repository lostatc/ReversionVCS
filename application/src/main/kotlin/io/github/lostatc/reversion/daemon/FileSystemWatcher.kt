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

package io.github.lostatc.reversion.daemon

import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService

/**
 * An event which has occurred in the file system.
 *
 * @param [path] The absolute path of the file.
 * @param [type] The type of event which occurred.
 */
data class FileSystemEvent(val path: Path, val type: WatchEvent.Kind<Path>)

/**
 * A watcher for watching for file system events.
 *
 * @param [watchDirectory] The path of the directory to watch.
 * @param [recursive] Watch the directory and all its descendants.
 */
data class FileSystemWatcher(val watchDirectory: Path, val recursive: Boolean) : Closeable {
    /**
     * The watcher for watching the file system.
     */
    private val watcher: WatchService = watchDirectory.fileSystem.newWatchService()

    /**
     * The absolute path of the directory being watched for each watch key.
     */
    private val pathsByKey = mutableMapOf<WatchKey, Path>()

    init {
        if (recursive) {
            for (path in Files.walk(watchDirectory)) {
                register(path)
            }
        } else {
            register(watchDirectory)
        }
    }

    /**
     * Watch the given [path] if it is the path of a directory.
     *
     * @return `true` if the [path] was registered, `false` if it was not a directory.
     */
    private fun register(path: Path): Boolean = if (Files.isDirectory(path)) {
        val key = path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
        pathsByKey[key] = path
        true
    } else {
        false
    }

    /**
     * A sequence of events that have occurred in the [watchDirectory] directory.
     */
    val events: Sequence<FileSystemEvent> = sequence {
        while (true) {
            val key = try {
                watcher.take()
            } catch (e: InterruptedException) {
                break
            }

            val directory: Path = pathsByKey[key] ?: continue

            for (event in key.pollEvents()) {
                @Suppress("UNCHECKED_CAST")
                event as WatchEvent<Path>

                val absolutePath = event.context()?.let { directory.resolve(it) } ?: continue

                if (event.kind() == OVERFLOW) continue
                if (recursive && event.kind() == ENTRY_CREATE) register(absolutePath)

                yield(FileSystemEvent(absolutePath, event.kind()))
            }

            if (!key.reset()) break
        }
    }

    override fun close() {
        watcher.close()
    }
}
