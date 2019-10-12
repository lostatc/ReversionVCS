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

import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.util.Callback

/**
 * A cell factory which maps objects to string representations.
 *
 * @param [transform] A function that converts an object to its string representation.
 */
class MappingCellFactory<T>(val transform: (T) -> String) : Callback<ListView<T>, ListCell<T>> {
    override fun call(param: ListView<T>?): ListCell<T> = object : ListCell<T>() {
        override fun updateItem(item: T, empty: Boolean) {
            super.updateItem(item, empty)
            text = if (empty) "" else transform(item)
        }
    }
}
