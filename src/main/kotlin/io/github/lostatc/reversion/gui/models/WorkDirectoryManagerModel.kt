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

package io.github.lostatc.reversion.gui.models

import io.github.lostatc.reversion.DATA_DIR
import io.github.lostatc.reversion.api.getValue
import io.github.lostatc.reversion.api.setValue
import io.github.lostatc.reversion.daemon.WatchDaemon
import io.github.lostatc.reversion.gui.controllers.WorkDirectoryManagerController
import io.github.lostatc.reversion.gui.sendNotification
import io.github.lostatc.reversion.gui.ui
import io.github.lostatc.reversion.storage.SnapshotMounter
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.nio.file.Path
import java.time.Instant

/**
 * The path of the directory to mount the FUSE file system at.
 */
private val MOUNT_DIR: Path = DATA_DIR.resolve("Mounted Directory")

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
     * Adds the given working directory [model] to [workDirectories].
     *
     * This also registers the directory with the [WatchDaemon] so that it is loaded when the program starts.
     */
    suspend fun addWorkDirectory(model: WorkDirectoryModel) {
        _workDirectories.add(model)
        WatchDaemon.registered.add(model.path)
    }

    /**
     * Deletes the selected working directory.
     *
     * This also unregisters the directory with the [WatchDaemon] so that it is not loaded when the program starts.
     */
    fun deleteWorkDirectory() {
        val selected = selected ?: return

        selected.executeAsync {
            workDirectory.delete()
        } ui {
            _workDirectories.remove(selected)
            this.selected = null
            WatchDaemon.registered.remove(selected.path)
        }
    }

    /**
     * Removes the selected working directory from the list without deleting it.
     */
    fun hideWorkDirectory() {
        val selected = selected ?: return

        launch {
            _workDirectories.remove(selected)
            WatchDaemon.registered.remove(selected.path)
        }
    }

    /**
     * Mounts the latest snapshot created before [time] and opens it in the browser.
     */
    fun mountSnapshot(time: Instant) {
        val selected = selected ?: return

        launch {
            val snapshot = selected.executeAsync {
                workDirectory.timeline.snapshots.values.filter { it.timeCreated <= time }.maxBy { it.revision }
            }.await()

            if (snapshot == null) {
                sendNotification("There are no versions that far back in time.")
                return@launch
            }

            withContext(Dispatchers.IO) {
                SnapshotMounter.unmount(MOUNT_DIR)
                SnapshotMounter.mount(snapshot, MOUNT_DIR)

                Desktop.getDesktop().open(MOUNT_DIR.toFile())
            }
        }
    }
}
