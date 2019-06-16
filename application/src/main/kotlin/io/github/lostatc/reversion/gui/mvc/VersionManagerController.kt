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

package io.github.lostatc.reversion.gui.mvc

import com.jfoenix.controls.JFXCheckBox
import com.jfoenix.controls.JFXListView
import com.jfoenix.controls.JFXTextArea
import com.jfoenix.controls.JFXTextField
import io.github.lostatc.reversion.cli.format
import io.github.lostatc.reversion.gui.MappedObservableList
import io.github.lostatc.reversion.gui.controls.Card
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.stage.FileChooser
import kotlinx.coroutines.runBlocking
import org.apache.commons.io.FileUtils
import java.nio.file.Paths
import java.time.format.FormatStyle

/**
 * The controller for the view that is used to manage versions of a file.
 */
class VersionManagerController {

    /**
     * The root node of the view.
     */
    @FXML
    private lateinit var root: StackPane

    /**
     * The field for getting the path of the file to manage versions of.
     */
    @FXML
    private lateinit var pathField: JFXTextField

    /**
     * The list of currently loaded versions.
     */
    @FXML
    private lateinit var versionList: JFXListView<Node>

    /**
     * The pane where version information is displayed.
     */
    @FXML
    private lateinit var infoPane: Pane

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
     * A model for storing information about selected versions.
     */
    private val model: VersionManagerModel =
        VersionManagerModel()

    @FXML
    fun initialize() {
        // Bind the selected version in the model to the selected version in the view.
        versionList.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            model.selected = model.versions.getOrNull(newValue.toInt())
        }

        // Bind the list of versions in the view to the model.
        versionList.items = MappedObservableList(model.versions) {
            Card().apply {
                titleProperty.bind(it.displayNameProperty)
                subtitle = it.timeCreated.format(FormatStyle.MEDIUM)
            }
        }

        // Make the version information pane initially invisible.
        infoPane.isVisible = false

        model.selectedProperty.addListener { _, oldValue, newValue ->

            lastModifiedLabel.text = newValue?.lastModified?.toInstant()?.format(FormatStyle.MEDIUM)

            sizeLabel.text = newValue?.size?.let { FileUtils.byteCountToDisplaySize(it) }

            if (oldValue != null) {
                // Remove bindings between the old [VersionModel] and the view.
                nameField.textProperty().unbindBidirectional(oldValue.nameProperty)
                descriptionField.textProperty().unbindBidirectional(oldValue.descriptionProperty)
                pinnedCheckBox.selectedProperty().unbindBidirectional(oldValue.pinnedProperty)

                // Save the current version info.
                oldValue.saveInfo()
            }

            if (newValue != null) {
                // Add bindings between the new [VersionModel] and the view.
                nameField.textProperty().bindBidirectional(newValue.nameProperty)
                descriptionField.textProperty().bindBidirectional(newValue.descriptionProperty)
                pinnedCheckBox.selectedProperty().bindBidirectional(newValue.pinnedProperty)

                infoPane.isVisible = true
            } else {
                infoPane.isVisible = false
            }
        }
    }

    /**
     * This function is run before the program exits.
     */
    fun cleanup() {
        // Save the information for the currently selected version.
        model.selected?.run {
            saveInfo()
            runBlocking { flush() }
        }
    }

    /**
     * Load versions from the path in the [pathField].
     */
    @FXML
    fun setPath() {
        val file = Paths.get(pathField.text)
        model.loadVersions(file)
    }

    /**
     * Load versions by browsing for a file.
     */
    @FXML
    fun browsePath() {
        val file = FileChooser().run {
            title = "Select file"
            showOpenDialog(pathField.scene.window)?.toPath() ?: return
        }

        pathField.text = file.toString()
        model.loadVersions(file)
    }

    /**
     * Delete the currently selected version.
     */
    @FXML
    fun deleteVersion() {
        model.deleteVersion()
    }

    /**
     * Restore the currently selected version.
     */
    @FXML
    fun restoreVersion() {
        model.restoreVersion()
    }

    /**
     * Open the currently selected version in its default application.
     */
    @FXML
    fun openVersion() {
        model.openVersion()
    }
}
