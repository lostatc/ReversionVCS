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

package io.github.lostatc.reversion.api

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import kotlin.reflect.KProperty

/**
 * An property used for configuration.
 *
 * Instances of this class can be used as property delegates on implementations of [Configurable].
 *
 * @param [key] The unique identifier of the property.
 * @param [default] The default value of the property.
 * @param [type] The [Type] of the value this property holds.
 */
data class ConfigProperty<T>(val key: String, val default: T, val type: Type) {
    /**
     * Gets the value associated with this property from the [config][Configurable.config].
     */
    operator fun getValue(thisRef: Configurable, property: KProperty<*>): T = thisRef.config[this]

    /**
     * Sets the value associated with this property in the [config][Configurable.config].
     */
    operator fun setValue(thisRef: Configurable, property: KProperty<*>, value: T) {
        thisRef.config[this] = value
    }

    companion object {
        /**
         * Create a new [ConfigProperty] with an inferred [type].
         */
        inline fun <reified T> of(key: String, default: T): ConfigProperty<T> =
            ConfigProperty(key, default, token<T>().type)
    }
}

/**
 * A configuration that maps [ConfigProperty] objects to values.
 */
interface Config {
    /**
     * Return the value of the given [property] in this config.
     *
     * @return The value in the config or [ConfigProperty.default] if it is not present in the config.
     */
    operator fun <T> get(property: ConfigProperty<T>): T

    /**
     * Sets the [value] of the given [property] in this config.
     */
    operator fun <T> set(property: ConfigProperty<T>, value: T)

    /**
     * Write the values in this [Config] to persistent storage.
     */
    fun write()

    /**
     * Populate this [Config] with values from persistent storage.
     */
    fun read()
}

/**
 * An object which updates values in a [Config].
 */
interface Configurator {
    /**
     * Update the values in the given [config].
     */
    fun configure(config: Config)

    /**
     * A [Configurator] which does nothing.
     */
    object Default : Configurator {
        override fun configure(config: Config) = Unit
    }
}

/**
 * Create a [Configurator] from the given function.
 */
fun Configurator(func: (Config) -> Unit): Configurator = object : Configurator {
    override fun configure(config: Config) {
        func(config)
    }
}

/**
 * A [Config] that stores values as JSON.
 *
 * @param [file] The file to persistently storage values in.
 * @param [gson] The object used to serialize values as JSON.
 */
class JsonConfig(private val file: Path, private val gson: Gson) : Config {
    /**
     * A map of properties to their serialized values.
     */
    private val elements: MutableMap<String, JsonElement> = mutableMapOf()

    override fun <T> get(property: ConfigProperty<T>): T =
        elements[property.key]?.let { gson.fromJson<T>(it, property.type) } ?: property.default

    override fun <T> set(property: ConfigProperty<T>, value: T) {
        elements[property.key] = gson.toJsonTree(value, property.type)
    }

    override fun write() {
        Files.newBufferedWriter(file, CREATE, TRUNCATE_EXISTING).use {
            val jsonObject = JsonObject().apply {
                for ((key, element) in elements) {
                    add(key, element)
                }
            }
            gson.toJson(jsonObject, it)
        }
    }

    override fun read() {
        val newElements = Files.newBufferedReader(file).use { reader ->
            JsonParser().parse(reader).asJsonObject.entrySet().associate { it.key to it.value }
        }

        elements.putAll(newElements)
    }
}

/**
 * Something that can be configured.
 */
interface Configurable {
    /**
     * The configuration for this object.
     */
    val config: Config
}
