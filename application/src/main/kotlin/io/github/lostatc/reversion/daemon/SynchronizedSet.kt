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

package io.github.lostatc.reversion.daemon

/**
 * A collection interface which is safe for concurrent access.
 */
interface SynchronizedSet<E> {
    suspend fun add(element: E): Boolean

    suspend fun addAll(elements: Collection<E>): Boolean = elements.any { add(it) }

    suspend fun remove(element: E): Boolean

    suspend fun removeAll(elements: Collection<E>): Boolean = elements.any { remove(it) }

    suspend fun contains(element: E): Boolean

    suspend fun containsAll(elements: Collection<E>): Boolean = elements.all { contains(it) }

    suspend fun toSet(): Set<E>
}
