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

import com.jfoenix.controls.JFXListView
import com.jfoenix.controls.JFXTextField
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Label
import java.net.URL
import java.nio.file.Paths
import java.util.ResourceBundle

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
    private lateinit var versionList: JFXListView<Label>

    override fun initialize(location: URL, resources: ResourceBundle?) = Unit

    @FXML
    fun loadVersions(event: ActionEvent) {
        val path = Paths.get(pathField.text)
        val workDirectory = WorkDirectory.openFromDescendant(path)
        val relativePath = workDirectory.path.relativize(path)
        val versions = workDirectory.timeline.listVersions(relativePath)

        versionList.items.clear()
        versionList.items.addAll(versions.map { Label("Version ${it.snapshot.revision}") })
    }
}
