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
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import kotlinx.coroutines.Job
import java.time.Instant

/**
 * A handler used to show a dialog and return a value based on a user selection.
 */
data class DialogHandle<T>(val dialog: JFXDialog, private val result: () -> T) {
    fun show(pane: StackPane): T {
        dialog.show(pane)
        return result()
    }

    companion object {
        /**
         * Returns a new [DialogHandle] with a return type of [Unit].
         */
        fun of(dialog: JFXDialog): DialogHandle<Unit> = DialogHandle(dialog) { Unit }
    }
}

/**
 * Creates a new dialog that presents the user with information and prompts them to dismiss it.
 *
 * @param [title] The title of the dialog.
 * @param [text] The message body of the dialog.
 */
fun infoDialog(title: String, text: String): DialogHandle<Unit> {
    val dialog = JFXDialog().apply {
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

    return DialogHandle.of(dialog)
}

/**
 * A dialog which is used to indicate that a [job] is running.
 *
 * The dialog automatically closes when the job is complete, and the dialog has a button for cancelling the job.
 *
 * @param [title] The title of the dialog.
 * @param [job] The job which this dialog is tied to.
 *
 * @return A handle which returns `true` if the job completed normally and `false` if it was cancelled.
 */
fun processingDialog(title: String, job: Job): DialogHandle<Boolean> {
    var finished = true

    val dialog = JFXDialog().apply {
        isOverlayClose = false
        content = JFXDialogLayout().apply {
            heading.add(Label(title))
            body.add(JFXSpinner())
            actions.addAll(
                JFXButton().apply {
                    this.text = "Cancel"
                    this.styleClass.addAll("button-text", "button-regular")
                    this.onAction = EventHandler {
                        job.cancel()
                        finished = false
                        close()
                    }
                }
            )
        }

        job.invokeOnCompletion { close() }
    }

    return DialogHandle(dialog) { finished }
}

/**
 * Creates a new dialog that prompts the user to confirm an action.
 *
 * @param [title] The title of the dialog.
 * @param [text] The message body of the dialog.
 *
 * @return A handle which returns whether the user confirmed the action.
 */
fun confirmationDialog(title: String, text: String): DialogHandle<Boolean> {
    var confirmed = false

    val dialog = JFXDialog().apply {
        content = JFXDialogLayout().apply {
            heading.add(Label(title))
            body.add(Label(text))
            actions.addAll(
                JFXButton().apply {
                    this.text = "No"
                    this.styleClass.addAll("button-text", "button-regular")
                    this.onAction = EventHandler {
                        close()
                    }
                },
                JFXButton().apply {
                    this.text = "Yes"
                    this.styleClass.addAll("button-text", "button-confirm")
                    this.onAction = EventHandler {
                        confirmed = true
                        close()
                    }
                }
            )
        }
    }

    return DialogHandle(dialog) { confirmed }
}

/**
 * Creates a new dialog that prompts the user to confirm an action, where the action could be dangerous.
 *
 * @param [title] The title of the dialog.
 * @param [text] The message body of the dialog.
 *
 * @return A handle which returns whether the user approved the action.
 */
fun approvalDialog(title: String, text: String): DialogHandle<Boolean> {
    var approved = false

    val dialog = JFXDialog().apply {
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
                        approved = true
                        close()
                    }
                }
            )
        }
    }

    return DialogHandle(dialog) { approved }
}

/**
 * Creates a new dialog that prompts the user to choose a date and time.
 *
 * @param [title] The title of the dialog.
 * @param [text] The message body of the dialog.
 * @param [default] The default date and time that is selected in the dialog.
 *
 * @return A handle which returns the date/time chosen by the user or `null` if none was chosen.
 */
fun dateTimeDialog(
    title: String,
    text: String,
    default: Instant? = null
): DialogHandle<Instant?> {
    var result: Instant? = null

    val dialog = JFXDialog().apply {
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
                        result = dateTimePicker.instant
                        close()
                    }
                }
            )
        }
    }

    return DialogHandle(dialog) { result }
}
