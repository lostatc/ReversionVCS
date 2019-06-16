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
import io.github.lostatc.reversion.gui.TaskChannel
import io.github.lostatc.reversion.gui.sendNotification
import io.github.lostatc.reversion.gui.taskActor
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.sendBlocking
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
typealias WorkDirectoryOperation<R> = WorkDirectoryOperationContext.() -> R

/**
 * Statistics about a working directory.
 *
 * @param [snapshots] The number of snapshots in the timeline.
 * @param [latestVersion] The time the most recent version was created, or `null` if there are no versions.
 * @param [storageUsed] The amount of space the repository takes up in bytes.
 * @param [storageSaved] The difference between the total size of all the versions stored in the repository and the
 * amount of space taken up by the repository.
 */
data class WorkDirectoryStatistics(
    val snapshots: Int,
    val latestVersion: Instant?,
    val storageUsed: Long,
    val storageSaved: Long
)

/**
 * The model for storing information about the currently selected working directory.
 */
class WorkDirectoryModel(private val workDirectory: WorkDirectory) : CoroutineScope by MainScope() {
    /**
     * A channel to send storage operations to.
     */
    private val taskChannel: TaskChannel = taskActor(context = Dispatchers.IO)

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

    init {
        // Load the cleanup policies in the UI.
        launch {
            cleanupPolicies.addAll(query { workDirectory.timeline.cleanupPolicies })
        }

        // Load the ignored path list in the UI.
        launch {
            ignoredPaths.addAll(query { workDirectory.ignoredPaths })
        }

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
     * Queue up a change to the working directory to be completed asynchronously.
     */
    fun execute(operation: WorkDirectoryOperation<Unit>) {
        taskChannel.sendBlocking { WorkDirectoryOperationContext(workDirectory).operation() }
    }

    /**
     * Request information from the working directory to be returned asynchronously.
     */
    suspend fun <R> query(operation: WorkDirectoryOperation<R>): R =
        taskChannel.sendAsync { WorkDirectoryOperationContext(workDirectory).operation() }.await()

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

    /**
     * Returns statistics for the working directory.
     */
    suspend fun getStatistics(): WorkDirectoryStatistics = query {
        val totalSize = workDirectory.timeline.snapshots.values
            .flatMap { it.versions.values }
            .map { it.size }
            .sum()

        WorkDirectoryStatistics(
            snapshots = workDirectory.timeline.snapshots.size,
            latestVersion = workDirectory.timeline.latestSnapshot?.timeCreated,
            storageUsed = workDirectory.repository.storedSize,
            storageSaved = totalSize - workDirectory.repository.storedSize
        )
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
