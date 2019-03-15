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

package io.github.lostatc.reversion.schema

import io.github.lostatc.reversion.schema.Versions.file
import io.github.lostatc.reversion.schema.Versions.snapshot
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

/**
 * A table for storing the relationships between files and snapshots.
 *
 * The [file] and [snapshot] must be part of the same timeline.
 */
object Versions : Table() {
    /**
     * The file in the timeline.
     */
    val file: Column<EntityID<Int>> = reference("file", Files).primaryKey(0)

    /**
     * The snapshot in the timeline.
     */
    val snapshot: Column<EntityID<Int>> = reference("snapshot", Snapshots).primaryKey(1)

    /**
     * The timeline [file] is a part of.
     */
    private val fileTimeline: Column<EntityID<Int>> = reference("fileTimeline", Files.timeline)

    /**
     * The timeline [snapshot] is a part of.
     */
    private val snapshotTimeline: Column<EntityID<Int>> = reference("snapshotTimeline", Snapshots.timeline)

    init {
        check { fileTimeline eq snapshotTimeline }
    }
}