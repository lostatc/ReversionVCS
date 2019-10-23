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

import io.github.lostatc.reversion.DEFAULT_PROVIDER
import io.github.lostatc.reversion.api.getValue
import io.github.lostatc.reversion.api.setValue
import io.github.lostatc.reversion.api.storage.CleanupPolicy
import io.github.lostatc.reversion.api.storage.OpenAttempt
import io.github.lostatc.reversion.daemon.WatchDaemon
import io.github.lostatc.reversion.gui.ActorEvent
import io.github.lostatc.reversion.gui.StateWrapper
import io.github.lostatc.reversion.gui.models.StorageModel.storageActor
import io.github.lostatc.reversion.gui.ui
import io.github.lostatc.reversion.gui.wrap
import io.github.lostatc.reversion.storage.IgnoreMatcher
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The default cleanup policies to use for new working directories.
 */
private val defaultPolicies: Set<CleanupPolicy> = setOf(
    CleanupPolicy.ofStaggered(1, ChronoUnit.SECONDS),
    CleanupPolicy.ofStaggered(60, ChronoUnit.MINUTES),
    CleanupPolicy.ofStaggered(24, ChronoUnit.HOURS),
    CleanupPolicy.ofStaggered(30, ChronoUnit.DAYS),
    CleanupPolicy.ofStaggered(52, ChronoUnit.WEEKS)
)

/**
 * Mutable state associated with a working directory.
 *
 * @param [workDirectory] The working directory.
 */
data class WorkDirectoryState(val workDirectory: WorkDirectory)

/**
 * The model for storing information about the currently selected working directory.
 *
 * @param [workDirectory] The working directory this model represents.
 */
class WorkDirectoryModel(
    private val workDirectory: WorkDirectory
) : CoroutineScope by MainScope(),
    StateWrapper<TaskType, WorkDirectoryState> by storageActor.wrap(WorkDirectoryState(workDirectory)) {

    /**
     * The [CleanupPolicy] objects associated with this [WorkDirectory].
     */
    val cleanupPolicies: ObservableList<CleanupPolicy> = FXCollections.observableArrayList()

    /**
     * The matchers for files to ignore being displayed in the UI.
     */
    val ignoreMatchers: ObservableList<IgnoreMatcher> = FXCollections.observableArrayList()

    /**
     * A property for [path].
     */
    val pathProperty: Property<Path> = SimpleObjectProperty(workDirectory.path)

    /**
     * The path of the working directory.
     */
    var path: Path by pathProperty

    /**
     * A property for [snapshots].
     */
    val snapshotsProperty: Property<Int?> = SimpleObjectProperty(null)

    /**
     * The number of snapshots in the working directory.
     */
    var snapshots: Int? by snapshotsProperty

    /**
     * A property for [latestVersion].
     */
    val latestVersionProperty: Property<Instant?> = SimpleObjectProperty(null)

    /**
     * The time that the most recent version was created.
     */
    var latestVersion: Instant? by latestVersionProperty

    /**
     * A property for [storageUsed].
     */
    val storageUsedProperty: Property<Long?> = SimpleObjectProperty(null)

    /**
     * The amount of space the repository takes up in bytes.
     */
    var storageUsed: Long? by storageUsedProperty

    /**
     * A property for [storageSaved].
     */
    val storageSavedProperty: Property<Long?> = SimpleObjectProperty(null)

    /**
     * The difference between the total size of all the versions stored in the repository and the amount of space taken
     * up by the repository.
     */
    var storageSaved: Long? by storageSavedProperty

    /**
     * A property for [trackedFiles].
     */
    val trackedFilesProperty: Property<Int?> = SimpleObjectProperty(null)

    /**
     * The number of tracked files in the working directory.
     */
    var trackedFiles: Int? by trackedFilesProperty

    /**
     * A property for [trackingChanges].
     */
    val trackingChangesProperty: Property<Boolean> = SimpleObjectProperty(false)

    /**
     * Whether this working directory is currently tracking changes.
     */
    var trackingChanges: Boolean by trackingChangesProperty

    init {
        // Load the cleanup policies in the UI.
        executeAsync { workDirectory.timeline.cleanupPolicies } ui { cleanupPolicies.addAll(it) }

        // Load the ignored path list in the UI.
        executeAsync { workDirectory.readMatchers() } ui { ignoreMatchers.addAll(it) }

        // Set whether the working directory is tracking changes.
        executeAsync { WatchDaemon.tracked.contains(path) } ui { trackingChanges = it }

        // Load statistics about the working directory.
        updateStatistics()

        // Update the working directory statistics whenever a change is made.
        actor.addEventHandler(ActorEvent.TASK_COMPLETED) { key ->
            // Only update the UI if the event was not triggered by another UI update.
            if (key != TaskType.HANDLER) {
                updateStatistics()
            }
        }

        // Update the working directory whenever a cleanup policy is added or removed.
        cleanupPolicies.addListener(
            ListChangeListener<CleanupPolicy> { change ->
                execute { workDirectory.timeline.cleanupPolicies = change.list.toSet() }
            }
        )

        // Update the working directory whenever an ignored path is added or removed.
        ignoreMatchers.addListener(
            ListChangeListener<IgnoreMatcher> { change ->
                execute { workDirectory.writeMatchers(change.list) }
            }
        )
    }

    /**
     * Updates the values of the statistics in this model.
     */
    private fun updateStatistics() {
        executeAsync(TaskType.HANDLER) { workDirectory.timeline.snapshots.size } ui { snapshots = it }
        executeAsync(TaskType.HANDLER) { workDirectory.timeline.latestSnapshot?.timeCreated } ui { latestVersion = it }
        executeAsync(TaskType.HANDLER) { workDirectory.repository.storedSize } ui { storageUsed = it }
        executeAsync(TaskType.HANDLER) {
            workDirectory.repository.totalSize - workDirectory.repository.storedSize
        } ui { storageSaved = it }
        executeAsync(TaskType.HANDLER) { workDirectory.listFiles().size } ui { trackedFiles = it }
    }

    /**
     * Set whether to track changes for this working directory to [value].
     */
    fun setTrackChanges(value: Boolean) {
        trackingChanges = value

        launch {
            if (trackingChanges) {
                WatchDaemon.tracked.add(path)
            } else {
                WatchDaemon.tracked.remove(path)
            }
        }
    }

    companion object {
        /**
         * Returns a new [WorkDirectoryModel] for the working directory with the given [path].
         *
         * If there is not working directory at [path], a new one is created.
         *
         * @param [path] The path of the working directory.
         */
        suspend fun fromPath(path: Path): OpenAttempt<WorkDirectoryModel> = withContext(Dispatchers.IO) {
            if (WorkDirectory.isWorkDirectory(path)) {
                WorkDirectory.open(path).wrap { WorkDirectoryModel(it) }
            } else {
                WorkDirectory.init(path, DEFAULT_PROVIDER).run {
                    timeline.cleanupPolicies = defaultPolicies
                    OpenAttempt.Success(WorkDirectoryModel(this))
                }
            }
        }
    }
}
