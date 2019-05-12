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

import com.jfoenix.controls.JFXListView
import com.jfoenix.controls.JFXTextField
import io.github.lostatc.reversion.api.Snapshot
import io.github.lostatc.reversion.api.Version
import io.github.lostatc.reversion.cli.format
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.time.format.FormatStyle
import java.util.ResourceBundle

/**
 * The name of the snapshot to display to the user.
 */
private val Snapshot.displayName: String
    get() = tags.values.let { if (it.isEmpty()) "Version $revision" else it.joinToString { tag -> tag.name } }

class VersionSelectController : Initializable, CoroutineScope by MainScope() {
    /**
     * The versions currently visible in the UI.
     */
    private val versions: ObservableList<Version> = FXCollections.observableArrayList()

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

    override fun initialize(location: URL, resources: ResourceBundle?) {
        versionList.placeholder = FXMLLoader.load(this::class.java.getResource("/fxml/NoVersionsPlaceholder.fxml"))
    }

    /**
     * Updates [versions] and [versionList] with data from the repository.
     */
    private suspend fun reloadVersions(path: Path) {
        val newVersions = withContext(Dispatchers.Default) {
            val workDirectory = WorkDirectory.openFromDescendant(path)
            val relativePath = workDirectory.path.relativize(path)
            workDirectory.timeline.listVersions(relativePath)
        }

        versions.setAll(newVersions)
        versionList.items.setAll(
            newVersions.map {
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
     * Load versions by browsing for a file.
     */
    @FXML
    fun browsePath(event: ActionEvent) = launch {
        val file = FileChooser().run {
            title = "Select file"
            showOpenDialog(pathField.scene.window)?.toPath() ?: return@launch
        }

        pathField.text = file.toString()
        reloadVersions(file)
    }

    /**
     * Load versions by selecting a file path.
     */
    @FXML
    fun setPath(event: ActionEvent) = launch {
        val file = Paths.get(pathField.text)
        reloadVersions(file)
    }
}
