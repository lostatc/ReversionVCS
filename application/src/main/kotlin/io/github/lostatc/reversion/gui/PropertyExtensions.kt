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

import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.beans.property.Property
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.ReadOnlyProperty
import javafx.collections.ObservableList
import javafx.collections.transformation.SortedList
import java.util.concurrent.Callable
import kotlin.reflect.KProperty

operator fun <T> ReadOnlyProperty<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

operator fun <T> Property<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.value = value
}

/**
 * Returns a read-only property with a value equal to this property with the given [transform] function applied.
 */
fun <T, R> ReadOnlyProperty<T>.toMappedProperty(transform: (T) -> R): ReadOnlyProperty<R> {
    val wrapper = ReadOnlyObjectWrapper<R>(transform(value))
    addListener { _, _, newValue -> wrapper.value = transform(newValue) }
    return wrapper.readOnlyProperty
}

/**
 * Returns a read-only property with a value equal to this property with the given [transform] function applied.
 *
 * If the value of this property is `null`, a placeholder string is returned.
 */
fun <T : Any> ReadOnlyProperty<T?>.toDisplayProperty(transform: (T) -> String): ReadOnlyProperty<String> =
    toMappedProperty { if (it == null) "Loading..." else transform(it) }

/**
 * Returns a read-only sorted view of this list.
 */
fun <T : Comparable<T>> ObservableList<T>.toSorted(): SortedList<T> = SortedList(this, compareBy { it })

/**
 * Returns a read-only sorted view of this list which is sorted by the given [selector].
 */
fun <T> ObservableList<T>.toSortedBy(selector: (T) -> Comparable<T>): SortedList<T> =
    SortedList(this, compareBy(selector))

/**
 * Create a binding on this property.
 *
 * @param [dependencies] This binding's dependencies.
 * @param [transform] The function which yields the new value.
 */
fun <T> Property<T>.createBinding(vararg dependencies: Observable, transform: () -> T) {
    bind(Bindings.createObjectBinding(Callable { transform() }, *dependencies))
}
