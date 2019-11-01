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

package io.github.lostatc.reversion.daemon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService

/**
 * Returns a [Flow] which emits each element in [elements] after a [delayMillis]-millisecond delay.
 *
 * If [coalesce] is `true`, identical elements which are read from [elements] will be combined before being emitted by
 * the flow. For this option to be used, elements of type [T] must be immutable.
 */
@UseExperimental(FlowPreview::class)
fun <T> CoroutineScope.delayed(delayMillis: Long, elements: Sequence<T>, coalesce: Boolean = true): Flow<T> {
    val delayJobs = mutableMapOf<T, Job>()

    return flow {
        for (element in elements) {
            if (coalesce) {
                delayJobs[element]?.cancel()
            }

            val job = launch {
                delay(delayMillis)
                emit(element)
            }

            if (coalesce) {
                delayJobs[element] = job
            }
        }
    }
}

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
 * @param [coalesce] Combine identical file system events.
 * @param [includeMatcher] A path matcher which matches paths to watch, or `null` to watch all paths.
 */
data class FileSystemWatcher(
    val watchDirectory: Path,
    val recursive: Boolean,
    val coalesce: Boolean,
    val includeMatcher: PathMatcher? = null
) : Closeable {
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
            val events = if (coalesce) {
                key.pollEvents().distinctBy { it.kind() to it.context() }
            } else {
                key.pollEvents()
            }

            for (event in events) {
                @Suppress("UNCHECKED_CAST")
                event as WatchEvent<Path>

                val absolutePath = event.context()?.let { directory.resolve(it) } ?: continue

                if (includeMatcher != null && !includeMatcher.matches(absolutePath)) continue
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
