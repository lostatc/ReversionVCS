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
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

/**
 * A table for storing the relationships between regular files and binary objects.
 */
object RegularFileBlobs : Table() {
    /**
     * A regular file in the timeline.
     */
    val regularFile: Column<EntityID<Int>> = reference("regularFile", RegularFiles).primaryKey(0)

    /**
     * A binary object that makes up the file.
     */
    val blob: Column<EntityID<Int>> = reference("blob", Blobs).primaryKey(1)

    /**
     * The index of the binary object among all the binary objects that make up the file.
     */
    val index: Column<Int> = integer("index").primaryKey(2)
}
