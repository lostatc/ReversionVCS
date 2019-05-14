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
import java.awt.Desktop
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.format.FormatStyle
import java.util.ResourceBundle

class VersionSelectController : Initializable, CoroutineScope by MainScope() {
    /**
     * The current working directory.
     */
    private var workDirectory: WorkDirectory? = null

    /**
     * The versions currently visible in the UI.
     */
    private val versions: ObservableList<Version> = FXCollections.observableArrayList()

    /**
     * The index of the currently selected version or `null` if there is none.
     */
    private val selectedIndex: Int?
        get() = versionList.selectionModel.selectedIndex.let { if (it < 0) null else it }

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

        // Reload version information when the selected version changes.
        versionList.selectionModel.selectedIndexProperty().addListener { _, _, _ ->
            updateVersionInfo()
        }

        // Make the version info invisible until a version is selected.
        versionInfoPane.isVisible = false
    }

    /**
     * Updates [versions] with the versions of the given [path].
     */
    private suspend fun loadVersions(path: Path) {
        val newWorkDirectory = withContext(Dispatchers.IO) {
            WorkDirectory.openFromDescendant(path)
        }

        val newVersions = withContext(Dispatchers.IO) {
            val relativePath = newWorkDirectory.path.relativize(path)
            newWorkDirectory.timeline.listVersions(relativePath)
        }

        workDirectory = newWorkDirectory
        versions.setAll(newVersions)
    }

    /**
     * Updates the UI to display information about the currently selected version.
     */
    @FXML
    fun updateVersionInfo() {
        val version = selectedVersion

        if (version == null) {
            versionInfoPane.isVisible = false
            return
        }

        versionInfoPane.isVisible = true
        versionNameField.text = version.snapshot.name
        versionDescriptionField.text = version.snapshot.description
        versionModifiedLabel.text = version.lastModifiedTime.toInstant().format(FormatStyle.MEDIUM)
        versionSizeLabel.text = FileUtils.byteCountToDisplaySize(version.size)
        versionPinnedCheckBox.isSelected = version.snapshot.pinned
    }

    /**
     * Saves information from the UI about the currently selected version.
     */
    @FXML
    fun saveVersionInfo() {
        val version = selectedVersion ?: return

        val name = versionNameField.text?.let { if (it.isEmpty()) null else it }
        val description = versionDescriptionField.text ?: ""
        val pinned = versionPinnedCheckBox.isSelected

        launch {
            withContext(Dispatchers.IO) {
                version.snapshot.apply {
                    this.name = name
                    this.description = description
                    this.pinned = pinned
                }
            }

            versionList.refresh()
        }
    }

    /**
     * Loads versions by browsing for a file.
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
     * Loads versions by selecting a file path.
     */
    @FXML
    fun setPath() {
        val file = Paths.get(pathField.text)
        launch { loadVersions(file) }
    }

    /**
     * Open the selected version in its default application.
     */
    @FXML
    fun openVersion() {
        val version = selectedVersion ?: return

        launch(Dispatchers.IO) {
            val tempDirectory = Files.createTempDirectory("reversion-")
            val targetPath = tempDirectory.resolve(version.path.fileName)
            version.checkout(targetPath)
            Desktop.getDesktop().open(targetPath.toFile())
        }
    }

    /**
     * Restores the currently selected version to its original location.
     *
     * This saves the current version of the file and then overwrites it.
     */
    @FXML
    fun restoreVersion() {
        val workDirectory = workDirectory ?: return
        val version = selectedVersion ?: return
        val targetPath = workDirectory.path.resolve(version.path)

        launch {
            withContext(Dispatchers.IO) {
                // Save the current version of the file.
                workDirectory.commit(
                    listOf(targetPath),
                    description = "This version was created before the file was overwritten by a restore.",
                    pinned = true
                )

                // Restore the old version of the file.
                version.checkout(targetPath, overwrite = true)
            }

            loadVersions(targetPath)
        }
    }

    /**
     * Deletes the currently selected version.
     */
    @FXML
    fun deleteVersion() {
        val workDirectory = workDirectory ?: return
        val version = selectedVersion ?: return
        val targetPath = workDirectory.path.resolve(version.path)

        launch {
            withContext(Dispatchers.IO) {
                version.snapshot.removeVersion(version.path)
            }

            loadVersions(targetPath)
        }
    }
}
