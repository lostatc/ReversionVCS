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

package io.github.lostatc.reversion.gui.models

import io.github.lostatc.reversion.api.Version
import io.github.lostatc.reversion.gui.StateWrapper
import io.github.lostatc.reversion.gui.getValue
import io.github.lostatc.reversion.gui.models.StorageModel.storageActor
import io.github.lostatc.reversion.gui.setValue
import io.github.lostatc.reversion.gui.toMappedProperty
import io.github.lostatc.reversion.gui.wrap
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.beans.property.Property
import javafx.beans.property.ReadOnlyProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant

/**
 * Mutable state associated with a version of a file.
 *
 * @param [version] The version.
 * @param [workDirectory] The working directory that the [version] is a part of.
 */
data class VersionState(val version: Version, val workDirectory: WorkDirectory) {
    /**
     * The path of the [version].
     */
    val path: Path
        get() = workDirectory.path.resolve(version.path)
}

/**
 * The model for storing information about the currently selected version.
 *
 * @param [version] The version this model represents.
 * @param [workDirectory] The working directory this model is in.
 */
data class VersionModel(
    private val version: Version,
    private val workDirectory: WorkDirectory
) : CoroutineScope by MainScope(),
    StateWrapper<TaskType, VersionState> by storageActor.wrap(VersionState(version, workDirectory)) {
    /**
     * The revision number of the version.
     */
    val revision: Int = version.snapshot.revision

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
    val descriptionProperty: Property<String> = SimpleStringProperty(version.snapshot.description)

    /**
     * The description of the version.
     */
    var description: String by descriptionProperty

    /**
     * A property for [pinned].
     */
    val pinnedProperty: Property<Boolean> = SimpleObjectProperty(version.snapshot.pinned)

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
     * The name to display for a snapshot without a name.
     */
    private val defaultName: String = version.snapshot.defaultName

    /**
     * A property for [displayName].
     */
    val displayNameProperty: ReadOnlyProperty<String> =
        nameProperty.toMappedProperty { if (it.isNullOrEmpty()) defaultName else it }

    /**
     * The name of the snapshot to display to the user.
     */
    val displayName: String by displayNameProperty

    /**
     * The time the snapshot was created.
     */
    val timeCreated: Instant = version.snapshot.timeCreated

    /**
     * Suspend and wait for changes applied with [execute] or [executeAsync] to commit.
     */
    suspend fun flush() {
        storageActor.flush()
    }

    /**
     * Saves the values of the properties in this model to the [Version].
     */
    fun saveInfo() {
        val name = name?.let { if (it.isEmpty()) null else it }
        val description = description
        val pinned = pinned

        execute {
            version.snapshot.name = name
            version.snapshot.description = description
            version.snapshot.pinned = pinned
        }
    }
}
