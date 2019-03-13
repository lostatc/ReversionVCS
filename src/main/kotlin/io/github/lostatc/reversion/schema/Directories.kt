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
 * A table for storing directories in the timeline.
 */
object Directories : IntIdTable() {
    /**
     * The metadata associated with this directory.
     */
    val file: Column<EntityID<Int>> = reference("file", Files).uniqueIndex()
}

/**
 * A directory in the timeline.
 */
class Directory(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The metadata associated with this directory.
     */
    var file: File by File referencedOn Directories.file

    companion object : IntEntityClass<Directory>(Directories)
}
