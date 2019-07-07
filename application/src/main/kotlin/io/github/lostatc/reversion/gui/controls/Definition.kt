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

package io.github.lostatc.reversion.gui.controls

import io.github.lostatc.reversion.gui.getValue
import io.github.lostatc.reversion.gui.setValue
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import org.kordamp.ikonli.javafx.FontIcon

/**
 * A list item that displays a definition with a [name] and [value].
 */
class Definition : HBox() {

    /**
     * The root node of the control.
     */
    @FXML
    private lateinit var root: Pane

    /**
     * The icon to display.
     */
    @FXML
    private lateinit var fontIcon: FontIcon

    /**
     * The label containing the name.
     */
    @FXML
    private lateinit var nameLabel: Label

    /**
     * The label containing the value.
     */
    @FXML
    private lateinit var valueLabel: Label

    /**
     * A property for [icon].
     */
    val iconProperty: Property<String?> = SimpleObjectProperty()

    /**
     * The [icon code][FontIcon.getIconLiteral] for the icon to display, or `null` to display no icon.
     */
    var icon: String? by iconProperty

    /**
     * A property for [name].
     */
    val nameProperty: Property<String> = SimpleObjectProperty()

    /**
     * The name of the thing to define.
     */
    var name: String by nameProperty

    /**
     * A property for [value].
     */
    val valueProperty: Property<String> = SimpleObjectProperty()

    /**
     * The value of [name].
     */
    var value: String by valueProperty

    /**
     * A property for [tooltip].
     */
    val tooltipProperty: Property<Tooltip?> = SimpleObjectProperty()

    /**
     * The tooltip to display on this button.
     */
    var tooltip: Tooltip? by tooltipProperty

    init {
        FXMLLoader(this::class.java.getResource("/fxml/Definition.fxml")).apply {
            classLoader = this@Definition::class.java.classLoader
            setRoot(this@Definition)
            setController(this@Definition)
            load()
        }
    }

    @FXML
    fun initialize() {
        tooltipProperty.addListener { _, _, newValue ->
            Tooltip.install(root, newValue)
        }

        nameLabel.textProperty().bind(nameProperty)
        valueLabel.textProperty().bind(valueProperty)

        iconProperty.addListener { _, _, newValue ->
            fontIcon.iconLiteral = newValue
        }
    }
}
