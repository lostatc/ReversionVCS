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

import com.jfoenix.controls.JFXSnackbar
import io.github.lostatc.reversion.api.getValue
import io.github.lostatc.reversion.api.loadFxml
import io.github.lostatc.reversion.api.setValue
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.util.Duration

/**
 * A popup notification.
 */
abstract class Notification : HBox() {
    /**
     * The label which displays the message.
     */
    @FXML
    private lateinit var messageLabel: Label

    /**
     * A property for [message].
     */
    val messageProperty: Property<String> = SimpleObjectProperty()

    /**
     * The message to display in the notification.
     */
    var message: String by messageProperty

    init {
        loadFxml(this, "/fxml/controls/Notification.fxml")
    }

    @FXML
    fun initialize() {
        messageLabel.textProperty().bind(messageProperty)
    }

    /**
     * Dismisses the notification.
     */
    @FXML
    protected abstract fun close(event: MouseEvent)
}

/**
 * Sends an error message to the snackbar.
 *
 * @param [message] The message to display.
 * @param [timeout] The amount of time before the notification automatically dismisses itself.
 */
fun JFXSnackbar.sendNotification(message: String, timeout: Duration = Duration.INDEFINITE) {
    val notification = object : Notification() {
        override fun close(event: MouseEvent) {
            close()
        }
    }
    notification.message = message
    enqueue(JFXSnackbar.SnackbarEvent(notification, timeout, null))
}
