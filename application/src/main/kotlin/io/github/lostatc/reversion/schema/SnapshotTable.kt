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
import org.jetbrains.exposed.sql.SizedIterable
import java.time.Instant
import java.util.UUID

object SnapshotTable : IntIdTable() {
    val revision: Column<Int> = integer("revision")

    val timeline: Column<EntityID<UUID>> = cascadeReference("timeline", TimelineTable)

    val timeCreated: Column<Instant> = instant("timeCreated")

    init {
        uniqueIndex(revision, timeline)
    }
}

/**
 * A snapshot in the timeline.
 */
class SnapshotEntity(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The revision number of the snapshot.
     *
     * This is unique with respect to other snapshots in the same timeline.
     */
    var revision: Int by SnapshotTable.revision

    /**
     * The timeline the snapshot is a part of.
     */
    var timeline: TimelineEntity by TimelineEntity referencedOn SnapshotTable.timeline

    /**
     * The time the snapshot was created.
     */
    var timeCreated: Instant by SnapshotTable.timeCreated

    /**
     * The versions of files that are a part of this snapshot.
     */
    val versions: SizedIterable<VersionEntity> by VersionEntity referrersOn VersionTable.snapshot

    /**
     * The tags which are associated with this snapshot.
     */
    val tags: SizedIterable<TagEntity> by TagEntity referrersOn TagTable.snapshot

    companion object : IntEntityClass<SnapshotEntity>(SnapshotTable)
}
