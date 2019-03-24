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

package io.github.lostatc.reversion.storage

import io.github.lostatc.reversion.schema.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.time.Instant

/**
 * A snapshot in the timeline.
 */
interface Snapshot {
    /**
     * The revision number of the snapshot.
     *
     * This is unique with respect to other snapshots in the same timeline.
     */
    val revision: Int

    /**
     * The time the snapshot was created.
     */
    val timeCreated: Instant

    /**
     * The timeline this snapshot is a part of.
     */
    val timeline: Timeline

    /**
     * Adds the file with the given [path] to this snapshot and returns it.
     */
    fun addFile(path: Path): File

    /**
     * Removes the file with the given [path] from this snapshot.
     *
     * @return `true` if the file was removed, `false` if it didn't exist.
     */
    fun removeFile(path: Path): Boolean

    /**
     * Returns the file in this snapshot with the given [path].
     *
     * @return The file or `null` if it doesn't exist.
     */
    fun getFile(path: Path): File?

    /**
     * Returns a sequence of the files in this snapshot.
     *
     * @param [parent] If not `null`, only the files which are descendants of this relative path will be returned.
     */
    fun listFiles(parent: Path? = null): Sequence<File>

    /**
     * Adds a tag to this snapshot and returns it.
     *
     * @param [name] The name of the tag.
     * @param [description] The description of the tag.
     * @param [pinned] Whether the tag should be kept forever.
     */
    fun addTag(name: String, description: String = "", pinned: Boolean = true): Tag

    /**
     * Removes the tag with the given [name] from this snapshot.
     *
     * @return `true` if the tag was removed, `false` if it didn't exist.
     */
    fun removeTag(name: String): Boolean

    /**
     * Returns the tag in this snapshot with the given [name].
     *
     * @return The tag or `null` if it doesn't exist.
     */
    fun getTag(name: String): Tag?

    /**
     * Returns a sequence of the tags that are associated with this snapshot.
     */
    fun listTags(): Sequence<Tag>
}

data class DatabaseSnapshot(val entity: SnapshotEntity) : Snapshot {
    override val revision: Int
        get() = transaction { entity.revision }

    override val timeCreated: Instant
        get() = transaction { entity.timeCreated }

    override val timeline: Timeline
        get() = transaction { DatabaseTimeline(entity.timeline) }

    override fun addFile(path: Path): File {
        TODO("not implemented")
    }

    override fun removeFile(path: Path): Boolean {
        TODO("not implemented")
    }

    override fun getFile(path: Path): File? = transaction {
        FileEntity
            .find { (SnapshotTable.id eq entity.id) and (PathTable.path eq path) }
            .map { DatabaseFile(it) }
            .singleOrNull()
    }

    override fun listFiles(parent: Path?): Sequence<File> = transaction {
        val allFiles = entity.files.asSequence().map { DatabaseFile(it) }

        if (parent == null) {
            allFiles
        } else {
            val descendants = PathEntity
                .find { PathTable.path eq parent }
                .single()
                .descendants
                .map { it.path }
                .toSet()

            allFiles.filter { it.path in descendants }
        }
    }

    override fun addTag(name: String, description: String, pinned: Boolean): Tag = transaction {
        val tag = TagEntity.new {
            this.name = name
            this.description = description
            this.pinned = pinned
            this.snapshot = entity
        }

        DatabaseTag(tag)
    }

    override fun removeTag(name: String): Boolean = transaction {
        val tagEntity = TagEntity
            .find { (TagTable.timeline eq entity.timeline.id) and (TagTable.name eq name) }
            .singleOrNull()

        if (tagEntity == null) {
            false
        } else {
            tagEntity.delete()
            true
        }
    }

    override fun getTag(name: String): Tag? = transaction {
        TagEntity
            .find { (TagTable.timeline eq entity.timeline.id) and (TagTable.name eq name) }
            .map { DatabaseTag(it) }
            .singleOrNull()
    }

    override fun listTags(): Sequence<Tag> = transaction {
        entity.tags.asSequence().map { DatabaseTag(it) }
    }
}