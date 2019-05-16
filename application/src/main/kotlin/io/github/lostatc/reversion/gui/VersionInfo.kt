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

import com.jfoenix.controls.JFXCheckBox
import com.jfoenix.controls.JFXTextArea
import com.jfoenix.controls.JFXTextField
import io.github.lostatc.reversion.cli.format
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import org.apache.commons.io.FileUtils
import java.time.format.FormatStyle

class VersionInfo : VBox() {
    /**
     * The field for displaying the name of the version.
     */
    @FXML
    private lateinit var nameField: JFXTextField

    /**
     * The field for displaying the version's description.
     */
    @FXML
    private lateinit var descriptionField: JFXTextArea

    /**
     * The check box for displaying whether the version has been pinned.
     */
    @FXML
    private lateinit var pinnedCheckBox: JFXCheckBox

    /**
     * The label for displaying the time the version was last modified.
     */
    @FXML
    private lateinit var lastModifiedLabel: Label

    /**
     * The label for displaying the size of the version.
     */
    @FXML
    private lateinit var sizeLabel: Label

    /**
     * The model for storing information about each version.
     */
    val model: VersionInfoModel = VersionInfoModel()

    init {
        FXMLLoader(this::class.java.getResource("/fxml/VersionInfo.fxml")).apply {
            setRoot(this)
            setController(this)
            load()
        }
    }

    @FXML
    fun initialize() {
        // Bind properties bidirectionally between the model and view.
        nameField.textProperty().bindBidirectional(model.nameProperty)
        descriptionField.textProperty().bindBidirectional(model.descriptionProperty)
        pinnedCheckBox.selectedProperty().bindBidirectional(model.pinnedProperty)

        // Bind properties in the view to properties in the model.
        model.lastModifiedProperty.addListener { _, _, newValue ->
            lastModifiedLabel.text = newValue?.toInstant()?.format(FormatStyle.MEDIUM)
        }
        model.sizeProperty.addListener { _, _, newValue ->
            sizeLabel.text = newValue?.let { FileUtils.byteCountToDisplaySize(it) }
        }
    }
}
