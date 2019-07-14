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

import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXDialog
import com.jfoenix.controls.JFXDialogLayout
import com.jfoenix.controls.JFXSpinner
import io.github.lostatc.reversion.gui.controls.DateTimePicker
import javafx.event.EventHandler
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import kotlinx.coroutines.Job
import java.time.Instant

/**
 * Creates a new dialog that presents the user with information and prompts them to dismiss it.
 *
 * @param [title] The title of the dialog.
 * @param [text] The message body of the dialog.
 */
fun infoDialog(title: String, text: String): JFXDialog = JFXDialog().apply {
    content = JFXDialogLayout().apply {
        heading.add(Label(title))
        body.add(Label(text))
        actions.addAll(
            JFXButton().apply {
                this.text = "OK"
                this.styleClass.addAll("button-text", "button-confirm")
                this.onAction = EventHandler { close() }
            }
        )
    }
}

/**
 * A dialog which is used to indicate that a [job] is running.
 *
 * The dialog automatically closes when the job is complete, and the dialog has a button for cancelling the job.
 *
 * @param [title] The title of the dialog.
 * @param [job] The job which this dialog is tied to.
 */
fun processingDialog(title: String, job: Job): JFXDialog = JFXDialog().apply {
    isOverlayClose = false
    content = JFXDialogLayout().apply {
        heading.add(Label(title))
        body.add(JFXSpinner())
        actions.addAll(
            JFXButton().apply {
                this.text = "Cancel"
                this.styleClass.addAll("button-text", "button-regular")
                this.onAction = EventHandler {
                    close()
                    job.cancel()
                }
            }
        )
    }

    job.invokeOnCompletion { close() }
}

/**
 * Creates a new dialog that prompts the user to confirm an action.
 *
 * @param [title] The title of the dialog.
 * @param [text] The message body of the dialog.
 * @param [action] The action to perform if the user confirms.
 */
fun confirmationDialog(title: String, text: String, action: () -> Unit): JFXDialog = JFXDialog().apply {
    content = JFXDialogLayout().apply {
        heading.add(Label(title))
        body.add(Label(text))
        actions.addAll(
            JFXButton().apply {
                this.text = "No"
                this.styleClass.addAll("button-text", "button-regular")
                this.onAction = EventHandler { close() }
            },
            JFXButton().apply {
                this.text = "Yes"
                this.styleClass.addAll("button-text", "button-confirm")
                this.onAction = EventHandler {
                    close()
                    action()
                }
            }
        )
    }
}

/**
 * Creates a new dialog that prompts the user to confirm an action, where the action could be dangerous.
 *
 * @param [title] The title of the dialog.
 * @param [text] The message body of the dialog.
 * @param [action] The action to perform if the user confirms.
 */
fun approvalDialog(title: String, text: String, action: () -> Unit): JFXDialog = JFXDialog().apply {
    content = JFXDialogLayout().apply {
        heading.add(Label(title))
        body.add(Label(text))
        actions.addAll(
            JFXButton().apply {
                this.text = "No"
                this.styleClass.addAll("button-text", "button-regular")
                this.onAction = EventHandler { close() }
            },
            JFXButton().apply {
                this.text = "Yes"
                this.styleClass.addAll("button-text", "button-danger")
                this.onAction = EventHandler {
                    close()
                    action()
                }
            }
        )
    }
}

/**
 * Creates a new dialog that prompts the user to choose a date and time.
 *
 * @param [title] The title of the dialog.
 * @param [text] The message body of the dialog.
 * @param [default] The default date and time that is selected in the dialog.
 * @param [action] The action to perform when the user confirms their selection.
 */
fun dateTimeDialog(
    title: String,
    text: String,
    default: Instant? = null,
    action: (Instant?) -> Unit
): JFXDialog = JFXDialog().apply {
    content = JFXDialogLayout().apply {
        val dateTimePicker = DateTimePicker().apply {
            instant = default
        }

        heading.add(Label(title))

        body.add(
            VBox(Label(text), dateTimePicker).apply {
                spacing = 15.0
            }
        )

        actions.addAll(
            JFXButton().apply {
                this.text = "Cancel"
                this.styleClass.addAll("button-text", "button-regular")
                this.onAction = EventHandler { close() }
            },
            JFXButton().apply {
                this.text = "OK"
                this.styleClass.addAll("button-text", "button-confirm")
                this.onAction = EventHandler {
                    close()
                    action(dateTimePicker.instant)
                }
            }
        )
    }
}
