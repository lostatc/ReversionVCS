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
import io.github.lostatc.reversion.api.Version
import io.github.lostatc.reversion.cli.format
import javafx.fxml.FXML
import javafx.scene.Node
import java.time.format.FormatStyle

class VersionList {
    @FXML
    private lateinit var versionList: JFXListView<Node>

    /**
     * The model for storing information about selected versions.
     */
    val model: VersionListModel = VersionListModel()

    @FXML
    fun initialize() {
        // Bind the selected version in the model to the selected version in the view.
        versionList.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            val index = newValue.toInt()
            model.selectedVersion = if (index < 0) null else model.versions[index]
        }

        // Bind the selected version in the view to the selected version in the model.
        model.selectedVersionProperty.addListener { _, _, newValue ->
            val index = model.versions.indexOf(newValue)
            versionList.selectionModel.select(index)
        }

        // Bind the list of versions to the version list view.
        versionList.items = MappedList(model.versions) {
            ListItem(it.snapshot.displayName, it.snapshot.timeCreated.format(FormatStyle.MEDIUM))
        }
    }

    /**
     * Refresh the version list view to reflect changes in the state of the underlying [Version].
     */
    fun refresh() {
        versionList.refresh()
    }
}
