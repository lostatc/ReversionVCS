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

import javafx.beans.property.ReadOnlyProperty
import javafx.scene.Node

/**
 * The output of a [Form].
 */
sealed class FormResult<T> {
    /**
     * The form is valid and produced the given [value].
     */
    data class Valid<T>(val value: T) : FormResult<T>()

    /**
     * The form has invalid input and the given error [message], if not `null`, should be displayed.
     */
    data class Invalid<T>(val message: String? = null) : FormResult<T>()

    inline fun onInvalid(block: (Invalid<T>) -> T): T = when (this) {
        is Valid -> value
        is Invalid -> block(this)
    }
}

/**
 * An form for user input that encapsulates multiple controls.
 */
interface Form<T> {
    /**
     * A property for [result].
     */
    val resultProperty: ReadOnlyProperty<FormResult<T>>

    /**
     * The node representing the form.
     */
    val node: Node

    /**
     * The output of the form.
     */
    val result: FormResult<T>
        get() = resultProperty.value

    /**
     * Clear the fields of the form.
     */
    fun clear()
}
