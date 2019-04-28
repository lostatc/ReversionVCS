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

object TimelineTable : IntIdTable() {
    val name: Column<String> = varchar("name", 255).uniqueIndex()

    val uuid: Column<UUID> = uuid("uuid").uniqueIndex()

    val timeCreated: Column<Instant> = instant("timeCreated")
}

/**
 * Metadata associated with a timeline.
 */
class TimelineEntity(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The unique name of the timeline.
     */
    var name: String by TimelineTable.name

    /**
     * The UUID of the table.
     */
    var uuid: UUID by TimelineTable.uuid

    /**
     * The time the timeline was created.
     */
    var timeCreated: Instant by TimelineTable.timeCreated

    /**
     * The retention policies that are associated with this timeline.
     */
    var retentionPolicies: SizedIterable<RetentionPolicyEntity> by RetentionPolicyEntity via TimelineRetentionPolicyTable

    /**
     * The snapshots that are a part of this timeline.
     */
    val snapshots: SizedIterable<SnapshotEntity> by SnapshotEntity referrersOn SnapshotTable.timeline

    companion object : IntEntityClass<TimelineEntity>(TimelineTable)
}
