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

package io.github.lostatc.reversion.gui

import javafx.beans.property.ReadOnlyProperty
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.control.Label
import javafx.scene.layout.VBox

data class ListItem(val title: ReadOnlyProperty<String>, val subtitle: String) : VBox() {
    @FXML
    private lateinit var titleText: Label

    @FXML
    private lateinit var subtitleText: Label

    init {
        FXMLLoader(this::class.java.getResource("/fxml/ListItem.fxml")).apply {
            setRoot(this@ListItem)
            setController(this@ListItem)
            load()
        }
    }

    @FXML
    fun initialize() {
        titleText.textProperty().bind(title)
        subtitleText.text = subtitle
    }
}
