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

import io.github.lostatc.reversion.DATA_DIR
import io.github.lostatc.reversion.daemon.STUB_NAME
import io.github.lostatc.reversion.daemon.WatchDaemon
import io.github.lostatc.reversion.daemon.WatchRemote
import io.github.lostatc.reversion.daemon.asDaemon
import io.github.lostatc.reversion.gui.getValue
import io.github.lostatc.reversion.gui.sendNotification
import io.github.lostatc.reversion.gui.setValue
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
import java.nio.file.Files
import java.nio.file.Path
import java.rmi.registry.LocateRegistry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The path of the directory to mount the FUSE file system at.
 */
private val MOUNT_DIR: Path = DATA_DIR.resolve("Directory as of")

/**
 * The time separator character to use in file names where certain characters may not be allowed.
 */
private const val TIME_SEPARATOR: String = "\ua789"

/**
 * Format this instant to a string which can be used in file names.
 */
private fun Instant.formatPathSafe(): String = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH${TIME_SEPARATOR}mm${TIME_SEPARATOR}ss")
    .withZone(ZoneId.systemDefault())
    .format(this)

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
     * The currently-running daemon instance.
     */
    val daemon: WatchDaemon = (LocateRegistry.getRegistry().lookup(STUB_NAME) as WatchRemote).asDaemon()

    /**
     * Loads the user's [workDirectories] asynchronously.
     *
     * @param [handler] A function which is passed the path of a working directory and returns a model representing it,
     * or `null` if that model should be skipped.
     */
    fun loadWorkDirectories(handler: suspend (Path) -> WorkDirectoryModel?) {
        launch {
            selected = null
            for (path in daemon.registered.value) {
                launch { handler(path)?.let { _workDirectories.add(it) } }
            }
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
                daemon.registered.value = workDirectories
                    .map { it.executeAsync { workDirectory.path }.await() }
                    .toSet()
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
            daemon.registered.value = workDirectories
                .map { it.executeAsync { workDirectory.path }.await() }
                .toSet()
        }
    }

    /**
     * Unmounts all snapshots and deletes their mount points.
     */
    private fun unmountAllSnapshots() {
        if (Files.notExists(MOUNT_DIR)) return

        for (directory in Files.list(MOUNT_DIR)) {
            SnapshotMounter.unmount(directory)
            Files.deleteIfExists(directory)
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
                unmountAllSnapshots()
                val mountpoint = MOUNT_DIR.resolve(snapshot.timeCreated.formatPathSafe())

                SnapshotMounter.unmount(mountpoint)
                SnapshotMounter.mount(snapshot, mountpoint)

                Desktop.getDesktop().open(mountpoint.toFile())
            }
        }
    }
}
