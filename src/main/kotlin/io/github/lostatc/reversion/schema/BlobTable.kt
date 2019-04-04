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

import io.github.lostatc.reversion.api.Checksum
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SizedIterable

object BlobTable : IntIdTable() {
    val checksum: Column<Checksum> = checksum("checksum").uniqueIndex()

    val size: Column<Long> = long("size")
}

/**
 * A piece of binary data stored in the repository.
 */
class BlobEntity(id: EntityID<Int>) : IntEntity(id) {
    /**
     * The hexadecimal checksum of the binary object.
     */
    var checksum: Checksum by BlobTable.checksum

    /**
     * The size of the binary object in bytes.
     */
    var size: Long by BlobTable.size

    /**
     * The blocks of data which this blob is a part of.
     */
    val blocks: SizedIterable<BlockEntity> by BlockEntity referrersOn BlockTable.blob

    companion object : IntEntityClass<BlobEntity>(BlobTable)
}