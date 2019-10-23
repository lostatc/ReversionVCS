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

import io.github.lostatc.reversion.api.getValue
import io.github.lostatc.reversion.api.loadFxml
import io.github.lostatc.reversion.api.setValue
import io.github.lostatc.reversion.gui.infoDialog
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXML
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.AnchorPane.setBottomAnchor
import javafx.scene.layout.AnchorPane.setLeftAnchor
import javafx.scene.layout.AnchorPane.setRightAnchor
import javafx.scene.layout.AnchorPane.setTopAnchor
import javafx.scene.layout.StackPane
import java.util.Objects

/**
 * A position along the edge of the screen to anchor a node to.
 */
enum class AnchorPoint {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

/**
 * A container which displays a help button that opens a help prompt.
 */
class HelpPane : StackPane() {
    /**
     * The button to press to open a help dialog.
     */
    @FXML
    private lateinit var helpButton: IconButton

    /**
     * The pane containing the help button.
     */
    @FXML
    private lateinit var buttonPane: AnchorPane

    /**
     * A property for [helpText].
     */
    val helpTextProperty: Property<String> = SimpleObjectProperty("")

    /**
     * The text to display in the help dialog.
     */
    var helpText: String by helpTextProperty

    /**
     * A property for [position].
     */
    val positionProperty: Property<AnchorPoint> = SimpleObjectProperty()

    /**
     * The position along the edge of the screen to anchor the help button to.
     */
    var position: AnchorPoint by positionProperty

    init {
        loadFxml(this, "/fxml/controls/HelpPane.fxml")
    }

    @FXML
    fun initialize() {
        positionProperty.addListener { _, _, newValue ->
            setTopAnchor(helpButton, null)
            setBottomAnchor(helpButton, null)
            setLeftAnchor(helpButton, null)
            setRightAnchor(helpButton, null)

            when (newValue) {
                AnchorPoint.TOP -> {
                    setTopAnchor(helpButton, 0.0)
                    setLeftAnchor(helpButton, 0.0)
                    setRightAnchor(helpButton, 0.0)
                }
                AnchorPoint.BOTTOM -> {
                    setBottomAnchor(helpButton, 0.0)
                    setLeftAnchor(helpButton, 0.0)
                    setRightAnchor(helpButton, 0.0)
                }
                AnchorPoint.LEFT -> {
                    setLeftAnchor(helpButton, 0.0)
                    setTopAnchor(helpButton, 0.0)
                    setBottomAnchor(helpButton, 0.0)
                }
                AnchorPoint.RIGHT -> {
                    setRightAnchor(helpButton, 0.0)
                    setTopAnchor(helpButton, 0.0)
                    setBottomAnchor(helpButton, 0.0)
                }
                AnchorPoint.TOP_LEFT -> {
                    setTopAnchor(helpButton, 0.0)
                    setLeftAnchor(helpButton, 0.0)
                }
                AnchorPoint.TOP_RIGHT -> {
                    setTopAnchor(helpButton, 0.0)
                    setRightAnchor(helpButton, 0.0)
                }
                AnchorPoint.BOTTOM_LEFT -> {
                    setBottomAnchor(helpButton, 0.0)
                    setLeftAnchor(helpButton, 0.0)
                }
                AnchorPoint.BOTTOM_RIGHT -> {
                    setBottomAnchor(helpButton, 0.0)
                    setRightAnchor(helpButton, 0.0)
                }
                else -> Unit
            }
        }

        position = AnchorPoint.TOP_RIGHT
    }

    @FXML
    fun openHelp() {
        infoDialog(title = "Help", text = helpText).dialog.show(scene.root as StackPane)
    }

    override fun layoutChildren() {
        super.layoutChildren()
        children.remove(buttonPane)
        children.add(buttonPane)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HelpPane) return false
        return position == other.position && helpText == other.helpText
    }

    override fun hashCode(): Int = Objects.hash(position, helpText)
}
