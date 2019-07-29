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

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import io.github.lostatc.reversion.storage.fromJson
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import kotlin.reflect.KClass

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
 * @param [type] The type of the elements in the set.
 * @param [typeAdapter] A type adapter for serializing elements as JSON.
 */
data class JsonPersistentSet<E>(
    val storageFile: Path,
    val type: KClass<*>,
    val typeAdapter: TypeAdapter<E>
) : PersistentSet<E> {
    /**
     * The object for serializing/de-serializing objects as JSON.
     */
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeHierarchyAdapter(type.java, typeAdapter)
        .create()

    override val elements: Set<E>
        get() = Files.newBufferedReader(storageFile).use { gson.fromJson(it) }

    override fun add(element: E): Boolean {
        if (element in elements) return false

        Files.newBufferedWriter(storageFile).use {
            gson.toJson(elements.plusElement(element))
        }

        return true
    }

    override fun remove(element: E): Boolean {
        if (element !in elements) return false

        Files.newBufferedReader(storageFile).use {
            gson.toJson(elements.minusElement(element))
        }

        return true
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
        inline fun <reified T> of(storageFile: Path, typeAdapter: TypeAdapter<T>): JsonPersistentSet<T> =
            JsonPersistentSet(storageFile, T::class, typeAdapter)
    }
}
