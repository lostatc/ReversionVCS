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

import io.github.lostatc.reversion.schema.BlobEntity
import io.github.lostatc.reversion.schema.BlobTable
import io.github.lostatc.reversion.schema.TimelineEntity
import io.github.lostatc.reversion.schema.TimelineTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.zeroturnaround.zip.ZipUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*

/**
 * Information about the integrity of a repository.
 *
 * @param [corruptVersions] The set of files in the repository which are corrupt.
 */
data class IntegrityReport(val corruptVersions: Set<Version>) {
    /**
     * Whether the repository is valid (not corrupt).
     */
    val isValid: Boolean
        get() = corruptVersions.isEmpty()
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
     * @param [policies] The rules which govern how old snapshots in this timeline are cleaned up.
     */
    fun createTimeline(name: String, policies: Set<RetentionPolicy> = setOf()): Timeline

    /**
     * Removes the timeline with the given [name] from the repository.
     *
     * This deletes the timeline and all its snapshots, files and tags.
     *
     * @return `true` if the timeline was deleted, `false` if it didn't exist.
     */
    fun removeTimeline(name: String): Boolean

    /**
     * Removes the timeline with the given [id] from the repository.
     *
     * This deletes the timeline and all its snapshots, files and tags.
     *
     * @return `true` if the timeline was deleted, `false` if it didn't exist.
     */
    fun removeTimeline(id: UUID): Boolean

    /**
     * Returns the timeline with the given [name].
     *
     * @return The timeline or `null` if it doesn't exist.
     */
    fun getTimeline(name: String): Timeline?

    /**
     * Returns the timeline with the given [id].
     *
     * @return The timeline or `null` if it doesn't exist.
     */
    fun getTimeline(id: UUID): Timeline?

    /**
     * Returns a sequence of timelines stored in the repository.
     */
    fun listTimelines(): Sequence<Timeline>

    /**
     * Verifies the integrity of the repository.
     */
    fun verify(): IntegrityReport

    /**
     * Exports the repository to the file at [target].
     *
     * The file created is guaranteed to be importable by [StorageProvider.importRepository].
     *
     * @throws [IOException] An I/O error occurred.
     */
    fun export(target: Path)
}

/**
 * An exception which is thrown when the format of a repository isn't supported by the storage provider.
 *
 * @param [message] A message describing the exception
 */
class UnsupportedFormatException(message: String? = null) : IllegalArgumentException(message)

/**
 * An implementation of [Repository] which is backed by a relational database.
 *
 * @param [path] The path of the repository.
 *
 * @throws [UnsupportedFormatException] The format of the repository at [path] isn't supported.
 */
data class DatabaseRepository(override val path: Path) : Repository {
    /**
     * The path of the repository's database.
     */
    private val databasePath = path.resolve("manifest.db")

    /**
     * The path of the file containing the repository's format version.
     */
    private val versionPath = path.resolve("version")

    /**
     * The path of the directory where blobs are stored.
     */
    private val blobsPath = path.resolve("blobs")

    /**
     * The version of this repository.
     */
    private val version: UUID

    /**
     * The maximum size of the blocks that files stored in this repository are split into.
     */
    val blockSize: Long = Long.MAX_VALUE

    init {
        // Create the repository if it doesn't exist.
        if (Files.notExists(path)) {
            Files.createDirectories(path)
            Files.createDirectory(blobsPath)
            Files.writeString(versionPath, currentVersion.toString())
        }

        // Check if the repository is compatible.
        try {
            version = UUID.fromString(Files.readString(versionPath))
        } catch (e: IOException) {
            throw UnsupportedFormatException("The format version could not be determined.")
        } catch (e: IllegalArgumentException) {
            throw UnsupportedFormatException("The format version could not be determined.")
        }

        if (version !in supportedVersions) {
            throw UnsupportedFormatException("The format of the repository isn't supported.")
        }
    }

    /**
     * The connection to the repository's database.
     */
    val db: Database = databases.getOrPut(path) {
        Database.connect("jdbc:sqlite:${databasePath.toUri().path}", driver = "org.sqlite.JDBC")
    }

