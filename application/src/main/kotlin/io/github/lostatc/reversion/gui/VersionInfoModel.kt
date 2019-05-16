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
import javafx.beans.property.Property
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.nio.file.attribute.FileTime

/**
 * A model for storing information about an in individual version.
 */
class VersionInfoModel : CoroutineScope by MainScope() {
    /**
     * A property for [name].
     */
    val nameProperty: Property<String?> = SimpleStringProperty(null)

    /**
     * The name of the version.
     */
    var name: String? by nameProperty

    /**
     * A property for [description].
     */
    val descriptionProperty: Property<String?> = SimpleStringProperty(null)

    /**
     * The description of the version.
     */
    var description: String? by descriptionProperty

    /**
     * A property for [pinned].
     */
    val pinnedProperty: Property<Boolean> = SimpleBooleanProperty()

    /**
     * Whether the version is pinned.
     */
    var pinned: Boolean by pinnedProperty

    /**
     * A property for [lastModified].
     */
    val lastModifiedProperty: Property<FileTime?> = SimpleObjectProperty(null)

    /**
     * The time the version was last modified.
     */
    var lastModified: FileTime? by lastModifiedProperty

    /**
     * A property for [size].
     */
    val sizeProperty: Property<Long?> = SimpleObjectProperty(null)

    /**
     * The size of the version.
     */
    var size: Long? by sizeProperty

    /**
     * Sets the values of the properties in this model from the given [version].
     */
    fun load(version: Version) {
        name = version.snapshot.name
        description = version.snapshot.description
        pinned = version.snapshot.pinned
        lastModified = version.lastModifiedTime
        size = version.size
    }

    /**
     * Saves the values of the properties in this model to the given [version].
     */
    fun save(version: Version) {
        val name = name?.let { if (it.isEmpty()) null else it }
        val description = description ?: ""
        val pinned = pinned

        launch(Dispatchers.IO) {
            version.snapshot.name = name
            version.snapshot.description = description
            version.snapshot.pinned = pinned
        }
    }
}
