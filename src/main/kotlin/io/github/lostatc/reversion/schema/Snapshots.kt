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
import org.joda.time.DateTime

/**
 * A table for storing snapshots in the timeline.
 */
object Snapshots : IntIdTable() {
    /**
     * The time the snapshot was created.
     */
    val timeCreated: Column<DateTime> = datetime("timeCreated")
}

/**
 * A snapshot in the timeline.
 */
class Snapshot(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The time the snapshot was created.
     */
    var timeCreated: DateTime by Snapshots.timeCreated

    /**
     * The files that are a part of this snapshot.
     */
    var files: SizedIterable<File> by File via FileSnapshots

    /**
     * The tags which are associated with this snapshot.
     */
    val tags: SizedIterable<Tag> by Tag referrersOn Tags.snapshot

    companion object : IntEntityClass<Snapshot>(Snapshots)
}