    override fun createTimeline(name: String, policies: Set<RetentionPolicy>): DatabaseTimeline = transaction {
        val timeline = DatabaseTimeline(
            TimelineEntity.new {
                this.name = name
                this.uuid = UUID.randomUUID()
                this.timeCreated = Instant.now()
            },
            this@DatabaseRepository
        )
        timeline.retentionPolicies = policies
        timeline
    }

    override fun removeTimeline(name: String): Boolean = transaction {
        val timelineEntity = TimelineEntity.find { TimelineTable.name eq name }.singleOrNull()

        timelineEntity?.delete()
        timelineEntity != null
    }

    override fun removeTimeline(id: UUID): Boolean = transaction {
        val timelineEntity = TimelineEntity.find { TimelineTable.uuid eq id }.singleOrNull()

        timelineEntity?.delete()
        timelineEntity != null
    }

    override fun getTimeline(name: String): DatabaseTimeline? = transaction {
        TimelineEntity
            .find { TimelineTable.name eq name }
            .singleOrNull()
            ?.let { DatabaseTimeline(it, this@DatabaseRepository) }
    }

    override fun getTimeline(id: UUID): DatabaseTimeline? = transaction {
        TimelineEntity
            .find { TimelineTable.uuid eq id }
            .singleOrNull()
            ?.let { DatabaseTimeline(it, this@DatabaseRepository) }
    }

    override fun listTimelines(): Sequence<DatabaseTimeline> = transaction {
        TimelineEntity.all().asSequence().map { DatabaseTimeline(it, this@DatabaseRepository) }
    }

    override fun verify(): IntegrityReport {
        val affectedVersions = mutableSetOf<Version>()

        for (blobEntity in BlobEntity.all()) {
            val blob = getBlob(blobEntity.checksum)

            // Skip the blob if it is valid.
            if (blob != null && blob.checksum == blobEntity.checksum) continue

            // The blob is either missing or corrupt. Find all versions that contain the blob.
            affectedVersions.addAll(blobEntity.blocks.map { DatabaseVersion(it.version, this) })
        }

        return IntegrityReport(affectedVersions)
    }

    /**
     * Returns the storage location of the blob with the given [checksum].
     */
    private fun getBlobPath(checksum: Checksum): Path =
        blobsPath.resolve(checksum.hex.slice(0..1)).resolve(checksum.hex)

    /**
     * Adds the given [blob] to this repository.
     */
    fun addBlob(blob: Blob) {
        // Add the blob to the file system before adding the record to the database to avoid corruption in case this
        // operation is interrupted.
        val blobPath = getBlobPath(blob.checksum)
        Files.createDirectories(blobPath.parent)
        Files.copy(blob.inputStream, blobPath)

        transaction {
            BlobEntity.new {
                checksum = blob.checksum
                size = Files.size(blobPath)
            }
        }
    }

    /**
     * Removes the blob with the given [checksum] from this repository.
     *
     * @return `true` if the blob was removed, `false` if it didn't exist.
     */
    fun removeBlob(checksum: Checksum): Boolean {
        // Remove the record from the database before removing the blob from the file system to avoid corruption in case
        // this operation is interrupted.
        transaction {
            BlobEntity
                .find { BlobTable.checksum eq checksum }
                .singleOrNull()
                ?.delete()
        }

        return Files.deleteIfExists(getBlobPath(checksum))
    }

    /**
     * Returns the blob in this repository with the given [checksum].
     *
     * @return The blob or `null` if it doesn't exist.
     */
    fun getBlob(checksum: Checksum): Blob? {
        val blobPath = getBlobPath(checksum)
        if (Files.notExists(blobPath)) return null
        return Blob.of(Files.newInputStream(blobPath), DatabaseRepository.hashAlgorithm)
    }

    override fun export(target: Path) {
        ZipUtil.pack(path.toFile(), target.toFile())
    }

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

        /**
         * The name of the algorithm used to calculate checksums.
         */
        const val hashAlgorithm: String = "SHA-256"
    }
}

