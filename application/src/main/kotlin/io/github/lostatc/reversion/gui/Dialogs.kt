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

import com.jfoenix.controls.JFXButton
import com.jfoenix.controls.JFXDialog
import com.jfoenix.controls.JFXDialogLayout
import com.jfoenix.controls.JFXSpinner
import io.github.lostatc.reversion.gui.controls.DateTimePicker
import javafx.event.EventHandler
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import java.time.Instant

/**
 * A handler used to show a dialog and return a value based on a user selection.
 */
data class DialogHandle<T>(val dialog: JFXDialog, val result: Deferred<T>) {
    /**
     * Show the dialog and wait for the user's selection.
     */
    suspend fun prompt(parent: StackPane): T {
        dialog.show(parent)
        return result.await()
    }
}

/**
 * Creates a new dialog that presents the user with information and prompts them to dismiss it.
 *
 * @param [title] The title of the dialog.
 * @param [text] The message body of the dialog.
 *
 * @return A deferred value which completes when the user dismisses the dialog.
 */
fun infoDialog(title: String, text: String): DialogHandle<Unit> {
    val deferred = CompletableDeferred<Unit>()
    val dialog = JFXDialog().apply {

        setOnDialogClosed { deferred.complete(Unit) }

        content = JFXDialogLayout().apply {
            heading.add(Label(title))
            body.add(Label(text))
            actions.addAll(
                JFXButton().apply {
                    this.text = "OK"
                    this.styleClass.addAll("button-text", "button-confirm")
                    this.onAction = EventHandler {
                        deferred.complete(Unit)
                        close()
                    }
                }
            )
        }
    }

    return DialogHandle(dialog, deferred)
}

/**
 * A dialog which is used to indicate that a [job] is running.
 *
 * The dialog automatically closes when the job is complete, and the dialog has a button for cancelling the job.
 *
 * @param [title] The title of the dialog.
 * @param [job] The job which this dialog is tied to.
 *
 * @return A deferred value which yields `true` if the job completes normally or `false` if it cancelled.
 */
fun processingDialog(title: String, job: Job): DialogHandle<Boolean> {
    val deferred = CompletableDeferred<Boolean>()
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
                        deferred.complete(false)
                        close()
                    }
                }
            )
        }

        // Handle the case where the [job] completes before the dialog can be opened.
        setOnDialogOpened {
            if (deferred.isCompleted) close()
        }

        job.invokeOnCompletion {
            deferred.complete(true)
            // Don't attempt to close the dialog unless it's been opened.
            if (dialogContainer != null) close()
        }
    }

    return DialogHandle(dialog, deferred)
}

/**
 * Creates a new dialog that prompts the user to confirm an action.
 *
 * @param [title] The title of the dialog.
 * @param [text] The message body of the dialog.
 *
 * @return A deferred value which yields `true` if user confirms or `false` if they do not.
 */
fun confirmationDialog(title: String, text: String): DialogHandle<Boolean> {
    val deferred = CompletableDeferred<Boolean>()
    val dialog = JFXDialog().apply {

        setOnDialogClosed { deferred.complete(false) }

        content = JFXDialogLayout().apply {
            heading.add(Label(title))
            body.add(Label(text))
            actions.addAll(
                JFXButton().apply {
                    this.text = "No"
                    this.styleClass.addAll("button-text", "button-regular")
                    this.onAction = EventHandler {
                        deferred.complete(false)
                        close()
                    }
                },
                JFXButton().apply {
                    this.text = "Yes"
                    this.styleClass.addAll("button-text", "button-confirm")
                    this.onAction = EventHandler {
                        deferred.complete(true)
                        close()
                    }
                }
            )
        }
    }

    return DialogHandle(dialog, deferred)
}

/**
 * Creates a new dialog that prompts the user to confirm an action, where the action could be dangerous.
 *
 * @param [title] The title of the dialog.
 * @param [text] The message body of the dialog.
 *
 * @return A deferred value which yields `true` if user approves or `false` if they do not.
 */
fun approvalDialog(title: String, text: String): DialogHandle<Boolean> {
    val deferred = CompletableDeferred<Boolean>()
    val dialog = JFXDialog().apply {

        setOnDialogClosed { deferred.complete(false) }

        content = JFXDialogLayout().apply {
            heading.add(Label(title))
            body.add(Label(text))
            actions.addAll(
                JFXButton().apply {
                    this.text = "No"
                    this.styleClass.addAll("button-text", "button-regular")
                    this.onAction = EventHandler {
                        deferred.complete(false)
                        close()
                    }
                },
                JFXButton().apply {
                    this.text = "Yes"
                    this.styleClass.addAll("button-text", "button-danger")
                    this.onAction = EventHandler {
                        deferred.complete(true)
                        close()
                    }
                }
            )
        }
    }

    return DialogHandle(dialog, deferred)
}

/**
 * Creates a new dialog that prompts the user to choose a date and time.
 *
 * @param [title] The title of the dialog.
 * @param [text] The message body of the dialog.
 * @param [default] The default date and time that is selected in the dialog.
 *
 * @return A deferred value which yields the selected time or `null` if none was selected.
 */
fun dateTimeDialog(
    title: String,
    text: String,
    default: Instant? = null
): DialogHandle<Instant?> {
    val deferred = CompletableDeferred<Instant?>()
    val dialog = JFXDialog().apply {

        setOnDialogClosed { deferred.complete(null) }

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
                    this.onAction = EventHandler {
                        deferred.complete(null)
                        close()
                    }
                },
                JFXButton().apply {
                    this.text = "OK"
                    this.styleClass.addAll("button-text", "button-confirm")
                    this.onAction = EventHandler {
                        deferred.complete(dateTimePicker.instant)
                        close()
                    }
                }
            )
        }
    }

    return DialogHandle(dialog, deferred)
}
