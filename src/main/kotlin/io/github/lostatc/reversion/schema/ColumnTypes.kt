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

import io.github.lostatc.reversion.api.Checksum
import io.github.lostatc.reversion.api.PermissionSet
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.DateColumnType
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.VarCharColumnType
import org.joda.time.DateTime
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Duration
import java.time.Instant

/**
 * The string used to separate path segments in serialized [Path] objects.
 */
private const val PATH_SEPARATOR: String = "/"

fun <T : Comparable<T>> Table.cascadeReference(name: String, foreign: IdTable<T>): Column<EntityID<T>> =
    reference(name, foreign, ReferenceOption.CASCADE, ReferenceOption.CASCADE)

fun <T : Comparable<T>> Table.cascadeReference(name: String, refColumn: Column<T>): Column<T> =
    reference(name, refColumn, ReferenceOption.CASCADE, ReferenceOption.CASCADE)

/**
 * An [IColumnType] that delegates to another [IColumnType].
 */
interface DelegateColumnType : IColumnType {
    /**
     * The [IColumnType] to delegate to.
     */
    val column: IColumnType

    override var nullable: Boolean
        get() = column.nullable
        set(value) {
            column.nullable = value
        }

    override fun valueFromDB(value: Any): Any

    override fun notNullValueToDB(value: Any): Any

    override fun sqlType(): String = column.sqlType()

    override fun valueToString(value: Any?): String = value?.let { nonNullValueToString(it) } ?: "NULL"

    override fun readObject(rs: ResultSet, index: Int): Any? = column.readObject(rs, index)

    override fun setParameter(stmt: PreparedStatement, index: Int, value: Any?) =
        column.setParameter(stmt, index, value)
}

/**
 * A column type for storing [Path] objects.
 */
class PathColumnType : DelegateColumnType {
    override val column: IColumnType = VarCharColumnType(4096)

    override fun notNullValueToDB(value: Any): Any = column.notNullValueToDB(
        if (value is Path) value.joinToString(separator = PATH_SEPARATOR) else value
    )

    override fun valueFromDB(value: Any): Any {
        if (value is Path) return value

        return column.valueFromDB(value).let {
            if (it is String) {
                val segments = it.split(PATH_SEPARATOR)
                val firstSegment = segments.first()
                val remainingSegments = segments.drop(1).toTypedArray()
                Paths.get(firstSegment, *remainingSegments)
            } else {
                it
            }
        }
    }
}

/**
 * Creates a new column that stores a [Path].
 */
fun Table.path(name: String): Column<Path> = registerColumn(name, PathColumnType())

/**
 * A column type for storing [PermissionSet] objects.
 */
class PosixPermissionsColumnType : DelegateColumnType {
    override val column: IColumnType = VarCharColumnType(9)

    override fun notNullValueToDB(value: Any): Any = column.notNullValueToDB(
        if (value is PermissionSet) value.toString() else value
    )

    override fun valueFromDB(value: Any): Any {
        if (value is PermissionSet) return value
        return column.valueFromDB(value).let {
            if (it is String) PermissionSet.fromString(it) else it
        }
    }
}

/**
 * Creates a new column that stores a [PermissionSet].
 */
fun Table.filePermissions(name: String): Column<PermissionSet> = registerColumn(name, PosixPermissionsColumnType())

/**
 * A column type for storing [Checksum] objects.
 */
class ChecksumColumnType : DelegateColumnType {
    override val column: IColumnType = VarCharColumnType(128)

    override fun notNullValueToDB(value: Any): Any = column.notNullValueToDB(
        if (value is Checksum) value.toHex() else value
    )

    override fun valueFromDB(value: Any): Any {
        if (value is Checksum) return value
        return column.valueFromDB(value).let {
            if (it is String) Checksum.fromHex(it) else it
        }
    }
}

/**
 * Creates a new column that stores a [Checksum].
 */
fun Table.checksum(name: String): Column<Checksum> = registerColumn(name, ChecksumColumnType())

/**
 * A column type for storing [FileTime] objects.
 */
class FileTimeColumnType : DelegateColumnType {
    override val column: IColumnType = DateColumnType(time = true)

    override fun notNullValueToDB(value: Any): Any = column.notNullValueToDB(
        if (value is FileTime) DateTime(value.toMillis()) else value
    )

    override fun valueFromDB(value: Any): Any {
        if (value is FileTime) return value
        return column.valueFromDB(value).let {
            if (it is DateTime) FileTime.fromMillis(it.millis) else it
        }
    }
}

/**
 * Creates a new column that stores a [FileTime].
 */
fun Table.fileTime(name: String): Column<FileTime> = registerColumn(name, FileTimeColumnType())

/**
 * A column type for storing [Duration] objects.
 */
class DurationColumnType : DelegateColumnType {
    override val column: IColumnType = LongColumnType()

    override fun notNullValueToDB(value: Any): Any = column.notNullValueToDB(
        if (value is Duration) value.toMillis() else value
    )

    override fun valueFromDB(value: Any): Any {
        if (value is Duration) return value
        return column.valueFromDB(value).let {
            if (it is Long) Duration.ofMillis(it) else it
        }
    }
}

/**
 * Creates a new column that stores a [Duration].
 */
fun Table.duration(name: String): Column<Duration> = registerColumn(name, DurationColumnType())

/**
 * A column type for storing [Instant] objects.
 */
class InstantColumnType : DelegateColumnType {
    override val column: IColumnType = DateColumnType(time = true)

    override fun notNullValueToDB(value: Any): Any = column.notNullValueToDB(
        if (value is Instant) DateTime(value.toEpochMilli()) else value
    )

    override fun valueFromDB(value: Any): Any {
        if (value is Instant) return value
        return column.valueFromDB(value).let {
            if (it is DateTime) Instant.ofEpochMilli(it.millis) else it
        }
    }
}

/**
 * Creates a new column that stores an [Instant].
 */
fun Table.instant(name: String): Column<Instant> = registerColumn(name, InstantColumnType())
