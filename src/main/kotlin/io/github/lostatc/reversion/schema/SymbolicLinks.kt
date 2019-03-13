/*
 * Copyright 2019 Garrett Powell
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
 * A table for storing symbolic links in the timeline.
 */
object SymbolicLinks : IntIdTable() {
    /**
     * The metadata associated with this symbolic link.
     */
    val file: Column<EntityID<Int>> = reference("file", Files).uniqueIndex()

    /**
     * The path URI of the file this symbolic link points to.
     */
    val target: Column<String> = varchar("target", 4096)
}

/**
 * A symbolic link in the timeline.
 */
class SymbolicLink(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The metadata associated with this symbolic link.
     */
    var file: File by File referencedOn SymbolicLinks.file

    /**
     * The path URI of the file this symbolic link points to.
     */
    var target: String by SymbolicLinks.target

    companion object : IntEntityClass<SymbolicLink>(SymbolicLinks)
}
