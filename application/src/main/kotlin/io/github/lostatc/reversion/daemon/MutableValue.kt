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
import io.github.lostatc.reversion.storage.token
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Serializable
import java.rmi.Remote
import java.rmi.RemoteException

/**
 * A mutable value which can be read from and written to asynchronously.
 */
interface MutableValue<T> {
    /**
     * Get the value.
     */
    suspend fun get(): T

    /**
     * Set the value.
     */
    suspend fun set(value: T)

    /**
     * Apply the function [block] to the value to modify it.
     */
    suspend fun mutate(block: suspend (T) -> T) {
        set(block(get()))
    }
}

/**
 * A [Remote] for reading and writing a serializable value with Java RMI.
 */
interface SerializableValue<T : Serializable?> : MutableValue<T>, Remote {
    @Throws(RemoteException::class)
    override suspend fun get(): T

    @Throws(RemoteException::class)
    override suspend fun set(value: T)

    @Throws(RemoteException::class)
    override suspend fun mutate(block: suspend (T) -> T) {
        super.mutate(block)
    }
}

/**
 * Get a [SerializableValue] which serializes the value using [gson].
 */
inline fun <reified T> MutableValue<T>.serialized(
    gson: Gson
): SerializableValue<String> = object : SerializableValue<String> {
    @Throws(RemoteException::class)
    override suspend fun get(): String = gson.toJson(this@serialized.get(), token<T>().type)

    @Throws(RemoteException::class)
    override suspend fun set(value: String) {
        this@serialized.set(gson.fromJson(value, token<T>().type))
    }
}

/**
 * Get a [MutableValue] that deserializes the value using [gson].
 */
inline fun <reified T> SerializableValue<String>.deserialized(
    gson: Gson
): MutableValue<T> = object : MutableValue<T> {
    override suspend fun get(): T = gson.fromJson(this@deserialized.get(), token<T>().type)

    override suspend fun set(value: T) {
        this@deserialized.set(gson.toJson(value, token<T>().type))
    }
}

/**
 * Get a [MutableValue] which is safe against concurrent access.
 *
 * Calling [mutate][MutableValue.mutate] on the returned object modifies the value atomically.
 */
fun <T> MutableValue<T>.synchronized(): MutableValue<T> = object : MutableValue<T> {
    private val lock = Mutex()

    override suspend fun get(): T = lock.withLock { this@synchronized.get() }

    override suspend fun set(value: T) {
        lock.withLock { this@synchronized.set(value) }
    }

    override suspend fun mutate(block: suspend (T) -> T) {
        lock.withLock { this@synchronized.set(block(this@synchronized.get())) }
    }
}
