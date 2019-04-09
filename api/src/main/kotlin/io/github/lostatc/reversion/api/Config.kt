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

package io.github.lostatc.reversion.api

import java.util.*
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.createType

/**
 * An property used for configuration.
 *
 * Instances of this class can be used as property delegates on implementations of [Configurable].
 *
 * @param [key] The unique identifier of the property.
 * @param [default] The default value of the property.
 * @param [type] The type of the property's value.
 * @param [name] The human-readable name of the property.
 * @param [description] The human-readable description of the property.
 */
data class ConfigProperty<T>(
    val key: String,
    val default: T,
    val type: KType,
    val name: String = key,
    val description: String = ""
) {
    /**
     * Gets the value associated with this property from the [config][Configurable.config].
     */
    operator fun getValue(thisRef: Configurable, property: KProperty<*>): T = thisRef.config[this]

    /**
     * Associates the given [value] with this property in the [config][Configurable.config].
     */
    operator fun setValue(thisRef: Configurable, property: KProperty<*>, value: T) {
        thisRef.config[this] = value
    }

    companion object {
        /**
         * Constructs a [ConfigProperty] with an inferred type.
         *
         * @param [key] The unique identifier of the property.
         * @param [default] The default value of the property.
         * @param [name] The human-readable name of the property.
         * @param [description] The human-readable description of the property.
         */
        inline fun <reified T> of(
            key: String,
            default: T,
            name: String = key,
            description: String = ""
        ): ConfigProperty<T> {
            return ConfigProperty(
                key = key,
                default = default,
                type = T::class.createType(),
                name = name,
                description = description
            )
        }
    }
}

/**
 * A configuration that maps [config properties][ConfigProperty] to values.
 *
 * The [key][ConfigProperty.key] of each property in this config is unique.
 */
class Config {
    /**
     * A map of properties to their values.
     */
    private val valueByProperty: MutableMap<ConfigProperty<*>, Any?> = mutableMapOf()

    /**
     * The set of properties contained in this config.
     */
    val properties: Set<ConfigProperty<*>> = valueByProperty.keys

    /**
     * Sets the [value] of the given [property] in this config.
     *
     * If another property with the same [key][ConfigProperty.key] exists in this config, it is removed.
     */
    operator fun <T> set(property: ConfigProperty<T>, value: T) {
        valueByProperty.keys.removeAll { it.key == property.key }
        valueByProperty[property] = value
    }

    /**
     * Returns the value of the given [property] in this config.
     *
     * @return The value in the config or the [default][ConfigProperty.default] value of the property if it is not
     * present in the config.
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(property: ConfigProperty<T>): T =
        valueByProperty[property]?.let { it as T } ?: property.default

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Config) return false
        return other.valueByProperty == valueByProperty
    }

    override fun hashCode(): Int = Objects.hash(valueByProperty)

    override fun toString(): String = valueByProperty.mapKeys { it.key.name }.toString()
}

/**
 * Something that can be configured.
 */
interface Configurable {
    /**
     * A configuration.
     */
    val config: Config
}
