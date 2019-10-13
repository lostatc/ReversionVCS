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
import javafx.beans.value.WritableValue
import java.io.Serializable
import java.rmi.Remote
import java.rmi.RemoteException

/**
 * A [Remote] for reading and writing a serializable value with Java RMI.
 */
interface SerializableValue<T : Serializable> : WritableValue<T>, Remote {
    /**
     * Get the serializable value, or `null` if no value exists.
     */
    @Throws(RemoteException::class)
    override fun getValue(): T?

    /**
     * Set the serializable value.
     */
    @Throws(RemoteException::class)
    override fun setValue(value: T)
}

/**
 * Get a [SerializableValue] which serializes the value using [gson].
 */
inline fun <reified T> WritableValue<T>.serialized(
    gson: Gson
): SerializableValue<String> = object : SerializableValue<String> {
    @Throws(RemoteException::class)
    override fun getValue(): String? = gson.toJson(this@serialized.value, token<T>().type)

    @Throws(RemoteException::class)
    override fun setValue(value: String) {
        this@serialized.value = gson.fromJson(value, token<T>().type)
    }
}

/**
 * Get a [WritableValue] that deserializes the value using [gson].
 *
 * If there is no value to deserialize, [default] is returned.
 */
inline fun <reified T> SerializableValue<String>.deserialized(
    gson: Gson,
    default: T
): WritableValue<T> = object : WritableValue<T> {
    override fun getValue(): T {
        val value = this@deserialized.value ?: return default
        return gson.fromJson(value, token<T>().type)
    }

    override fun setValue(value: T) {
        this@deserialized.setValue(gson.toJson(value, token<T>().type))
    }
}
