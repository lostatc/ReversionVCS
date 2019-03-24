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

import io.github.lostatc.reversion.schema.FileEntity
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Information about the integrity of a repository.
 *
 * @param [corruptFiles] The set of files in the repository which are corrupt.
 */
data class IntegrityReport(val corruptFiles: Set<File>) {
    /**
     * Whether the repository is valid (not corrupt).
     */
    val isValid: Boolean
        get() = corruptFiles.isEmpty()
}

/**
 * A repository where version history is stored.
 */
interface Repository {
    /**
     * The absolute path of the repository.
     */
    val path: Path

    /**
     * Creates a new timeline in this repository and returns it.
     *
     * @param [name] The name of the new timeline.
     */
    fun createTimeline(name: String): Timeline

    /**
     * Removes a timeline from the repository.
     *
     * This deletes the timeline and all its snapshots, files and tags.
     *
     * @return `true` if the timeline was deleted, `false` if it didn't exist.
     */
    fun removeTimeline(name: String): Boolean

    /**
     * Returns the timeline with the given [name].
     */
    fun getTimeline(name: String): Timeline

    /**
     * Returns the timeline with the given [id].
     */
    fun getTimeline(id: UUID): Timeline

    /**
     * Returns a sequence of timelines stored in the repository.
     */
    fun listTimelines(): Sequence<Timeline>

    /**
     * Verifies the integrity of the repository.
     */
    fun verify(): IntegrityReport

}

/**
 * An exception which is thrown when the format of a repository isn't supported by the storage provider.
 *
 * @param [version] The version of the repository.
 * @param [message] A message describing the exception
 */
class UnsupportedFormatException(val version: UUID, message: String? = null) : IllegalArgumentException(message)

data class DatabaseRepository(override val path: Path) : Repository {
    /**
     * The path of the repository's database.
     */
    private val databasePath = path.resolve("manifest.db")

    /**
     * The path of the repository's format version.
     */
    private val versionPath = path.resolve("version")

    /**
     * The version of this repository.
     */
    private val version: UUID = UUID.fromString(Files.readString(path))

    init {
        if (version !in supportedVersions) {
            throw UnsupportedFormatException(version)
        }
    }

    /**
     * The connection to the repository's database.
     */
    val db: Database = databases.getOrPut(path) {
        Database.connect(
            "jdbc:postgresql://localhost:12346${databasePath.toUri().path}",
            driver = "org.postgresql.Driver"
        )
    }

    override fun createTimeline(name: String): Timeline {
        TODO("not implemented")
    }

    override fun removeTimeline(name: String): Boolean {
        TODO("not implemented")
    }

    override fun getTimeline(name: String): Timeline {
        TODO("not implemented")
    }

    override fun getTimeline(id: UUID): Timeline {
        TODO("not implemented")
    }

    override fun listTimelines(): Sequence<Timeline> {
        TODO("not implemented")
    }

    override fun verify(): IntegrityReport = IntegrityReport(
        transaction {
            FileEntity
                .all()
                .map { DatabaseFile(it) }
                .filter { !it.isValid() }
                .toSet()
        }
    )

    companion object {
        /**
         * A map of repository paths to their database objects.
         *
         * This is used to prevent a database from being connected to more than once.
         */
        private val databases: MutableMap<Path, Database> = mutableMapOf()

        /**
         * The current version of the repository format.
         */
        private val currentVersion: UUID = UUID.fromString("c0747b1e-4bd2-11e9-a623-bff5824aa175")

        /**
         * The set of versions of the repository format supported by this service provider.
         */
        private val supportedVersions: Set<UUID> = setOf(
            UUID.fromString("c0747b1e-4bd2-11e9-a623-bff5824aa175")
        )
    }
}

