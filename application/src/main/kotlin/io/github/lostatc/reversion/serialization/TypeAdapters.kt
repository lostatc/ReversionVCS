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

package io.github.lostatc.reversion.serialization

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import io.github.lostatc.reversion.api.Config
import io.github.lostatc.reversion.api.ConfigProperty
import java.lang.reflect.Type
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A [JsonSerializer] for serializing [Config] objects as JSON.
 */
object ConfigSerializer : JsonSerializer<Config> {
    override fun serialize(src: Config?, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        context.serialize(src?.properties?.associate { it.key to src.getRaw(it) })
}

/**
 * A [JsonDeserializer] for de-serializing [Config] objects from JSON.
 *
 * @param [properties] The properties that were serialized.
 */
data class ConfigDeserializer(private val properties: Collection<ConfigProperty<*>>) :
    JsonDeserializer<Config> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Config {
        val jsonObject = json.asJsonObject
        val config = Config()

        for ((key, element) in jsonObject.entrySet()) {
            val property = properties.find { it.key == key } ?: continue
            config[property] = context.deserialize(element, String::class.java)
        }

        return config
    }
}

/**
 * A type adapter for serializing [Path] objects
 */
object PathTypeAdapter : TypeAdapter<Path>() {
    override fun write(writer: JsonWriter, value: Path?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value.toUri().toString())
        }
    }

    override fun read(reader: JsonReader): Path? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }

        return Paths.get(URI(reader.nextString()))
    }
}

/**
 * A type adapter for serializing [Path] objects that may be relative.
 */
object RelativePathTypeAdapter : TypeAdapter<Path>() {
    override fun write(writer: JsonWriter, value: Path?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value.toString())
        }
    }

    override fun read(reader: JsonReader): Path? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }

        return Paths.get(reader.nextString())
    }
}
