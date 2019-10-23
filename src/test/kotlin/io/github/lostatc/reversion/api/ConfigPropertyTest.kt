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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigPropertyTest {
    @Test
    fun `create int property`() {
        val property = ConfigProperty.of("test", 0)
        val config = Config(property)

        assertEquals(0, config[property])

        config[property] = "100"

        assertEquals(100, config[property])
        assertThrows<ValueConvertException> {
            config[property] = "invalid value"
        }
    }

    @Test
    fun `create long property`() {
        val property = ConfigProperty.of("test", 0L)
        val config = Config(property)

        assertEquals(0L, config[property])

        config[property] = "100"

        assertEquals(100L, config[property])
        assertThrows<ValueConvertException> {
            config[property] = "invalid value"
        }
    }

    @Test
    fun `create float property`() {
        val property = ConfigProperty.of("test", 0.0f)
        val config = Config(property)

        assertEquals(0.0f, config[property])

        config[property] = "100"

        assertEquals(100.0f, config[property])
        assertThrows<ValueConvertException> {
            config[property] = "invalid value"
        }
    }

    @Test
    fun `create double property`() {
        val property = ConfigProperty.of("test", 0.0)
        val config = Config(property)

        assertEquals(0.0, config[property])

        config[property] = "100"

        assertEquals(100.0, config[property])
        assertThrows<ValueConvertException> {
            config[property] = "invalid value"
        }
    }

    @Test
    fun `create bool property`() {
        val property = ConfigProperty.of("test", true)
        val config = Config(property)

        assertEquals(true, config[property])

        config[property] = "false"

        assertEquals(false, config[property])
        assertThrows<ValueConvertException> {
            config[property] = "invalid value"
        }
    }
}