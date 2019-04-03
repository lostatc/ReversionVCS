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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.zeroturnaround.zip.ZipUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.*
import kotlin.streams.asSequence

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
     * The configuration for the repository.
     */
    val config: RepositoryConfig

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
 * A factory for [Database] instances.
 */
private object DatabaseFactory {
    /**
     * A map of database paths to their database objects.
     *
     * This is used to prevent a database from being connected to more than once.
     */
    private val databases: MutableMap<Path, Database> = mutableMapOf()

    /**
     * Connect to the database at the given [path].
     */
    fun connect(path: Path): Database = databases.getOrPut(path) {
        Database.connect("jdbc:sqlite:${path.toUri().path}", driver = "org.sqlite.JDBC")
    }
}

/**
 * An implementation of [Repository] which is backed by a relational database.
 */
data class DatabaseRepository(override val path: Path, override val config: RepositoryConfig) : Repository {
    /**
     * The path of the repository's database.
     */
    private val databasePath = path.resolve(relativeDatabasePath)

    /**
     * The path of the directory where blobs are stored.
     */
    private val blobsPath = path.resolve(relativeBlobsPath)

    /**
     * The connection to the repository's database.
     */
    val db: Database = DatabaseFactory.connect(databasePath)

    /**
     * The hash algorithm used by this repository.
     */
    val hashAlgorithm: String = config[hashAlgorithmAttribute]

    /**
     * The block size used by this repository.
     */
    val blockSize: Long = config[blockSizeAttribute]

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

    override fun export(target: Path) {
        ZipUtil.pack(path.toFile(), target.toFile())
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
        blob.newInputStream().use { Files.copy(it, blobPath) }

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
        return Blob.fromFile(blobPath, hashAlgorithm)
    }

    /**
     * Returns a sequence of the checksums of blobs stored in this repository.
     *
     * The returned checksums are the expected checksums of the blobs, which could be different from their actual
     * checksums if the blobs are corrupt.
     */
    fun listBlobs(): Sequence<Checksum> = Files.walk(blobsPath)
        .asSequence()
        .filter { Files.isRegularFile(it) }
        .map { Checksum.fromHex(it.fileName.toString()) }

    /**
     * Removes any unused blobs from the repository.
     */
    fun clean() {
        val usedChecksums = transaction {
            BlobEntity.all().map { it.checksum }.toSet()
        }

        for (checksum in listBlobs()) {
            if (checksum !in usedChecksums) {
                removeBlob(checksum)
            }
        }
    }

    companion object {
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
         * The relative path of the database.
         */
        private val relativeDatabasePath: Path = Paths.get("manifest.db")

        /**
         * The relative path of the file containing the repository version.
         */
        private val relativeVersionPath: Path = Paths.get("version")

        /**
         * The relative path of the directory containing blobs.
         */
        private val relativeBlobsPath: Path = Paths.get("blobs")

        /**
         * The attribute which stores the hash algorithm.
         */
        val hashAlgorithmAttribute: RepositoryAttribute<String> = RepositoryAttribute.of(
            name = "Hash algorithm",
            default = "SHA-256",
            description = "The name of the algorithm used to calculate checksums."
        )

        /**
         * The attribute which stores the block size.
         */
        val blockSizeAttribute: RepositoryAttribute<Long> = RepositoryAttribute.of(
            name = "Block size",
            default = Long.MAX_VALUE,
            description = "The maximum size of the blocks that files stored in the repository are split into."
        )

        /**
         * A list of the attributes supported by this repository.
         */
        val attributes: List<RepositoryAttribute<*>> = listOf(
            hashAlgorithmAttribute,
            blockSizeAttribute
        )

        /**
         * Opens the repository at [path] and returns it.
         *
         * @param [path] The path of the repository.
         * @param [config] The configuration for the repository.
         *
         * @throws [UnsupportedFormatException] There is no compatible repository at [path].
         */
        fun open(path: Path, config: RepositoryConfig): DatabaseRepository {
            if (!check(path))
                throw UnsupportedFormatException("The format of the repository at '$path' is not supported.")

            return DatabaseRepository(path, config)
        }

        /**
         * Creates a repository at [path] and returns it.
         *
         * @param [path] The path of the repository.
         * @param [config] The configuration for the repository.
         *
         * @throws [FileAlreadyExistsException] There is already a file at [path].
         * @throws [IOException] An I/O error occurred.
         */
        fun create(path: Path, config: RepositoryConfig): DatabaseRepository {
            if (Files.exists(path)) throw FileAlreadyExistsException(path.toFile())

            val databasePath = path.resolve(relativeDatabasePath)
            val versionPath = path.resolve(relativeVersionPath)
            val blobsPath = path.resolve(relativeBlobsPath)

            Files.createDirectories(path)
            Files.createDirectory(blobsPath)

            DatabaseFactory.connect(databasePath)
            SchemaUtils.create(
                TimelineTable,
                SnapshotTable,
                VersionTable,
                TagTable,
                BlobTable,
                BlockTable,
                RetentionPolicyTable,
                TimelineRetentionPolicyTable
            )

            // Do this last to signify that the repository is valid.
            Files.writeString(versionPath, currentVersion.toString())

            return open(path, config)
        }

        /**
         * Imports a repository from a file and returns it.
         *
         * This is guaranteed to support importing the file created by [Repository.export].
         *
         * @param [source] The file to import the repository from.
         * @param [target] The path to create the repository at.
         * @param [config] The configuration for the repository.
         *
         * @throws [IOException] An I/O error occurred.
         */
        fun import(source: Path, target: Path, config: RepositoryConfig): DatabaseRepository {
            ZipUtil.unpack(source.toFile(), target.toFile())
            return open(target, config)
        }

        /**
         * Returns whether there is a compatible repository at [path].
         */
        fun check(path: Path): Boolean = try {
            val versionPath = path.resolve(relativeVersionPath)
            val version = UUID.fromString(Files.readString(versionPath))
            version in supportedVersions
        } catch (e: IOException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}

