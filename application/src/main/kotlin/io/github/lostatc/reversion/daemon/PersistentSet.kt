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

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import kotlin.reflect.KClass

/**
 * Waits for an exclusive lock the given [file] and executes [block].
 *
 * The lock is released after [block] executes.
 *
 * @param [file] The path of the file to lock.
 * @param [block] A function which is passed the channel which created the lock.
 */
private fun <R> withLock(file: Path, block: (FileChannel) -> R): R =
    FileChannel.open(file, READ, WRITE).use { channel ->
        channel.lock().use { block(channel) }
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
 * @param [typeAdapter] A type adapter for serializing and de-serializing objects of type [E], or `null` if none is
 * required.
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
        get() = FileChannel.open(storageFile).use { it.fromJson() }

    init {
        // Create an empty JSON file if it doesn't exist.
        try {
            FileChannel.open(storageFile, WRITE, CREATE_NEW).use { it.toJson(emptySet()) }
        } catch (e: java.nio.file.FileAlreadyExistsException) {
            // Ignore
        }
    }

    /**
     * Serializes the given [elements] to this channel as JSON.
     *
     * The file is truncated before anything is written. This does not close the channel.
     */
    private fun FileChannel.toJson(elements: Set<E>) {
        truncate(0)
        val writer = Channels.newWriter(this, Charset.defaultCharset())
        gson.toJson(elements, writer)
        writer.flush()
    }

    /**
     * De-serializes the elements from this channel as JSON.
     *
     * This does not close the channel.
     */
    private fun FileChannel.fromJson(): Set<E> {
        val reader = Channels.newReader(this, Charset.defaultCharset())
        return gson.fromJson(reader, setToken.type)
    }

    override fun add(element: E): Boolean = withLock(storageFile) {
        // Lock the file to prevent a race condition.
        val currentElements = it.fromJson()
        if (element in currentElements) return@withLock false
        it.toJson(currentElements.plusElement(element))
        true
    }

    override fun remove(element: E): Boolean = withLock(storageFile) {
        // Lock the file to prevent a race condition.
        val currentElements = it.fromJson()
        if (element !in currentElements) return@withLock false
        it.toJson(currentElements.minusElement(element))
        true
    }

    override fun onChange(handler: (Set<E>) -> Unit) {
        FileSystemWatcher(
            storageFile.parent,
            recursive = false,
            coalesce = true,
            includeMatcher = PathMatcher { it == storageFile }
        ).use {
            for (event in it.events) {
                if (event.type == ENTRY_MODIFY) {
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
