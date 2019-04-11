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

import java.util.*
import kotlin.reflect.KProperty

/**
 * An exception that is thrown when a config value cannot be converted.
 *
 * @param [property] The config property that the value is being assigned to.
 * @param [value] The value being converted.
 */
data class ValueConvertException(
    override val message: String,
    val property: ConfigProperty<*>,
    val value: String
) : Exception()

/**
 * A receiver for value converters.
 */
data class ConvertContext(val property: ConfigProperty<*>, val value: String) {
    /**
     * Throws an exception indicating that the value could not be converted.
     */
    fun fail(message: String = "Invalid value '$value'."): Nothing {
        throw ValueConvertException(message, property, value)
    }

    /**
     * Calls [fail] with the output of [lazyMessage] if [value] is `false`.
     */
    fun require(value: Boolean, lazyMessage: () -> String = { "Invalid value '$value'." }) {
        if (!value) fail(lazyMessage())
    }
}
/**
 * An property used for configuration.
 *
 * Instances of this class can be used as property delegates on implementations of [Configurable].
 *
 * @param [key] The unique identifier of the property.
 * @param [default] The default value of the property.
 * @param [converter] A function that converts a string to a valid value for this property.
 * @param [name] The human-readable name of the property.
 * @param [description] The human-readable description of the property.
 */
data class ConfigProperty<T>(
    val key: String,
    val default: String,
    val converter: ConvertContext.(String) -> T,
    val name: String = key,
    val description: String = ""
) {
    /**
     * Convert a string to a valid value for this property.
     */
    fun convert(value: String): T = ConvertContext(this, value).converter(value)

    /**
     * Gets the value associated with this property from the [config][Configurable.config].
     */
    operator fun getValue(thisRef: Configurable, property: KProperty<*>): T = thisRef.config[this]

    /**
     * Associates the given [value] with this property in the [config][Configurable.config].
     */
    operator fun setValue(thisRef: Configurable, property: KProperty<*>, value: String) {
        thisRef.config[this] = value
    }

    companion object {
        /**
         * Constructs a [ConfigProperty] for a [String] value.
         */
        fun of(key: String, default: String, name: String = key, description: String = ""): ConfigProperty<String> =
            ConfigProperty(
                key = key,
                default = default,
                converter = { it },
                name = name,
                description = description
            )

        /**
         * Constructs a [ConfigProperty] for an [Int] value.
         */
        fun of(key: String, default: Int, name: String = key, description: String = ""): ConfigProperty<Int> =
            ConfigProperty(
                key = key,
                default = default.toString(),
                converter = { it.toIntOrNull() ?: fail("The value '$it' must be an integer.") },
                name = name,
                description = description
            )

        /**
         * Constructs a [ConfigProperty] for a [Long] value.
         */
        fun of(key: String, default: Long, name: String = key, description: String = ""): ConfigProperty<Long> =
            ConfigProperty(
                key = key,
                default = default.toString(),
                converter = { it.toLongOrNull() ?: fail("The value '$it' must be an integer.") },
                name = name,
                description = description
            )

        /**
         * Constructs a [ConfigProperty] for a [Float] value.
         */
        fun of(key: String, default: Float, name: String = key, description: String = ""): ConfigProperty<Float> =
            ConfigProperty(
                key = key,
                default = default.toString(),
                converter = { it.toFloatOrNull() ?: fail("The value '$it' must be a decimal.") },
                name = name,
                description = description
            )

        /**
         * Constructs a [ConfigProperty] for a [Double] value.
         */
        fun of(key: String, default: Double, name: String = key, description: String = ""): ConfigProperty<Double> =
            ConfigProperty(
                key = key,
                default = default.toString(),
                converter = { it.toDoubleOrNull() ?: fail("The value '$it' must be a decimal.") },
                name = name,
                description = description
            )

        /**
         * Constructs a [ConfigProperty] for a [Boolean] value.
         */
        fun of(key: String, default: Boolean, name: String = key, description: String = ""): ConfigProperty<Boolean> =
            ConfigProperty(
                key = key,
                default = default.toString(),
                converter = {
                    when (it.toLowerCase()) {
                        "yes", "true" -> true
                        "no", "false" -> false
                        else -> fail("The value '$it' must be boolean.")
                    }
                },
                name = name,
                description = description
            )
    }
}

/**
 * A configuration that maps [config properties][ConfigProperty] to values.
 *
 * @param [properties] Properties to initialize the config with which are mapped to their default values.
 */
class Config(vararg properties: ConfigProperty<*>) {
    /**
     * A map of properties to their values.
     */
    private val valueByProperty: MutableMap<ConfigProperty<*>, String> =
        properties.associate { it to it.default }.toMutableMap()

    /**
     * The set of properties contained in this config.
     */
    val properties: Set<ConfigProperty<*>> = valueByProperty.keys

    /**
     * Sets the [value] of the given [property] in this config.
     */
    operator fun set(property: ConfigProperty<*>, value: String) {
        valueByProperty[property] = value
    }

    /**
     * Returns the converted value of the given [property] in this config.
     *
     * @return The value in the config or the [default][ConfigProperty.default] value of the property if it is not
     * present in the config.
     */
    operator fun <T> get(property: ConfigProperty<T>): T = property.convert(getRaw(property))

    /**
     * Returns the raw value of the given [property] in this config.
     *
     * @return The value in the config or the [default][ConfigProperty.default] value of the property if it is not
     * present in the config.
     */
    fun getRaw(property: ConfigProperty<*>): String = valueByProperty[property] ?: property.default

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
