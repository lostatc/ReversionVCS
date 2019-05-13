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

import com.jfoenix.controls.JFXCheckBox
import com.jfoenix.controls.JFXListView
import com.jfoenix.controls.JFXTextArea
import com.jfoenix.controls.JFXTextField
import io.github.lostatc.reversion.api.Snapshot
import io.github.lostatc.reversion.api.Tag
import io.github.lostatc.reversion.api.Version
import io.github.lostatc.reversion.cli.format
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.time.format.FormatStyle
import java.util.ResourceBundle

/**
 * The tag to display in the UI, or `null` if there is none.
 */
private val Snapshot.primaryTag: Tag?
    get() = tags.values.firstOrNull()

/**
 * The name of the snapshot to display to the user.
 */
private val Snapshot.displayName: String
    get() = primaryTag?.name ?: "Version $revision"

class VersionSelectController : Initializable, CoroutineScope by MainScope() {
    /**
     * The versions currently visible in the UI.
     */
    private val versions: ObservableList<Version> = FXCollections.observableArrayList()

    /**
     * The index of the currently selected version or `null` if there is none.
     */
    private var selectedIndex: Int? = null

    /**
     * The currently selected version.
     */
    private val selectedVersion: Version?
        get() = selectedIndex?.let { versions[it] }

    /**
     * The text field containing the path of the file to get versions for.
     */
    @FXML
    private lateinit var pathField: JFXTextField

    /**
     * The list of versions in the UI.
     */
    @FXML
    private lateinit var versionList: JFXListView<Node>

    /**
     * The pane containing information about the [selectedVersion].
     */
    @FXML
    private lateinit var versionInfoPane: Pane

    /**
     * The text field containing the name of the tag.
     */
    @FXML
    private lateinit var versionNameField: JFXTextField

    /**
     * The text area containing the description of the tag.
     */
    @FXML
    private lateinit var versionDescriptionField: JFXTextArea

    /**
     * The label displaying the last modified time of the version.
     */
    @FXML
    private lateinit var versionModifiedLabel: Label

    /**
     * The label displaying the size of the version.
     */
    @FXML
    private lateinit var versionSizeLabel: Label

    /**
     * The checkbox indicating whether the version is pinned.
     */
    @FXML
    private lateinit var versionPinnedCheckBox: JFXCheckBox

    override fun initialize(location: URL, resources: ResourceBundle?) {
        // Show a placeholder when there are no versions.
        versionList.placeholder = FXMLLoader.load(this::class.java.getResource("/fxml/NoVersionsPlaceholder.fxml"))

        // Set the [selectedIndex] and update the UI when the user selects a version.
        versionList.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            // Save unsaved changes before loading.
            saveVersionInfo()

            val index = newValue.toInt()
            selectedIndex = if (index < 0) null else index

            updateVersionInfo()
        }

        // Map [Version] objects in the model to [Node] objects to display in the view.
        versionList.items = MappedList(versions) {
            VBox(
                Label(it.snapshot.displayName).apply {
                    styleClass.add("card-title")
                },
                Label(it.snapshot.timeCreated.format(FormatStyle.MEDIUM)).apply {
                    styleClass.add("card-subtitle")
                }
            )
        }

        // Make the version info invisible until a version is selected.
        versionInfoPane.isVisible = false

        // Save the contents of the version description text area when it loses focus.
        versionDescriptionField.focusedProperty().addListener { _, _, focused ->
            // TODO: Fix description not being updated when you click to another version.
            if (!focused) saveVersionInfo()
        }
    }

    /**
     * Updates [versions] with the versions of the given [path].
     */
    private suspend fun loadVersions(path: Path) {
        val newVersions = withContext(Dispatchers.Default) {
            val workDirectory = WorkDirectory.openFromDescendant(path)
            val relativePath = workDirectory.path.relativize(path)
            workDirectory.timeline.listVersions(relativePath)
        }

        versions.setAll(newVersions)
    }

    /**
     * Update the UI to display information about the [selectedVersion].
     */
    private fun updateVersionInfo() {
        val version = selectedVersion

        if (version == null) {
            versionInfoPane.isVisible = false
            return
        }

        versionInfoPane.isVisible = true
        versionNameField.text = version.snapshot.primaryTag?.name
        versionDescriptionField.text = version.snapshot.primaryTag?.description
        versionModifiedLabel.text = version.lastModifiedTime.toInstant().format(FormatStyle.MEDIUM)
        versionSizeLabel.text = FileUtils.byteCountToDisplaySize(version.size)
        versionPinnedCheckBox.isSelected = version.snapshot.pinned
    }

    /**
     * Saves version information in the UI to storage.
     */
    @FXML
    fun saveVersionInfo() {
        val index = selectedIndex ?: return
        val version = selectedVersion ?: return

        val tagName = versionNameField.text
        val tagDescription = versionDescriptionField.text ?: ""
        val tagPinned = versionPinnedCheckBox.isSelected

        launch {
            withContext(Dispatchers.Default) {
                version.snapshot.primaryTag?.apply {
                    name = tagName
                    description = tagDescription
                    pinned = tagPinned
                } ?: version.snapshot.addTag(name = tagName, description = tagDescription, pinned = tagPinned)
            }

            versions[index] = version
        }

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
        launch { loadVersions(file) }
    }

    /**
     * Load versions by selecting a file path.
     */
    @FXML
    fun setPath() {
        val file = Paths.get(pathField.text)
        launch { loadVersions(file) }
    }
}
