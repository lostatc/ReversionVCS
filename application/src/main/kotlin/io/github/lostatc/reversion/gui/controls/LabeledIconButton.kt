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

import com.jfoenix.controls.JFXRippler
import io.github.lostatc.reversion.gui.getValue
import io.github.lostatc.reversion.gui.setValue
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseEvent
import javafx.scene.shape.Circle
import org.kordamp.ikonli.javafx.FontIcon

/**
 * A circular button that displays an icon.
 */
class LabeledIconButton : JFXRippler() {
    /**
     * The icon top display on the button.
     */
    @FXML
    private lateinit var fontIcon: FontIcon

    /**
     * The click box of the button.
     */
    @FXML
    private lateinit var foreground: Circle

    /**
     * The label to display for the button.
     */
    @FXML
    private lateinit var buttonLabel: Label

    /**
     * A property for [icon].
     */
    val iconProperty: Property<String> = SimpleObjectProperty()

    /**
     * The [icon code][FontIcon.getIconLiteral] for the icon to display.
     */
    var icon: String by iconProperty

    /**
     * A property for [onAction].
     */
    val onActionProperty: Property<EventHandler<MouseEvent>> = SimpleObjectProperty()

    /**
     * The action to take when the button is clicked.
     */
    var onAction: EventHandler<MouseEvent> by onActionProperty

    /**
     * A property for [tooltip].
     */
    val tooltipProperty: Property<Tooltip?> = SimpleObjectProperty()

    /**
     * The tooltip to display on this button.
     */
    var tooltip: Tooltip? by tooltipProperty

    /**
     * A property for [label].
     */
    val labelProperty: Property<String> = SimpleObjectProperty("Label")

    /**
     * The text to display below the button, or `null` to remove the label.
     */
    var label: String by labelProperty

    /**
     * A property for [radius].
     */
    val radiusProperty: Property<Float> = SimpleObjectProperty(18.0f)

    /**
     * The radius of the button.
     */
    var radius: Float by radiusProperty

    init {
        FXMLLoader(this::class.java.getResource("/fxml/controls/LabeledIconButton.fxml")).apply {
            classLoader = this@LabeledIconButton::class.java.classLoader
            setRoot(this@LabeledIconButton)
            setController(this@LabeledIconButton)
            load()
        }
    }

    @FXML
    fun initialize() {
        iconProperty.addListener { _, _, newValue ->
            fontIcon.iconLiteral = newValue
        }

        buttonLabel.textProperty().bind(labelProperty)

        tooltipProperty.addListener { _, _, newValue ->
            Tooltip.install(foreground, newValue)
            Tooltip.install(buttonLabel, newValue)
        }

        foreground.setOnMouseClicked { event -> onAction.handle(event) }

        foreground.radiusProperty().bind(radiusProperty)
    }
}
