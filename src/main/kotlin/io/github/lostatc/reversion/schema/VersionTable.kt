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

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Column

object VersionTable : IntIdTable() {
    val file: Column<EntityID<Int>> = reference("file", FileTable)

    val snapshot: Column<EntityID<Int>> = reference("snapshot", SnapshotTable)

    private val fileTimeline: Column<EntityID<Int>> = reference("fileTimeline", FileTable.timeline)

    private val snapshotTimeline: Column<EntityID<Int>> = reference("snapshotTimeline", SnapshotTable.timeline)

    init {
        uniqueIndex(file, snapshot)
        check { fileTimeline eq snapshotTimeline }
    }
}

/**
 * A version of a file.
 *
 * The [file] and [snapshot] must be part of the same timeline.
 */
class VersionEntity(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The file that this object represents a version of.
     */
    var file: FileEntity by FileEntity referencedOn VersionTable.file

    /**
     * The snapshot containing this version of the file.
     */
    var snapshot: SnapshotEntity by SnapshotEntity referencedOn VersionTable.snapshot

    companion object : IntEntityClass<VersionEntity>(VersionTable)
}
