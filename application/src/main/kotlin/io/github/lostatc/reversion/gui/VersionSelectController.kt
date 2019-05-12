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
import io.github.lostatc.reversion.api.Snapshot
import io.github.lostatc.reversion.api.Tag
import io.github.lostatc.reversion.api.Version
import io.github.lostatc.reversion.cli.format
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.event.ActionEvent
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
import kotlin.properties.Delegates

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
     *
     * This sets the values in [versionList].
     */
    private var versions: List<Version> = emptyList()
        set(value) {
            field = value

            versionList.items.setAll(
                value.map {
                    VBox(
                        Label(it.snapshot.displayName).apply {
                            styleClass.add("card-title")
                        },
                        Label(it.snapshot.timeCreated.format(FormatStyle.MEDIUM)).apply {
                            styleClass.add("card-subtitle")
                        }
                    )
                }
            )
        }

    /**
     * The currently-selected version.
     */
    private var selectedVersion: Version? by Delegates.observable<Version?>(null) { _, _, newValue ->
        updateVersionInfo(newValue)
    }

    /**
     * The index of the most recently selected version or -1 if there is none.
     */
    private var lastSelectedIndex: Int = -1

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

        // Set the [selectedIndex] and [selectedVersion] when the user selects a version in the UI.
        versionList.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            val index = newValue.toInt()

            if (index < 0) {
                selectedVersion = null
            } else {
                lastSelectedIndex = index
                selectedVersion = versions[index]
            }
        }

        // Make the version info invisible until a version is selected.
        versionInfoPane.isVisible = false

        // Save the contents of the version description text area when it loses focus.
        versionDescriptionField.focusedProperty().addListener { _, _, focused ->
            if (!focused) saveVersionInfo()
        }
    }

    /**
     * Updates [versions] with the versions of the given [path].
     */
    private suspend fun loadVersions(path: Path) {
        versions = withContext(Dispatchers.Default) {
            val workDirectory = WorkDirectory.openFromDescendant(path)
            val relativePath = workDirectory.path.relativize(path)
            workDirectory.timeline.listVersions(relativePath)
        }
    }

    /**
     * Updates [versions] with the current data from the repository.
     */
    private suspend fun reloadVersions() {
        val version = selectedVersion ?: return

        versions = withContext(Dispatchers.Default) {
            version.timeline.listVersions(version.path)
        }

        versionList.selectionModel.select(lastSelectedIndex)
    }

    /**
     * Update the UI to display information about the given [version].
     */
    private fun updateVersionInfo(version: Version?) {
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
    fun saveVersionInfo() = launch {
        val version = selectedVersion ?: return@launch

        val tagName = versionNameField.text
        val tagDescription = versionDescriptionField.text ?: ""
        val tagPinned = versionPinnedCheckBox.isSelected

        withContext(Dispatchers.Default) {
            val primaryTag = version.snapshot.primaryTag

            primaryTag?.apply {
                name = tagName
                description = tagDescription
                pinned = tagPinned
            } ?: version.snapshot.addTag(name = tagName, description = tagDescription, pinned = tagPinned)
        }

        reloadVersions()
    }

    /**
     * Load versions by browsing for a file.
     */
    @FXML
    fun browsePath(event: ActionEvent) = launch {
        val file = FileChooser().run {
            title = "Select file"
            showOpenDialog(pathField.scene.window)?.toPath() ?: return@launch
        }

        pathField.text = file.toString()
        loadVersions(file)
    }

    /**
     * Load versions by selecting a file path.
     */
    @FXML
    fun setPath(event: ActionEvent) = launch {
        val file = Paths.get(pathField.text)
        loadVersions(file)
    }
}
