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

package io.github.lostatc.reversion.gui.mvc

import io.github.lostatc.reversion.DEFAULT_PROVIDER
import io.github.lostatc.reversion.api.CleanupPolicy
import io.github.lostatc.reversion.api.Timeline
import io.github.lostatc.reversion.api.Version
import io.github.lostatc.reversion.gui.TaskChannel
import io.github.lostatc.reversion.gui.getValue
import io.github.lostatc.reversion.gui.sendNotification
import io.github.lostatc.reversion.gui.setValue
import io.github.lostatc.reversion.gui.taskActor
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.TemporalUnit

/**
 * The receiver for a [WorkDirectoryOperation].
 *
 * @param [workDirectory] The working directory to modify.
 */
data class WorkDirectoryOperationContext(val workDirectory: WorkDirectory)

/**
 * An operation to store or retrieve data about a [WorkDirectory].
 */
typealias WorkDirectoryOperation<R> = suspend WorkDirectoryOperationContext.() -> R

/**
 * All the snapshots in the timeline.
 */
private val Timeline.versions: List<Version>
    get() = snapshots.values.flatMap { it.versions.values }

/**
 * The model for storing information about the currently selected working directory.
 */
data class WorkDirectoryModel(private val workDirectory: WorkDirectory) : CoroutineScope by MainScope() {
    /**
     * A channel to send storage operations to.
     */
    private val taskChannel: TaskChannel = taskActor(context = Dispatchers.IO, capacity = Channel.UNLIMITED)

    /**
     * The path of the working directory.
     */
    val path: Path = workDirectory.path

    /**
     * The [CleanupPolicy] objects associated with this [WorkDirectory].
     */
    val cleanupPolicies: ObservableList<CleanupPolicy> = FXCollections.observableArrayList()

    /**
     * The ignored paths being displayed in the UI.
     */
    val ignoredPaths: ObservableList<Path> = FXCollections.observableArrayList()

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

    init {
        // Load the cleanup policies in the UI.
        launch {
            cleanupPolicies.addAll(executeAsync { workDirectory.timeline.cleanupPolicies }.await())
        }

        // Load the ignored path list in the UI.
        launch {
            ignoredPaths.addAll(executeAsync { workDirectory.ignoredPaths }.await())
        }

        // Load statistics about the working directory.
        updateStatistics()

        // Update the working directory whenever a cleanup policy is added or removed.
        cleanupPolicies.addListener(
            ListChangeListener<CleanupPolicy> { change ->
                execute { workDirectory.timeline.cleanupPolicies = change.list.toSet() }
            }
        )

        // Update the working directory whenever an ignored path is added or removed.
        ignoredPaths.addListener(
            ListChangeListener<Path> { change ->
                execute { workDirectory.ignoredPaths = change.list }
            }
        )
    }

    /**
     * Updates the values of the statistics in this model.
     */
    private fun updateStatistics() {
        launch {
            snapshots = executeAsync { workDirectory.timeline.snapshots.size }.await()
        }
        launch {
            latestVersion = executeAsync { workDirectory.timeline.latestSnapshot?.timeCreated }.await()
        }
        launch {
            storageUsed = executeAsync { workDirectory.repository.storedSize }.await()
        }
        launch {
            storageSaved = executeAsync {
                val totalSize = workDirectory.timeline.versions.map { it.size }.sum()
                totalSize - workDirectory.repository.storedSize
            }.await()
        }
        launch {
            trackedFiles = executeAsync { workDirectory.listFiles().size }.await()
        }
    }

    /**
     * Queue up a change to the working directory to be completed asynchronously.
     *
     * @param [operation] The operation to complete asynchronously.
     *
     * @return The job that is running the operation.
     */
    fun execute(operation: WorkDirectoryOperation<Unit>): Job =
        taskChannel.sendBlockingAsync { WorkDirectoryOperationContext(workDirectory).operation() }

    /**
     * Request information from the working directory to be returned asynchronously.
     *
     * @param [operation] The operation to complete asynchronously.
     *
     * @return The deferred output of the operation.
     */
    fun <R> executeAsync(operation: WorkDirectoryOperation<R>): Deferred<R> =
        taskChannel.sendBlockingAsync { WorkDirectoryOperationContext(workDirectory).operation() }

    /**
     * Queue up a change to the working directory and run a function on completion.
     *
     * When the [operation] finishes, its output is passed to [onCompletion].
     *
     * @param [operation] The operation to complete asynchronously.
     * @param [onCompletion] The function to be called when the job completes.
     *
     * @return The job that is running the operation.
     */
    fun <R> executeAndRun(operation: WorkDirectoryOperation<R>, onCompletion: (R) -> Unit): Job {
        val result = executeAsync(operation)
        launch {
            onCompletion(result.await())
        }
        return result
    }

    /**
     * Adds a [CleanupPolicy] to this working directory.
     */
    fun addCleanupPolicy(versions: Int?, amount: Long?, unit: TemporalUnit?) {
        val policyFactory = workDirectory.repository.policyFactory

        val policy = if (unit == null && amount == null) {
            if (versions == null) {
                return
            } else {
                policyFactory.ofVersions(versions)
            }
        } else if (unit != null && amount != null) {
            if (versions == null) {
                policyFactory.ofDuration(amount, unit)
            } else {
                policyFactory.ofUnit(amount, unit, versions)
            }
        } else {
            sendNotification("The amount of time and unit of time must be specified together.")
            return
        }

        cleanupPolicies.add(policy)
    }

    companion object {
        /**
         * Returns a new [WorkDirectoryModel] for the working directory with the given [path].
         *
         * If there is not working directory at [path], a new one is created.
         */
        suspend fun fromPath(path: Path): WorkDirectoryModel = withContext(Dispatchers.IO) {
            val workDirectory = if (WorkDirectory.isWorkDirectory(path)) {
                WorkDirectory.open(path)
            } else {
                WorkDirectory.init(path, DEFAULT_PROVIDER)
            }

            WorkDirectoryModel(workDirectory)
        }
    }
}
