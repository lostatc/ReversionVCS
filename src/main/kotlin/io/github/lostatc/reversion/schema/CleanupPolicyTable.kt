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

package io.github.lostatc.reversion.schema

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SizedIterable
import java.time.Duration

object CleanupPolicyTable : IntIdTable() {
    val minInterval: Column<Duration> = duration("minIntervalMilliseconds")

    val timeFrame: Column<Duration> = duration("timeFrameMilliseconds")

    val maxVersions: Column<Int> = integer("maxVersions")

    val description: Column<String> = text("description")
}

/**
 * A rule specifying how old snapshots are cleaned up.
 *
 * For the first [timeFrame] after a snapshot is taken, this policy will only keep [maxVersions] versions of each file
 * for every [minInterval] interval.
 */
class CleanupPolicyEntity(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The minimum interval of time between each group of versions to keep.
     */
    var minInterval: Duration by CleanupPolicyTable.minInterval

    /**
     * The number of seconds into the past during which this policy takes effect.
     */
    var timeFrame: Duration by CleanupPolicyTable.timeFrame

    /**
     * The maximum number of versions of each file to keep within each [minInterval] interval.
     */
    var maxVersions: Int by CleanupPolicyTable.maxVersions

    /**
     * The human-readable description of the policy.
     */
    var description: String by CleanupPolicyTable.description

    /**
     * The timelines that are associated with this cleanup policy.
     */
    var timelines: SizedIterable<TimelineEntity> by TimelineEntity via TimelineCleanupPolicyTable

    companion object : IntEntityClass<CleanupPolicyEntity>(CleanupPolicyTable)
}