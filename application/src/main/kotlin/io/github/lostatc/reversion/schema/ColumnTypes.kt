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
import io.github.lostatc.reversion.api.PermissionSet
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.*
import org.joda.time.DateTime
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

private const val PATH_SEPARATOR: String = "/"

fun <T : Comparable<T>> Table.cascadeReference(name: String, foreign: IdTable<T>): Column<EntityID<T>> =
    reference(name, foreign, ReferenceOption.CASCADE, ReferenceOption.CASCADE)

fun <T : Comparable<T>> Table.cascadeReference(name: String, refColumn: Column<T>): Column<T> =
    reference(name, refColumn, ReferenceOption.CASCADE, ReferenceOption.CASCADE)

/**
 * A column type for storing [Path] objects.
 */
class PathColumnType : ColumnType() {
    override fun sqlType(): String = VarCharColumnType(4096).sqlType()

    override fun notNullValueToDB(value: Any): Any =
        if (value is Path) value.joinToString(separator = PATH_SEPARATOR) else value

    override fun valueFromDB(value: Any): Any =
        if (value is String) {
            val segments = value.split(PATH_SEPARATOR)
            val firstSegment = segments.first()
            val remainingSegments = segments.drop(1).toTypedArray()
            Paths.get(firstSegment, *remainingSegments)
        } else {
            value
        }
}

/**
 * Creates a new column that stores a [Path].
 */
fun Table.path(name: String): Column<Path> = registerColumn(name, PathColumnType())

/**
 * A column type for storing [PermissionSet] objects.
 */
class PosixPermissionsColumnType : ColumnType() {
    override fun sqlType(): String = VarCharColumnType(9).sqlType()

    override fun notNullValueToDB(value: Any): Any = if (value is PermissionSet) value.toString() else value

    override fun valueFromDB(value: Any): Any = if (value is String) PermissionSet.fromString(value) else value
}

/**
 * Creates a new column that stores a [PermissionSet].
 */
fun Table.filePermissions(name: String): Column<PermissionSet> = registerColumn(name, PosixPermissionsColumnType())

/**
 * A column type for storing [Checksum] objects.
 */
class ChecksumColumnType : ColumnType() {
    override fun sqlType(): String = VarCharColumnType(64).sqlType()

    override fun notNullValueToDB(value: Any): Any = if (value is Checksum) value.hex else value

    override fun valueFromDB(value: Any): Any = if (value is String) Checksum.fromHex(value) else value
}

/**
 * Creates a new column that stores a [Checksum].
 */
fun Table.checksum(name: String): Column<Checksum> = registerColumn(name, ChecksumColumnType())

/**
 * A column type for storing [FileTime] objects.
 */
class FileTimeColumnType : ColumnType() {
    private val column = DateColumnType(time = true)

    override fun sqlType(): String = column.sqlType()

    override fun notNullValueToDB(value: Any): Any = if (value is FileTime) Timestamp(value.toMillis()) else value

    override fun valueFromDB(value: Any): Any {
        val dateTime = column.valueFromDB(value)
        return if (dateTime is DateTime) FileTime.fromMillis(dateTime.millis) else value
    }
}

/**
 * Creates a new column that stores a [FileTime].
 */
fun Table.fileTime(name: String): Column<FileTime> = registerColumn(name, FileTimeColumnType())

/**
 * A column type for storing [Duration] objects.
 */
class DurationColumnType : ColumnType() {
    override fun sqlType(): String = LongColumnType().sqlType()

    override fun notNullValueToDB(value: Any): Any = if (value is Duration) value.seconds else value

    override fun valueFromDB(value: Any): Any = if (value is Long) Duration.ofSeconds(value) else value
}

/**
 * Creates a new column that stores a [Duration].
 */
fun Table.duration(name: String): Column<Duration> = registerColumn(name, DurationColumnType())

class InstantColumnType : ColumnType() {
    private val column = DateColumnType(time = true)

    override fun sqlType(): String = column.sqlType()

    override fun notNullValueToDB(value: Any): Any = if (value is Instant) Timestamp(value.toEpochMilli()) else value

    override fun valueFromDB(value: Any): Any {
        val dateTime = column.valueFromDB(value)
        return if (dateTime is DateTime) Instant.ofEpochMilli(dateTime.millis) else value
    }
}

/**
 * Creates a new column that stores an [Instant].
 */
fun Table.instant(name: String): Column<Instant> = registerColumn(name, InstantColumnType())
