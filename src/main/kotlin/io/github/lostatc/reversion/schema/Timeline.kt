/*
 * Copyright © 2019 Garrett Powell
 *
 * This file is part of reversion.
 *
 * reversion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * reversion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with reversion.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.lostatc.reversion.schema

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SizedIterable
import org.joda.time.DateTime
import java.util.*

object Timelines : IntIdTable() {
    val name: Column<String> = varchar("name", 255).uniqueIndex()

    val uuid: Column<UUID> = uuid("uuid").uniqueIndex()

    val timeCreated = datetime("timeCreated")
}

/**
 * Metadata associated with a timeline.
 */
class Timeline(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The unique name of the timeline.
     */
    var name: String by Timelines.name

    /**
     * A UUID used for associating working directories with this timeline.
     *
     * While the [name] of the timeline is unique, this is necessary so that the name can be changed without having to
     * update the references in each working directory.
     */
    var uuid: UUID by Timelines.uuid

    /**
     * The time the timeline was created.
     */
    var timeCreated: DateTime by Timelines.timeCreated

    /**
     * The retention policies that are associated with this timeline.
     */
    var retentionPolicies: SizedIterable<RetentionPolicy> by RetentionPolicy via TimelineRetentionPolicies

    /**
     * The files that are a part of this timeline.
     */
    val files: SizedIterable<File> by File referrersOn Files.timeline

    /**
     * The snapshots that are a part of this timeline.
     */
    val snapshots: SizedIterable<Snapshot> by Snapshot referrersOn Snapshots.timeline

    /**
     * The tags that are a part of this timeline.
     */
    val tags: SizedIterable<Tag> by Tag referrersOn Tags.timeline

    companion object : IntEntityClass<Timeline>(Timelines)
}
