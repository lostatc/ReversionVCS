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
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.Label

/**
 * A builder for creating a [JFXDialog].
 */
class DialogBuilder {
    /**
     * The title of the dialog.
     */
    var title: String? = null

    /**
     * The message to display in the dialog.
     */
    var text: String? = null

    /**
     * The dialog being built.
     */
    private val dialog: JFXDialog = JFXDialog()

    /**
     * The buttons which will be added to the dialog.
     */
    private val buttons: MutableList<JFXButton> = mutableListOf()

    /**
     * Adds a button to the dialog.
     *
     * The button automatically closes the dialog when pressed.
     *
     * @param [text] The text of the button.
     * @param [styleClass] The style classes of the button.
     * @param [action] The function to call when the button is pressed.
     */
    fun button(text: String, styleClass: Collection<String>, action: (ActionEvent) -> Unit = { }) {
        buttons.add(
            JFXButton().apply {
                this.text = text
                this.styleClass.addAll(styleClass)
                this.onAction = EventHandler { event ->
                    action(event)
                    dialog.close()
                }
            }
        )
    }

    /**
     * Build the dialog.
     */
    fun build(): JFXDialog = dialog.apply {
        content = JFXDialogLayout().apply {
            heading.add(Label(title))
            body.add(Label(text))
            actions.addAll(buttons)
        }
    }
}

/**
 * Creates a new [JFXDialog] for prompting the user.
 */
fun dialog(block: DialogBuilder.() -> Unit): JFXDialog {
    val builder = DialogBuilder()
    builder.block()
    return builder.build()
}
