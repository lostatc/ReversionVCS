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
import io.github.lostatc.reversion.gui.FlushableActor
import io.github.lostatc.reversion.gui.flushableActor
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import java.nio.file.Path
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
typealias WorkDirectoryOperation = WorkDirectoryOperationContext.() -> Unit

/**
 * The model for storing information about the currently selected working directory.
 */
class WorkDirectoryModel(private val workDirectory: WorkDirectory) : CoroutineScope by MainScope() {
    /**
     * An actor to send storage operations to.
     */
    private val actor: FlushableActor<WorkDirectoryOperation> = flushableActor(context = Dispatchers.IO) { operation ->
        WorkDirectoryOperationContext(workDirectory).operation()
    }

    /**
     * The path of the working directory.
     */
    val path: Path = workDirectory.path

    /**
     * The [CleanupPolicy] objects associated with this [WorkDirectory].
     */
    val cleanupPolicies: ObservableList<CleanupPolicy> =
        FXCollections.observableArrayList(workDirectory.timeline.cleanupPolicies)

    init {
        // Update the working directory whenever a cleanup policy is added or removed.
        cleanupPolicies.addListener(
            ListChangeListener<CleanupPolicy> { change ->
                execute { workDirectory.timeline.cleanupPolicies = change.list.toSet() }
            }
        )
    }

    /**
     * Queue up a change to the working directory to be completed asynchronously.
     */
    fun execute(operation: WorkDirectoryOperation) {
        actor.sendBlocking(operation)
    }

    /**
     * Adds a [CleanupPolicy] to this working directory.
     *
     * @throws [IllegalArgumentException] The [amount] is specified without a [unit] of vice versa.
     */
    fun addCleanupPolicy(versions: Int?, amount: Long?, unit: TemporalUnit?) {
        val policyFactory = workDirectory.repository.policyFactory

        val policy = if (unit == null && amount == null) {
            if (versions == null) {
                policyFactory.forever()
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
            throw IllegalArgumentException("The amount of time and unit of time must be specified together.")
        }

        cleanupPolicies.add(policy)
        execute { workDirectory.timeline.cleanupPolicies += policy }
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
