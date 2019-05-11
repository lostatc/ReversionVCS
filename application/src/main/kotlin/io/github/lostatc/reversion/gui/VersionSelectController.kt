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
import io.github.lostatc.reversion.cli.format
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
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

class VersionSelectController : Initializable {

    /**
     * The text field containing the path of the file to get versions for.
     */
    @FXML
    private lateinit var pathField: JFXTextField

    /**
     * The list of versions in the UI.
     */
    @FXML
    private lateinit var versionList: JFXListView<VBox>

    /**
     * The message to display if there are no versions.
     */
    @FXML
    private lateinit var noVersionsMessage: VBox

    override fun initialize(location: URL, resources: ResourceBundle?) = Unit

    private fun loadVersions(path: Path) {
        val workDirectory = WorkDirectory.openFromDescendant(path)
        val relativePath = workDirectory.path.relativize(path)
        val versions = workDirectory.timeline.listVersions(relativePath)

        noVersionsMessage.isVisible = false
        versionList.isVisible = true

        versionList.items.setAll(
            versions.map {
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

    @FXML
    fun browsePath(event: ActionEvent) {
        val file = FileChooser().run {
            title = "Select file"
            showOpenDialog(pathField.scene.window)?.toPath() ?: return
        }

        pathField.text = file.toString()
        loadVersions(file)
    }

    @FXML
    fun setPath(event: ActionEvent) {
        val file = Paths.get(pathField.text)
        loadVersions(file)
    }
}
