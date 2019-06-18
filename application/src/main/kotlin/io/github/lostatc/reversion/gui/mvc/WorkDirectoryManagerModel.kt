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

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.lostatc.reversion.DATA_DIR
import io.github.lostatc.reversion.gui.getValue
import io.github.lostatc.reversion.gui.sendNotification
import io.github.lostatc.reversion.gui.setValue
import io.github.lostatc.reversion.gui.ui
import io.github.lostatc.reversion.storage.PathTypeAdapter
import io.github.lostatc.reversion.storage.fromJson
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * The model for storing information for the [WorkDirectoryManagerController].
 */
class WorkDirectoryManagerModel : CoroutineScope by MainScope() {
    /**
     * A property for [selected].
     */
    val selectedProperty: Property<WorkDirectoryModel?> = SimpleObjectProperty(null)

    /**
     * A model representing the currently selected working directory, or `null` if there is none selected.
     */
    var selected: WorkDirectoryModel? by selectedProperty

    /**
     * The mutable backing property of [workDirectories].
     */
    private val _workDirectories: ObservableList<WorkDirectoryModel> = FXCollections.observableArrayList()

    /**
     * A read-only list of the working directories currently being displayed in the UI.
     */
    val workDirectories: ObservableList<WorkDirectoryModel> = FXCollections.unmodifiableObservableList(_workDirectories)

    /**
     * Loads the user's [workDirectories] asynchronously.
     */
    fun loadWorkDirectories() {
        launch {
            selected = null
            _workDirectories.setAll(loadWorkPaths().map { WorkDirectoryModel.fromPath(it) })
        }
    }

    /**
     * Adds the work directory with the given [path] asynchronously.
     */
    fun addWorkDirectory(path: Path) {
        launch {
            val model = WorkDirectoryModel.fromPath(path)

            if (model in workDirectories) {
                sendNotification("This directory is already being tracked.")
            } else {
                _workDirectories.add(model)
                saveWorkPaths(workDirectories.map { it.path })
            }
        }
    }

    /**
     * Deletes the selected working directory.
     */
    fun deleteWorkDirectory() {
        val selected = selected ?: return

        selected.executeAsync {
            workDirectory.delete()
        } ui {
            _workDirectories.remove(selected)
            this.selected = null
            saveWorkPaths(workDirectories.map { it.path })
        }
    }

    companion object {
        /**
         * The path of the file which stores the list of the user's working directories.
         */
        private val directoryListFile: Path = DATA_DIR.resolve("directories.json")

        /**
         * The object for serializing/de-serializing objects as JSON.
         */
        private val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .registerTypeHierarchyAdapter(Path::class.java, PathTypeAdapter)
            .create()

        /**
         * Loads the list of the user's working directories from storage.
         */
        private suspend fun loadWorkPaths(): List<Path> = withContext(Dispatchers.IO) {
            if (Files.notExists(directoryListFile)) return@withContext emptyList<Path>()

            Files.newBufferedReader(directoryListFile).use {
                gson.fromJson<List<Path>>(it)
            }
        }

        /**
         * Saves the given [paths] of the user's working directories to storage.
         */
        private suspend fun saveWorkPaths(paths: List<Path>) = withContext(Dispatchers.IO) {
            Files.newBufferedWriter(directoryListFile).use {
                gson.toJson(paths, it)
            }
        }
    }
}
