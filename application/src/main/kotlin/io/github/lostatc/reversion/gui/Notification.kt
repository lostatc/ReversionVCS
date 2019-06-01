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

import com.jfoenix.controls.JFXSnackbar
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.util.Duration

/**
 * A popup notification.
 *
 * @param [message] The message to display.
 */
abstract class Notification(val message: String) : HBox() {
    /**
     * The label which displays the message.
     */
    @FXML
    private lateinit var messageLabel: Label

    init {
        FXMLLoader(this::class.java.getResource("/fxml/Notification.fxml")).apply {
            setRoot(this@Notification)
            setController(this@Notification)
            load()
        }

        messageLabel.text = message
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
    val notification = object : Notification(message) {
        override fun close(event: MouseEvent) {
            close()
        }
    }
    enqueue(JFXSnackbar.SnackbarEvent(notification, timeout, null))
}
