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

import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.transformation.TransformationList

class MappedList<E, F>(source: ObservableList<F>, val transform: (F) -> E) : TransformationList<E, F>(source) {
    override val size: Int
        get() = source.size

    override fun getSourceIndex(index: Int): Int = index

    override fun getViewIndex(index: Int): Int = index

    override fun get(index: Int): E = transform(source[index])

    override fun sourceChanged(c: ListChangeListener.Change<out F>) {
        fireChange(
            object : ListChangeListener.Change<E>(this) {
                override fun wasAdded(): Boolean = c.wasAdded()

                override fun wasRemoved(): Boolean = c.wasRemoved()

                override fun wasReplaced(): Boolean = c.wasReplaced()

                override fun wasUpdated(): Boolean = c.wasUpdated()

                override fun wasPermutated(): Boolean = c.wasPermutated()

                override fun getPermutation(i: Int): Int = c.getPermutation(i)

                override fun getPermutation(): IntArray = error("Unreachable code")

                override fun getRemoved(): MutableList<E> = c.removed.map { transform(it) }.toMutableList()

                override fun getFrom(): Int = c.from

                override fun getTo(): Int = c.to

                override fun next(): Boolean = c.next()

                override fun reset() {
                    c.reset()
                }
            }
        )
    }
}