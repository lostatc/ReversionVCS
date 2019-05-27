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

import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
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
     * A model representing the currently selected version, or `null` if there is no version selected.
     */
    var selected: VersionModel? by selectedProperty

    /**
     * The mutable backing property of [versions].
     */
    private val _versions: ObservableList<VersionModel> = FXCollections.observableArrayList()

    /**
     * A read-only list of the versions currently being displayed in the UI.
     */
    val versions: ObservableList<VersionModel> = FXCollections.unmodifiableObservableList(_versions)

    /**
     * Loads the [versions] of the file with the given [path].
     */
    fun loadVersions(path: Path) {
        launch {
            // Wait for changes to be saved before loading new versions in case the same versions are reloaded.
            selected?.saveInfo()
            selected?.flush()

            selected = null
            _versions.setAll(VersionModel.listVersions(path))
        }
    }

    /**
     * Reloads the [versions] of the [selected] file.
     */
    fun reloadVersions() {
        selected?.let { loadVersions(it.path) }
    }

    /**
     * Deletes the currently selected version.
     */
    fun deleteVersion() {
        selected?.execute { version, _ ->
            version.snapshot.removeVersion(version.path)
            reloadVersions()
        }
    }

    /**
     * Restores the currently selected version.
     */
    fun restoreVersion() {
        val versionPath = selected?.path ?: return
        selected?.execute { version, workDirectory ->
            workDirectory.restore(listOf(versionPath), revision = version.snapshot.revision)
            reloadVersions()
        }
    }

    /**
     * Opens the currently selected version in its default application.
     */
    fun openVersion() {
        selected?.execute { version, workDirectory ->
            workDirectory.openInApplication(version)
        }
    }
}
