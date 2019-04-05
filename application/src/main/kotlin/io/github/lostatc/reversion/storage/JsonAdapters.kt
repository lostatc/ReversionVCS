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

import com.google.gson.*
import io.github.lostatc.reversion.api.Config
import io.github.lostatc.reversion.api.ConfigProperty
import java.lang.reflect.Type
import kotlin.reflect.jvm.javaType

/**
 * A [JsonSerializer] for serializing [Config] objects as JSON.
 */
object ConfigSerializer : JsonSerializer<Config> {
    override fun serialize(src: Config, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        context.serialize(src.properties.map { it.key to src[it] })
}

/**
 * A [JsonDeserializer] for de-serializing [Config] objects from JSON.
 *
 * @param [properties] The properties that were serialized.
 */
data class ConfigDeserializer(private val properties: Collection<ConfigProperty<*>>) : JsonDeserializer<Config> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Config {
        val jsonObject = json.asJsonObject
        val config = Config()

        for ((key, element) in jsonObject.entrySet()) {
            val property = properties.find { it.key == key } ?: continue
            config[property] = context.deserialize(element, property.type.javaType)
        }

        return config
    }
}