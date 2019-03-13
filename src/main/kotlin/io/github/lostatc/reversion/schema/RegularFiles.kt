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

/**
 * A table for storing regular files in the timeline.
 */
object RegularFiles : IntIdTable() {
    /**
     * The metadata associated with this file.
     */
    val file: Column<EntityID<Int>> = reference("file", Files).uniqueIndex()
}

/**
 * A regular file in the timeline.
 */
class RegularFile(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The metadata associated with this file.
     */
    var file: File by File referencedOn RegularFiles.file

    /**
     * The binary objects that make up this file.
     */
    var blobs: SizedIterable<Blob> by Blob via RegularFileBlobs

    companion object : IntEntityClass<RegularFile>(RegularFiles)
}