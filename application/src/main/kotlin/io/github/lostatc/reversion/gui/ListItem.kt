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

import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.layout.VBox
import javafx.scene.text.Text

data class ListItem(val title: String, val subtitle: String) : VBox() {
    @FXML
    private lateinit var titleNode: Text

    @FXML
    private lateinit var subtitleNode: Text

    init {
        FXMLLoader(this::class.java.getResource("/fxml/ListItem.fxml")).apply {
            setRoot(this)
            setController(this)
            load()
        }
    }

    @FXML
    fun initialize() {
        titleNode.text = title
        subtitleNode.text = subtitle
    }
}
