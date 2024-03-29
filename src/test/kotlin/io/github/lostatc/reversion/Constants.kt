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

package io.github.lostatc.reversion

import io.github.lostatc.reversion.api.io.FixedSizeChunker
import io.github.lostatc.reversion.api.io.ZpaqChunker

/**
 * The size of randomly generated test files in bytes.
 */
const val TEST_FILE_SIZE: Int = 4096

/**
 * The size of chunks to use when testing [FixedSizeChunker].
 */
const val TEST_CHUNK_SIZE: Long = 256L

/**
 * The number of bits to use when testing [ZpaqChunker].
 */
const val TEST_CHUNK_BITS: Int = 8
