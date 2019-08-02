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

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import kotlin.reflect.KClass

/**
 * Waits for an exclusive lock the given [file] and executes [block].
 *
 * The lock is released after [block] executes.
 *
 * @param [file] The path of the file to lock.
 * @param [block] A function which is passed the [FileLock].
 */
private fun <R> withLock(file: Path, block: (FileLock) -> R): R =
    FileChannel.open(file, WRITE).use { channel ->
        channel.lock().use { block(it) }
    }

/**
 * A persistent set of elements.
 */
interface PersistentSet<E> {
    /**
     * Returns the set of elements.
     */
    val elements: Set<E>

    /**
     * Adds the given [element] to the set.
     *
     * @return `true` if the [element] was added, `false` if it already existed.
     */
    fun add(element: E): Boolean

    /**
     * Removes the given [element] from the set.
     *
     * @return `true` if the [element] was removed, `false` if it did not exist.
     */
    fun remove(element: E): Boolean

    /**
     * Blocks and executes [handler] each time the set changes.
     *
     * @param [handler] A function which is passed the new set of elements.
     */
    fun onChange(handler: (Set<E>) -> Unit)

}

/**
 * A persistent set of elements backed by a JSON file.
 *
 * @param [storageFile] The path of the file where the set is stored.
 * @param [type] The type of [E].
 * @param [typeAdapter] A type adapter for serializing and de-serializing objects of type [E].
 */
data class JsonPersistentSet<E>(
    val storageFile: Path,
    private val type: KClass<*>,
    private val typeAdapter: TypeAdapter<E>? = null
) : PersistentSet<E> {

    /**
     * An object for serializing/de-serializing objects as JSON.
     */
    private val gson = GsonBuilder().run {
        setPrettyPrinting()
        typeAdapter?.let { registerTypeHierarchyAdapter(type.java, typeAdapter) }
        create()
    }

    /**
     * A type token representing the set of elements.
     */
    private val setToken = TypeToken.getParameterized(Set::class.java, type.java)

    override val elements: Set<E>
        get() = Files.newBufferedReader(storageFile).use { gson.fromJson(it, setToken.type) }

    init {
        // Create an empty JSON file if it doesn't exist.
        try {
            Files.newBufferedWriter(storageFile, WRITE, CREATE_NEW).use {
                gson.toJson(emptySet<E>(), it)
            }
        } catch (e: java.nio.file.FileAlreadyExistsException) {
            // Ignore
        }
    }

    override fun add(element: E): Boolean = withLock(storageFile) {
        // Lock the file to prevent a race condition.
        val currentElements = elements
        if (element in currentElements) return@withLock false
        Files.newBufferedWriter(storageFile).use {
            gson.toJson(currentElements.plusElement(element), it)
        }
        true
    }

    override fun remove(element: E): Boolean = withLock(storageFile) {
        // Lock the file to prevent a race condition.
        val currentElements = elements
        if (element !in currentElements) return@withLock false
        Files.newBufferedWriter(storageFile).use {
            gson.toJson(currentElements.minusElement(element), it)
        }
        true
    }

    override fun onChange(handler: (Set<E>) -> Unit) {
        FileSystemWatcher(storageFile.parent, recursive = false).use {
            for (event in it.events) {
                if (event.type == ENTRY_MODIFY && event.path == storageFile) {
                    handler(elements)
                }
            }
        }
    }

    companion object {
        /**
         * Create a [JsonPersistentSet] with an inferred [type].
         */
        inline fun <reified T> of(storageFile: Path, typeAdapter: TypeAdapter<T>? = null): JsonPersistentSet<T> =
            JsonPersistentSet(storageFile, T::class, typeAdapter)
    }
}
