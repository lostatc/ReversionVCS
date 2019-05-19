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

import io.github.lostatc.reversion.api.Version
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * A model for storing information about which versions are displayed in the UI.
 */
class VersionManagerModel : CoroutineScope by MainScope() {
    /**
     * A property for [selected].
     */
    val selectedProperty: Property<VersionModel?> = SimpleObjectProperty(null)

    /**
     * Information about the currently selected version, or `null` if there is no version selected.
     */
    var selected: VersionModel? by selectedProperty

    /**
     * The currently selected version or `null` if there is no version selected.
     */
    val selectedVersion: Version?
        get() = selected?.version

    /**
     * The absolute path of the currently selected version or `null` if there is no version selected.
     */
    val selectedPath: Path?
        get() = selectedVersion?.path?.let { workDirectory?.path?.resolve(it) }

    /**
     * The versions currently being displayed in the UI.
     */
    val versions: ObservableList<Version> = FXCollections.observableArrayList()

    /**
     * A property for [workDirectory].
     */
    val workDirectoryProperty: Property<WorkDirectory?> = SimpleObjectProperty(null)

    /**
     * The currently-selected working directory, or `null` if none is selected.
     */
    var workDirectory: WorkDirectory? by workDirectoryProperty

    /**
     * Sets the values of [versions] and [workDirectory] for the file with the given [path].
     */
    fun loadVersions(path: Path) {
        launch {
            // TODO: Find an alternative solution that's more obvious and less easy to forget.
            selected = null

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
    }

    /**
     * Sets the values of [versions] and [workDirectory] for the [selected].
     */
    fun reloadVersions() {
        selectedPath?.let { loadVersions(it) }
    }

    /**
     * Deletes the currently selected version.
     */
    fun deleteVersion() {
        val version = selectedVersion ?: return

        launch {
            withContext(Dispatchers.IO) {
                version.snapshot.removeVersion(version.path)
            }

            reloadVersions()
        }
    }

    /**
     * Restores the currently selected version.
     */
    fun restoreVersion() {
        val workDirectory = workDirectory ?: return
        val version = selectedVersion ?: return
        val versionPath = selectedPath ?: return

        launch {
            withContext(Dispatchers.IO) {
                workDirectory.restore(listOf(versionPath), revision = version.snapshot.revision)
            }

            reloadVersions()
        }
    }

    /**
     * Opens the currently selected version in its default application.
     */
    fun openVersion() {
        val workDirectory = workDirectory ?: return
        val version = selected?.version ?: return

        launch(Dispatchers.IO) {
            workDirectory.openInApplication(version)
        }
    }
}
