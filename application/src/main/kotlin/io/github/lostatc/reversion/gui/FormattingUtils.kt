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

package io.github.lostatc.reversion.gui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.pow

/**
 * Format an [Instant] as a string using the given [style].
 */
fun Instant.format(style: FormatStyle = FormatStyle.MEDIUM): String = DateTimeFormatter
    .ofLocalizedDateTime(style)
    .withZone(ZoneId.systemDefault())
    .format(this)

/**
 * A regex used to match a human-readable number of bytes.
 */
private val bytesRegex = Regex("""^(\d+)\s*((?:[kmgt]i?)?b)$""", RegexOption.IGNORE_CASE)

private fun Long.pow(exponent: Int): Long = toFloat().pow(exponent).toLong()

/**
 * Parse a human-readable number of bytes.
 *
 * @return A number of bytes or `null` if the [input] is invalid.
 */
fun parseBytes(input: String): Long? {
    val matchResult = bytesRegex.matchEntire(input.trim()) ?: return null
    val size = matchResult.groups[1]?.value?.toLong() ?: return null
    val unit = matchResult.groups[2]?.value ?: return null
    val multiplier = when (unit.toLowerCase()) {
        "b" -> 1L
        "kb" -> 1000L
        "kib" -> 1024L
        "mb" -> 1000L.pow(2)
        "mib" -> 1024L.pow(2)
        "gb" -> 1000L.pow(3)
        "gib" -> 1024L.pow(3)
        "tb" -> 1000L.pow(4)
        "tib" -> 1024L.pow(4)
        else -> return null
    }

    return size * multiplier
}
