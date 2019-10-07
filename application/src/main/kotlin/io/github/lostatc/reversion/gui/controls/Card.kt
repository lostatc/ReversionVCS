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

import io.github.lostatc.reversion.gui.getValue
import io.github.lostatc.reversion.gui.setValue
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import java.util.Objects

/**
 * An item in a list.
 */
class Card : VBox() {
    @FXML
    private lateinit var titleText: Label

    @FXML
    private lateinit var subtitleText: Label

    /**
     * A property for [title].
     */
    val titleProperty: Property<String> = SimpleObjectProperty()

    /**
     * The title of the list item.
     */
    var title: String by titleProperty

    /**
     * A property for [subtitle].
     */
    val subtitleProperty: Property<String> = SimpleObjectProperty()

    /**
     * The subtitle of the list item.
     */
    var subtitle: String by subtitleProperty

    init {
        FXMLLoader(this::class.java.getResource("/fxml/controls/Card.fxml")).apply {
            classLoader = this@Card::class.java.classLoader
            setRoot(this@Card)
            setController(this@Card)
            load()
        }
    }

    @FXML
    fun initialize() {
        titleText.textProperty().bind(titleProperty)
        subtitleText.textProperty().bind(subtitleProperty)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Card) return false
        return title == other.title && subtitle == other.subtitle
    }

    override fun hashCode(): Int = Objects.hash(title, subtitle)
}
