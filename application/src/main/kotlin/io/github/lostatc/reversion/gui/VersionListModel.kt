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
class VersionListModel : CoroutineScope by MainScope() {
    /**
     * The versions currently being displayed in the UI.
     */
    val versions: ObservableList<Version> = FXCollections.observableArrayList()

    /**
     * A [Property] for [selectedVersion].
     */
    val selectedVersionProperty: Property<Version?> = SimpleObjectProperty(null)

    /**
     * The currently-selected version, or `null` if no version is selected.
     */
    var selectedVersion: Version? by selectedVersionProperty

    /**
     * A property for [workDirectory].
     */
    val workDirectoryProperty: Property<WorkDirectory?> = SimpleObjectProperty(null)

    /**
     * The currently-selected working directory.
     */
    var workDirectory: WorkDirectory? by workDirectoryProperty

    /**
     * The absolute path of the [selectedVersion].
     */
    val versionPath: Path?
        get() = selectedVersion?.let { workDirectory?.path?.resolve(it.path) }

    init {
        // Clear the selected version when the selected working directory changes.
        workDirectoryProperty.addListener { _, oldValue, newValue ->
            if (newValue != oldValue) {
                selectedVersion = null
            }
        }
    }

    /**
     * Sets the values of [versions] and [workDirectory] for the file with the given [path].
     */
    fun load(path: Path) {
        launch {
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
     * Sets the values of [versions] and [workDirectory] for the [selectedVersion].
     */
    fun reload() {
        versionPath?.let { load(it) }
    }

    fun deleteVersion() {
        val workDirectory = workDirectory ?: return
        val version = selectedVersion ?: return

        launch {
            withContext(Dispatchers.IO) {
                version.snapshot.removeVersion(version.path)
            }

            reload()
        }
    }

    fun restoreVersion() {
        val workDirectory = workDirectory ?: return
        val version = selectedVersion ?: return
        val versionPath = versionPath ?: return

        launch {
            withContext(Dispatchers.IO) {
                workDirectory.restore(listOf(versionPath), revision = version.snapshot.revision)
            }

            reload()
        }
    }

    fun openVersion() {
        val workDirectory = workDirectory ?: return
        val version = selectedVersion ?: return

        launch(Dispatchers.IO) {
            workDirectory.openInApplication(version)
        }
    }
}
