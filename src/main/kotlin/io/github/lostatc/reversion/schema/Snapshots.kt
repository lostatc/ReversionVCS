/*
 * Copyright 2019 Wren Powell
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
