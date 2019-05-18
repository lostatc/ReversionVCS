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
import com.jfoenix.controls.JFXListView
import com.jfoenix.controls.JFXTextArea
import com.jfoenix.controls.JFXTextField
import io.github.lostatc.reversion.cli.format
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.Pane
import javafx.stage.FileChooser
import org.apache.commons.io.FileUtils
import java.nio.file.Paths
import java.time.format.FormatStyle

class VersionManagerController {
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
    private val listModel: VersionListModel = VersionListModel()

    /**
     * A model for storing information about the currently selected version.
     */
    private val infoModel: VersionInfoModel = VersionInfoModel()

    @FXML
    fun initialize() {
        // Bind the selected version in the [listModel] to the selected version in the view.
        versionList.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            val index = newValue.toInt()
            listModel.selectedVersion = if (index < 0) null else listModel.versions[index]
        }

        // Bind the selected version in the view to the selected version in the [listModel].
        listModel.selectedVersionProperty.addListener { _, _, newValue ->
            val index = listModel.versions.indexOf(newValue)
            versionList.selectionModel.select(index)
        }

        // Set a placeholder node for when the version list is empty.
        versionList.placeholder = FXMLLoader.load(
            this::class.java.getResource("/fxml/NoVersionsPlaceholder.fxml")
        )

        // Bind the list of versions to the [versionList].
        versionList.items = MappedList(listModel.versions) {
            ListItem(it.snapshot.displayName, it.snapshot.timeCreated.format(FormatStyle.MEDIUM))
        }

        // Make the version information pane initially invisible.
        infoPane.isVisible = false

        // Save the current version info and load the new version info whenever a version is selected or de-selected.
        listModel.selectedVersionProperty.addListener { _, oldValue, newValue ->
            oldValue?.let { infoModel.save(it) }

            if (newValue == null) {
                infoPane.isVisible = false
            } else {
                infoPane.isVisible = true
                infoModel.load(newValue)
            }
        }

        // Bind properties bidirectionally between the [infoModel] and view.
        nameField.textProperty().bindBidirectional(infoModel.nameProperty)
        descriptionField.textProperty().bindBidirectional(infoModel.descriptionProperty)
        pinnedCheckBox.selectedProperty().bindBidirectional(infoModel.pinnedProperty)

        // Bind properties in the view to properties in the [infoModel].
        infoModel.lastModifiedProperty.addListener { _, _, newValue ->
            lastModifiedLabel.text = newValue?.toInstant()?.format(FormatStyle.MEDIUM)
        }
        infoModel.sizeProperty.addListener { _, _, newValue ->
            sizeLabel.text = newValue?.let { FileUtils.byteCountToDisplaySize(it) }
        }
    }

    /**
     * This function is run before the program exits.
     */
    fun cleanup() {
        // Save the information for the currently selected version.
        listModel.selectedVersion?.let { infoModel.save(it) }
    }

    @FXML
    fun setPath() {
        val file = Paths.get(pathField.text)
        listModel.selectedVersion?.let { infoModel.save(it) }
        listModel.load(file)
    }

    @FXML
    fun browsePath() {
        val file = FileChooser().run {
            title = "Select file"
            showOpenDialog(pathField.scene.window)?.toPath() ?: return
        }

        pathField.text = file.toString()
        listModel.selectedVersion?.let { infoModel.save(it) }
        listModel.load(file)
    }

    @FXML
    fun deleteVersion() {
        listModel.deleteVersion()
    }

    @FXML
    fun restoreVersion() {
        listModel.restoreVersion()
    }

    @FXML
    fun openVersion() {
        listModel.openVersion()
    }
}
