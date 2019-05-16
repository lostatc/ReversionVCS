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

import com.jfoenix.controls.JFXTextField
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.stage.FileChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.nio.file.Paths

class VersionManager : CoroutineScope by MainScope() {
    @FXML
    private lateinit var versionList: VersionList

    @FXML
    private lateinit var versionInfo: VersionInfo

    @FXML
    private lateinit var pathField: JFXTextField

    init {
        FXMLLoader(this::class.java.getResource("/fxml/VersionManager.fxml")).apply {
            setRoot(this)
            setController(this)
            load()
        }
    }

    @FXML
    fun initialize() {
        // Save the current version info and load the new version info whenever a version is selected or de-selected.
        versionList.model.selectedVersionProperty.addListener { _, oldValue, newValue ->
            // TODO: Re-add this if saving in real time doesn't work.
            // oldValue?.let { versionInfo.model.save(it) }
            newValue?.let { versionInfo.model.load(it) }
        }

        // TODO: Test performance.
        // Automatically save changes to the version info when they are made.
        versionInfo.model.nameProperty.addListener { _, _, _ ->
            versionList.model.selectedVersion?.let { versionInfo.model.save(it) }
            versionList.refresh()
        }
        versionInfo.model.descriptionProperty.addListener { _, _, _ ->
            versionList.model.selectedVersion?.let { versionInfo.model.save(it) }
        }
        versionInfo.model.pinnedProperty.addListener { _, _, _ ->
            versionList.model.selectedVersion?.let { versionInfo.model.save(it) }
        }
    }

    @FXML
    fun setPath() {
        val file = Paths.get(pathField.text)
        versionList.model.load(file)
    }

    @FXML
    fun browsePath() {
        val file = FileChooser().run {
            title = "Select file"
            showOpenDialog(pathField.scene.window)?.toPath() ?: return
        }

        pathField.text = file.toString()
        versionList.model.load(file)
    }

    @FXML
    fun deleteVersion() {
        versionList.model.deleteVersion()
    }

    @FXML
    fun restoreVersion() {
        versionList.model.restoreVersion()
    }

    @FXML
    fun openVersion() {
        versionList.model.openVersion()
    }
}
