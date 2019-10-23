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

package io.github.lostatc.reversion.serialization

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlin.reflect.KClass

/**
 * A factory for serializing and deserializing types which are different at runtime.
 *
 * @param [baseType] The base type to be serialized and deserialized.
 * @param [typeFieldName] The name of the field for storing the type information.
 */
class RuntimeTypeAdapterFactory<T : Any>(
    private val baseType: KClass<T>,
    private val typeFieldName: String = "type"
) : TypeAdapterFactory {

    /**
     * A map of the labels of subtypes to their classes
     */
    private val labelToSubtype: MutableMap<String, KClass<out T>> = mutableMapOf()

    /**
     * A map of subtypes to their labels
     */
    private val subtypeToLabel: MutableMap<KClass<out T>, String> = mutableMapOf()

    /**
     * Registers the subclass [type] identified by [label] to be serialized and deserialized
     *
     * @throws IllegalArgumentException The given [type] or [label] is already registered
     */
    fun registerSubtype(type: KClass<out T>, label: String = type.simpleName ?: "") {
        require(type !in subtypeToLabel) { "The given type is already registered." }
        require(label !in labelToSubtype) { "The given label is already registered." }

        subtypeToLabel[type] = label
        labelToSubtype[label] = type
    }

    override fun <R : Any> create(gson: Gson, type: TypeToken<R>): TypeAdapter<R>? {
        if (!baseType.java.isAssignableFrom(type.rawType)) return null

        val labelToDelegate: MutableMap<String, TypeAdapter<*>> = mutableMapOf()
        val subtypeToDelegate: MutableMap<KClass<*>, TypeAdapter<*>> = mutableMapOf()

        for ((label, subtype) in labelToSubtype) {
            val delegate = gson.getDelegateAdapter(this, TypeToken.get(subtype.java))
            labelToDelegate[label] = delegate
            subtypeToDelegate[subtype] = delegate
        }

        return object : TypeAdapter<R>() {
            override fun write(writer: JsonWriter, value: R) {
                val valueType: KClass<*> = value::class
                val typeName = subtypeToLabel[valueType] ?: throw JsonParseException(
                    "Cannot serialize ${valueType.simpleName}. Is it registered as a subtype?"
                )

                @Suppress("UNCHECKED_CAST")
                val delegate = subtypeToDelegate[valueType] as TypeAdapter<R>

                val element = delegate.toJsonTree(value).asJsonObject
                if (element.has(typeFieldName)) throw JsonParseException(
                    "Cannot serialize ${valueType.simpleName} because it already defines a field named $typeFieldName."
                )
                element.addProperty(typeFieldName, typeName)

                gson.toJson(element, writer)
            }

            override fun read(reader: JsonReader): R {
                val element = JsonParser().parse(reader)
                val typeName = element.asJsonObject.remove(typeFieldName)?.asString ?: throw JsonParseException(
                    "Cannot deserialize $baseType because it does not define a field named $typeFieldName."
                )

                val delegate = labelToDelegate[typeName] ?: throw JsonParseException(
                    "Cannot deserialize $baseType subtype named $typeName. Is it registered as a subtype?"
                )

                @Suppress("UNCHECKED_CAST")
                delegate as TypeAdapter<R>

                return delegate.fromJsonTree(element)
            }
        }
    }
}
