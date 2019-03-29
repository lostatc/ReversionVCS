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
import java.security.MessageDigest

/**
 * A large piece of binary data.
 */
interface Blob {
    /**
     * An input stream for reading the data in this blob.
     */
    val inputStream: InputStream

    /**
     * The checksum of the data in this blob.
     */
    val checksum: Checksum

    /**
     * Returns whether the hash of [inputStream] is equal to [checksum].
     *
     * @param [algorithm] The name of the hash algorithm to use. This accepts any algorithm accepted by [MessageDigest].
     */
    fun isValid(algorithm: String): Boolean = checksum == Checksum.fromInputStream(inputStream, algorithm)
}

/**
 * An simple data class implementation of [Blob].
 */
data class SimpleBlob(override val inputStream: InputStream, override val checksum: Checksum) : Blob

/**
 * An implementation of [Blob] that lazily computes the [checksum] from the [inputStream].
 *
 * @param [algorithm] The name of the algorithm to compute the checksum with. This accepts any algorithm accepted by
 * [MessageDigest].
 */
data class LazyBlob(override val inputStream: InputStream, val algorithm: String) : Blob {
    override val checksum: Checksum by lazy { Checksum.fromInputStream(inputStream, algorithm) }
}
