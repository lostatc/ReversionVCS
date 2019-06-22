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

package io.github.lostatc.reversion.gui.mvc

import io.github.lostatc.reversion.api.delete
import io.github.lostatc.reversion.api.deleteIfEmpty
import io.github.lostatc.reversion.gui.MutableStateWrapper
import io.github.lostatc.reversion.gui.getValue
import io.github.lostatc.reversion.gui.mvc.StorageModel.storageActor
import io.github.lostatc.reversion.gui.sendNotification
import io.github.lostatc.reversion.gui.setValue
import io.github.lostatc.reversion.gui.ui
import io.github.lostatc.reversion.gui.wrap
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.transformation.SortedList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.nio.file.Path

/**
 * Mutable state associated with a file in a working directory.
 *
 * @param [path] The path of the file.
 * @param [workDirectory] The working directory that the file is in.
 */
data class FileState(val path: Path, val workDirectory: WorkDirectory) {
    /**
     * Returns a list of [VersionModel] objects representing the versions of this file.
     */
    fun listVersionModels(): List<VersionModel> {
        val relativePath = workDirectory.path.relativize(path)
        val versions = workDirectory.timeline.listVersions(relativePath)
        return versions.map { VersionModel(it, workDirectory) }
    }
}

/**
 * The model for storing information for the [VersionManagerController].
 */
class VersionManagerModel : CoroutineScope by MainScope() {
    /**
     * A property for [selectedFile].
     */
    private val selectedFileProperty: Property<MutableStateWrapper<TaskType, FileState>?> =
        SimpleObjectProperty(null)

    /**
     * Information about the currently selected file.
     */
    private var selectedFile: MutableStateWrapper<TaskType, FileState>? by selectedFileProperty

    /**
     * A property for [selectedVersion].
     */
    val selectedVersionProperty: Property<VersionModel?> = SimpleObjectProperty(null)

    /**
     * A model representing the currently selected version of the [selectedFile].
     */
    var selectedVersion: VersionModel? by selectedVersionProperty

    /**
     * The mutable backing property of [versions].
     */
    private val _versions: ObservableList<VersionModel> = FXCollections.observableArrayList()

    /**
     * A read-only list of the versions currently being displayed in the UI.
     *
     * This list is sorted from most recent to least recent.
     */
    val versions: ObservableList<VersionModel> = SortedList(_versions, compareByDescending { it.revision })

    /**
     * Selects the file with the given [path] and loads versions of it.
     */
    fun setFile(path: Path) {
        selectedFile = storageActor.wrap(FileState(path, WorkDirectory.openFromDescendant(path)))
        loadVersions()
    }

    /**
     * Loads the [versions] of the [selectedFile] and de-selects the selected version.
     */
    fun loadVersions() {
        selectedVersion?.saveInfo()
        selectedVersion = null

        val selectedFile = selectedFile ?: return
        selectedFile.executeAsync {
            listVersionModels()
        } ui {
            _versions.setAll(it)
        }
    }

    /**
     * Loads the [versions] of the [selectedFile].
     */
    fun reloadVersions() {
        val selectedFile = selectedFile ?: return

        selectedFile.executeAsync {
            listVersionModels()
        } ui { newVersions ->
            _versions.retainAll(newVersions)
            _versions.addAll(newVersions - _versions)
        }
    }

    /**
     * Deletes the currently selected version.
     */
    fun deleteVersion() {
        val selected = selectedVersion ?: return

        selected.execute {
            val snapshot = version.snapshot
            version.delete()
            snapshot.deleteIfEmpty()
        }

        _versions.remove(selected)
    }

    /**
     * Restores the currently selected version.
     */
    fun restoreVersion() {
        val selected = selectedVersion ?: return

        selected.executeAsync {
            workDirectory.restore(listOf(path), revision = version.snapshot.revision)
        } ui {
            reloadVersions()
        }
    }

    /**
     * Opens the currently selected version in its default application.
     */
    fun openVersion() {
        selectedVersion?.execute {
            workDirectory.openInApplication(version)
        }
    }

    /**
     * Creates a new version of the [selectedFile].
     */
    fun createVersion() {
        val selected = selectedFile

        if (selected == null) {
            sendNotification("There is no file selected to create a version of.")
            return
        }

        selected.executeAsync {
            workDirectory.commit(listOf(path), force = true)
        } ui {
            reloadVersions()
        }
    }
}
