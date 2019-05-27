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

package io.github.lostatc.reversion.gui

import io.github.lostatc.reversion.api.Version
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.beans.property.Property
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
 * An operation to apply to a [Version] and [WorkDirectory].
 */
typealias VersionOperation = (Version, WorkDirectory) -> Unit

/**
 * A model for storing information about the currently selected version.
 */
// TODO: Don't allow for changing values which should not be changed externally.
// For those, don't use [Property] objects. If you need to add a listener, add a listener to the [VersionModel]
// property itself. You can then add [displayName] and [timeCreated] as properties.
class VersionModel(
    private val version: Version,
    private val workDirectory: WorkDirectory
) : CoroutineScope by MainScope() {

    /**
     * An actor to send database operations to.
     */
    private val actor: FlushableActor<VersionOperation> = flushableActor(context = Dispatchers.IO) { operation ->
        operation(version, workDirectory)
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
    val pinnedProperty: Property<Boolean> = SimpleBooleanProperty(version.snapshot.pinned)

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
     * The name of the snapshot to display to the user.
     */
    val displayName: String = version.snapshot.displayName

    /**
     * The time the snapshot was created.
     */
    val timeCreated: Instant = version.snapshot.timeCreated

    /**
     * Queue up a change to the version to be completed asynchronously.
     */
    // TODO: Use a function with receiver instead of arguments.
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

        execute { version, _ ->
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
