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

import java.io.InputStream

/**
 * An [InputStream] that allows for setting a limit on the number of bytes which can be read.
 *
 * @param [inputStream] The input stream to wrap.
 * @param [limit] The maximum number of bytes which can be read.
 */
data class BoundedInputStream(val inputStream: InputStream, val limit: Long) : InputStream() {
    /**
     * The number of bytes which have been read by the input stream.
     */
    private var bytesRead: Long = 0

    override fun read(): Int {
        bytesRead++
        return if (bytesRead > limit) -1 else inputStream.read()
    }
}