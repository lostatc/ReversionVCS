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

package io.github.lostatc.reversion.gui.controls

import javafx.scene.control.Label
import javafx.scene.control.ListView
import java.util.Objects

/**
 * A [Label] for use in a [ListView].
 *
 * @param [text] The text display in the label.
 */
class ListItem(text: String) : Label(text) {

    init {
        styleClass += "list-item"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Label) return false
        return text == other.text
    }

    override fun hashCode(): Int = Objects.hash(text)
}
