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
 * A table for storing metadata associated with binary objects in the timeline.
 */
object Blobs : IntIdTable() {
    /**
     * The hexadecimal SHA-256 checksum of the binary object.
     */
    val checksum: Column<String> = varchar("checksum", 64).uniqueIndex()

    /**
     * The size of the binary object in bytes.
     */
    val size: Column<Long> = long("size")
}

/**
 * The metadata associated with a binary object in the timeline.
 */
class Blob(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The hexadecimal SHA-256 checksum of the binary object.
     */
    var checksum: String by Blobs.checksum

    /**
     * The size of the binary object in bytes.
     */
    var size: Long by Blobs.size

    /**
     * The regular files that this blob is a part of.
     */
    var regularFiles: SizedIterable<RegularFile> by RegularFile via RegularFileBlobs

    companion object : IntEntityClass<Blob>(Blobs)
}