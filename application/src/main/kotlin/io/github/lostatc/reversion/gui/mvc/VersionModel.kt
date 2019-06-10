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

import io.github.lostatc.reversion.api.Version
import io.github.lostatc.reversion.gui.FlushableActor
import io.github.lostatc.reversion.gui.flushableActor
import io.github.lostatc.reversion.gui.getValue
import io.github.lostatc.reversion.gui.setValue
import io.github.lostatc.reversion.gui.toMappedProperty
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.beans.property.BooleanProperty
import javafx.beans.property.Property
import javafx.beans.property.ReadOnlyProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant

/**
 * The receiver for a [VersionOperation].
 *
 * @param [version] The version to modify.
 * @param [workDirectory] The working directory that the [version] is a part of.
 * @param [path] The absolute path of the [version].
 */
data class VersionOperationContext(val version: Version, val workDirectory: WorkDirectory, val path: Path)

/**
 * An operation to store or retrieve data about a [Version].
 */
typealias VersionOperation = VersionOperationContext.() -> Unit

/**
 * The model for storing information about the currently selected version.
 */
class VersionModel(
    private val version: Version,
    private val workDirectory: WorkDirectory
) : CoroutineScope by MainScope() {

    /**
     * An actor to send storage operations to.
     */
    private val actor: FlushableActor<VersionOperation> = flushableActor(context = Dispatchers.IO) { operation ->
        VersionOperationContext(version, workDirectory, path).operation()
    }

    /**
     * The absolute path of the [version].
     */
    val path: Path
        get() = workDirectory.path.resolve(version.path)

    /**
     * A property for [name].
     */
    val nameProperty: Property<String?> = SimpleStringProperty(version.snapshot.name)

    /**
     * The name of the version.
     */
    var name: String? by nameProperty

    /**
     * A property for [description].
     */
    val descriptionProperty: Property<String?> = SimpleStringProperty(version.snapshot.description)

    /**
     * The description of the version.
     */
    var description: String? by descriptionProperty

    /**
     * A property for [pinned].
     */
    val pinnedProperty: BooleanProperty = SimpleBooleanProperty(version.snapshot.pinned)

    /**
     * Whether the version is pinned.
     */
    var pinned: Boolean by pinnedProperty

    /**
     * The time the version was last modified.
     */
    val lastModified: FileTime = version.lastModifiedTime

    /**
     * The size of the version.
     */
    val size: Long = version.size

    /**
     * A property for [displayName].
     */
    val displayNameProperty: ReadOnlyProperty<String> =
        nameProperty.toMappedProperty { if (it.isNullOrEmpty()) version.snapshot.defaultName else it }

    /**
     * The name of the snapshot to display to the user.
     */
    val displayName: String by displayNameProperty

    /**
     * The time the snapshot was created.
     */
    val timeCreated: Instant = version.snapshot.timeCreated

    /**
     * Queue up a change to the version to be completed asynchronously.
     */
    fun execute(operation: VersionOperation) {
        actor.sendBlocking(operation)
    }

    /**
     * Suspend and wait for changes applied with [execute] to commit.
     */
    suspend fun flush() {
        actor.flush()
    }

    /**
     * Saves the values of the properties in this model to the [version].
     */
    fun saveInfo() {
        val name = name?.let { if (it.isEmpty()) null else it }
        val description = description ?: ""
        val pinned = pinned

        execute {
            version.snapshot.name = name
            version.snapshot.description = description
            version.snapshot.pinned = pinned
        }
    }

    companion object {
        /**
         * Creates a list of [VersionModel] objects for all the versions of the file with the given [path].
         */
        suspend fun listVersions(path: Path): List<VersionModel> = withContext(Dispatchers.IO) {
            val workDirectory = WorkDirectory.openFromDescendant(path)
            val relativePath = workDirectory.path.relativize(path)
            val versions = workDirectory.timeline.listVersions(relativePath)

            versions.map { VersionModel(it, workDirectory) }
        }
    }
}