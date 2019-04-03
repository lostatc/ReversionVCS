/*
 * Copyright © 2019 Wren Powell
 *
 * This file is part of reversion.
 *
 * reversion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * reversion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with reversion.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.lostatc.reversion.storage

import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

/**
 * An attribute of a [Repository].
 *
 * @param [name] The name of the attribute.
 * @param [type] The type of the attribute's value.
 * @param [default] The default value of the attribute.
 * @param [description] A human-readable description of the attribute.
 */
data class RepositoryAttribute<T : Any>(
    val name: String,
    val type: KClass<T>,
    val default: T,
    val description: String = ""
) {
    companion object {
        /**
         * Constructs a [RepositoryAttribute] with an inferred type.
         *
         * @param [name] The name of the attribute.
         * @param [default] The default value of the attribute.
         * @param [description] A human-readable description of the attribute.
         */
        inline fun <reified T : Any> of(
            name: String,
            default: T,
            description: String = ""
        ): RepositoryAttribute<T> {
            return RepositoryAttribute(name = name, default = default, type = T::class, description = description)
        }
    }
}

/**
 * The configuration for a repository.
 *
 * This class maps [attributes][RepositoryAttribute] used for configuring a [Repository] to their values. It can be
 * initialized with a list of [attributes] which adds the attributes with their default values to the config.
 *
 * @param [attributes] The attributes to initialize this config with.
 */
class RepositoryConfig(attributes: List<RepositoryAttribute<*>>) {
    /**
     * A map of attributes to their values.
     */
    private val values: MutableMap<RepositoryAttribute<*>, Any?> =
        attributes.associateWith { it.default }.toMutableMap()

    /**
     * The attributes contained in this config.
     *
     * Adding an attribute to this set adds it to the config with its [default][RepositoryAttribute.default] value.
     * Removing an attribute from this set removes it from the config.
     */
    val attributes: MutableSet<RepositoryAttribute<*>> = object : MutableSet<RepositoryAttribute<*>> by values.keys {
        override fun add(element: RepositoryAttribute<*>): Boolean =
            values.put(element, element.default) == null

        override fun addAll(elements: Collection<RepositoryAttribute<*>>): Boolean =
        // Using `elements.any { add(it) }` may not add all the elements.
            elements.map { add(it) }.any()

        override fun equals(other: Any?): Boolean = values.keys == other

        override fun hashCode(): Int = values.keys.hashCode()

        override fun toString(): String = values.keys.toString()
    }

    /**
     * Initializes this config with the given [attributes].
     */
    constructor(vararg attributes: RepositoryAttribute<*>) : this(attributes.toList())

    /**
     * Sets the [value] of the given [attribute] in this config.
     */
    operator fun <T : Any> set(attribute: RepositoryAttribute<T>, value: T) {
        values[attribute] = value
    }

    /**
     * Returns the value of the given [attribute] in this config.
     *
     * @return The value in the config or the [default][RepositoryAttribute.default] value of the attribute if it is not
     * present in the config.
     */
    operator fun <T : Any> get(attribute: RepositoryAttribute<T>): T =
        values[attribute]?.let { attribute.type.cast(it) } ?: attribute.default

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RepositoryConfig) return false
        return other.values == values
    }

    override fun hashCode(): Int = Objects.hash(values)

    override fun toString(): String = values.mapKeys { it.key.name }.toString()
}
