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

/**
 * A table for storing tags in the timeline.
 */
object Tags : IntIdTable() {
    /**
     * The name of the tag.
     */
    val name: Column<String> = varchar("name", 255)

    /**
     * The description of the tag.
     */
    val description: Column<String> = text("description")

    /**
     * Whether the snapshot associated with this tag should be kept forever.
     */
    val pinned: Column<Boolean> = bool("pinned")

    /**
     * The snapshot associated with this tag.
     */
    val snapshot: Column<EntityID<Int>> = reference("snapshot", Snapshots)
}

/**
 * A tag in the timeline.
 */
class Tag(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The name of the tag.
     */
    var name: String by Tags.name

    /**
     * The description of the tag.
     */
    var description: String by Tags.description

    /**
     * Whether the snapshot associated with this tag should be kept forever.
     */
    var pinned: Boolean by Tags.pinned

    /**
     * The snapshot associated with this tag.
     */
    var snapshot: Snapshot by Snapshot referencedOn Tags.snapshot

    companion object : IntEntityClass<Tag>(Tags)
}