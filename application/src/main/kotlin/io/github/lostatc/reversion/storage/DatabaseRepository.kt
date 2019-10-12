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

package io.github.lostatc.reversion.storage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.lostatc.reversion.api.Blob
import io.github.lostatc.reversion.api.Checksum
import io.github.lostatc.reversion.api.CleanupPolicy
import io.github.lostatc.reversion.api.Config
import io.github.lostatc.reversion.api.ConfigProperty
import io.github.lostatc.reversion.api.IntegrityReport
import io.github.lostatc.reversion.api.Repository
import io.github.lostatc.reversion.api.UnsupportedFormatException
import io.github.lostatc.reversion.api.Version
import io.github.lostatc.reversion.api.delete
import io.github.lostatc.reversion.api.write
import io.github.lostatc.reversion.schema.BlobEntity
import io.github.lostatc.reversion.schema.BlobTable
import io.github.lostatc.reversion.schema.BlockEntity
import io.github.lostatc.reversion.schema.BlockTable
import io.github.lostatc.reversion.schema.CleanupPolicyTable
import io.github.lostatc.reversion.schema.SnapshotTable
import io.github.lostatc.reversion.schema.TimelineCleanupPolicyTable
import io.github.lostatc.reversion.schema.TimelineEntity
import io.github.lostatc.reversion.schema.TimelineTable
import io.github.lostatc.reversion.schema.VersionEntity
import io.github.lostatc.reversion.schema.VersionTable
import io.github.lostatc.reversion.serialization.ConfigDeserializer
import io.github.lostatc.reversion.serialization.ConfigSerializer
import org.apache.commons.io.FileUtils
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteConfig
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.sql.Connection
import java.time.Instant
import java.util.UUID
import kotlin.streams.asSequence

data class SimpleEntry<out K, out V>(override val key: K, override val value: V) : Map.Entry<K, V>

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
     * A logger for logging errors.
     */
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Configure the connection.
     */
    private fun configure(connection: Connection) {
        SQLiteConfig().apply {
            enforceForeignKeys(true)
            setJournalMode(SQLiteConfig.JournalMode.WAL)
            apply(connection)
        }
    }

    /**
     * Connect to the database at the given [path].
     */
    fun connect(path: Path): Database = databases.getOrPut(path) {
        val connection = Database.connect(
            "jdbc:sqlite:${path.toUri().path}",
            driver = "org.sqlite.JDBC",
            setupConnection = { configure(it) }
        )
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        logger.debug("Connecting to database at '$path'.")
        connection
    }
}

/**
 * An [IntegrityReport] that can be updated as the repository is checked for corruption.
 */
private data class IncrementalIntegrityReport(
    override val repaired: MutableSet<Version> = mutableSetOf(),
    override val deleted: MutableSet<Version> = mutableSetOf()
) : IntegrityReport {
    /**
     * The list of actions to take to repair the repository.
     */
    val actions: MutableList<() -> Unit> = mutableListOf()

    override fun repair() {
        for (action in actions) {
            action()
        }
    }
}

/**
 * An implementation of [Repository] which is backed by a relational database.
 */
data class DatabaseRepository(override val path: Path, override val config: Config) : Repository {

    override val timelines: Map<UUID, DatabaseTimeline> = object : AbstractMap<UUID, DatabaseTimeline>() {
        override val entries: Set<Map.Entry<UUID, DatabaseTimeline>>
            get() = transaction(db) {
                TimelineEntity
                    .all()
                    .map { SimpleEntry(it.id.value, DatabaseTimeline(it, this@DatabaseRepository)) }
                    .toSet()
            }

        override val keys: Set<UUID>
            get() = transaction(db) {
                TimelineEntity
                    .all()
                    .map { it.id.value }
                    .toSet()
            }

        override val size: Int
            get() = transaction(db) {
                TimelineEntity.count()
            }

        override fun containsKey(key: UUID): Boolean = get(key) != null

        override fun containsValue(value: DatabaseTimeline): Boolean = containsKey(value.id)

        override fun get(key: UUID): DatabaseTimeline? = transaction(db) {
            TimelineEntity
                .find { TimelineTable.id eq key }
                .firstOrNull()
                ?.let { DatabaseTimeline(it, this@DatabaseRepository) }
        }

        override fun isEmpty(): Boolean = transaction(db) {
            TimelineEntity.all().empty()
        }
    }

    override val storedSize: Long
        get() = transaction(db) {
            BlobEntity.all().map { it.size }.sum()
        }

    override val totalSize: Long
        get() = transaction(db) {
            VersionEntity.all().map { it.size }.sum()
        }

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
     * The block size used by this repository.
     */
    val blockSize: Long by blockSizeProperty

    override fun createTimeline(policies: Set<CleanupPolicy>): DatabaseTimeline {
        val timeline = transaction(db) {
            val timelineEntity = TimelineEntity.new {
                this.timeCreated = Instant.now()
            }

            DatabaseTimeline(timelineEntity, this@DatabaseRepository).apply {
                cleanupPolicies = policies
            }
        }

        logger.info("Created timeline $timeline.")

        return timeline
    }

    override fun removeTimeline(id: UUID): Boolean {
        // Remove the timeline from the database before modifying the file system to avoid corruption in case this
        // operation is interrupted.
        val timeline = timelines[id] ?: return false

        // Remove the timeline and all its snapshots, versions and tags from the database.
        transaction(db) {
            timeline.entity.delete()
        }

        // Remove any blobs associated with the timeline which aren't referenced by any other timeline.
        clean()

        logger.info("Removed timeline $timeline.")

        return true
    }

    /**
     * Returns the set of blobs which are corrupt.
     */
    private fun getCorruptBlobs(): Set<BlobEntity> = transaction(db) {
        BlobEntity
            .all()
            .filter { getBlob(it.checksum)?.checksum != it.checksum }
            .toSet()
    }

    /**
     * Returns the blob in the given [file] with the given [checksum].
     *
     * If the blob cannot be found or an [IOException] is thrown, this returns `null`.
     */
    private fun findBlob(file: Path, checksum: Checksum): Blob? {
        val blobs = try {
            Blob.chunkFile(file)
        } catch (e: IOException) {
            return null
        }

        return blobs.find { it.checksum == checksum }

    }

    override fun verify(workDirectory: Path): IntegrityReport {
        val report = IncrementalIntegrityReport()
        val corruptVersions = mutableSetOf<Version>()

        transaction(db) {
            val corruptBlobs = getCorruptBlobs()

            // Iterate over each corrupt blob.
            blobs@ for (blobEntity in corruptBlobs) {
                val blobPath = getBlobPath(blobEntity.checksum)

                val versions = blobEntity.blocks.map { DatabaseVersion(it.version, this@DatabaseRepository) }
                val versionPaths = versions.map { it.path }.distinct()

                // Because this blob is corrupt, all versions containing it are corrupt.
                corruptVersions.addAll(versions)

                // Iterate over the path of each version the corrupt blob is in.
                for (relativePath in versionPaths) {
                    val absolutePath = workDirectory.resolve(relativePath)

                    // Find the missing blob in the current version of the file. Go to the next version otherwise.
                    val missingBlob = findBlob(absolutePath, blobEntity.checksum) ?: continue

                    // If the missing blob was found, use it to repair the repository and go to the next blob.
                    missingBlob.write(blobPath, CREATE, TRUNCATE_EXISTING)

                    continue@blobs
                }

                // The missing blob was not found in the file system. Delete all versions containing the blob.
                report.actions.add {
                    for (version in versions) {
                        version.delete()
                    }
                }

                // Mark these versions as deleted.
                report.deleted.addAll(versions)
            }
        }

        // If a version is corrupt and was not deleted, it was repaired. We don't know whether a version will be
        // repaired until all blobs have been checked.
        report.repaired.addAll(corruptVersions - report.deleted)

        logger.info("Checked repository for corruption and returned report $report.")

        return report
    }

    override fun delete() {
        FileUtils.deleteDirectory(path.toFile())
    }

    /**
     * Returns the storage location of the blob with the given [checksum].
     */
    fun getBlobPath(checksum: Checksum): Path =
        blobsPath.resolve(checksum.toHex().slice(0..1)).resolve(checksum.toHex())

    /**
     * Adds the given [blob] to this repository.
     *
     * @throws [IOException] An I/O error occurred.
     */
    fun addBlob(blob: Blob) {
        // Add the blob to the file system before adding the record to the database to avoid corruption in case this
        // operation is interrupted.
        val blobPath = getBlobPath(blob.checksum)
        Files.createDirectories(blobPath.parent)
        if (Files.notExists(blobPath)) {
            blob.write(blobPath, CREATE)
        }

        transaction(db) {
            // Add the blob to the database if it doesn't already exist.
            if (BlobTable.select { BlobTable.checksum eq blob.checksum }.empty()) {
                BlobEntity.new {
                    checksum = blob.checksum
                    size = Files.size(blobPath)
                }
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
        transaction(db) {
            BlobTable.deleteWhere { BlobTable.checksum eq checksum }
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
        return Blob.fromFile(blobPath)
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
        // Get the checksums of blobs which are being referenced by at least one version.
        val usedChecksums = transaction(db) {
            BlockEntity.all().map { it.blob.checksum }.toSet()
        }

        for (checksum in listBlobs()) {
            if (checksum !in usedChecksums) {
                removeBlob(checksum)
            }
        }
    }

    companion object {
        /**
         * The logger for this class.
         */
        private val logger: Logger = LoggerFactory.getLogger(DatabaseRepository::class.java)

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
         * The relative path of the JSON config file.
         */
        private val relativeConfigPath: Path = Paths.get("config.json")

        /**
         * The property which stores the block size.
         */
        val blockSizeProperty: ConfigProperty<Long> = ConfigProperty.of(
            key = "blockSize",
            name = "Block size",
            default = Long.MAX_VALUE,
            description = "The maximum size of the blocks that files stored in the repository are split into.",
            validator = { require(it > 0) { "The given value must be greater than 0." } }
        )

        /**
         * An object used for serializing data as JSON.
         */
        private val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Config::class.java, ConfigSerializer)
            .registerTypeAdapter(
                Config::class.java,
                ConfigDeserializer(getConfig().properties)
            )
            .create()

        fun getConfig(): Config = Config(blockSizeProperty)

        /**
         * Opens the repository at [path] and returns it.
         *
         * @param [path] The path of the repository.
         *
         * @throws [UnsupportedFormatException] There is no compatible repository at [path].
         */
        fun open(path: Path): DatabaseRepository {
            if (!checkRepository(path))
                throw UnsupportedFormatException("The format of the repository at '$path' is not supported.")

            val configPath = path.resolve(relativeConfigPath)
            val config = Files.newBufferedReader(configPath).use {
                gson.fromJson(it, Config::class.java)
            }

            val repository = DatabaseRepository(path, config)

            logger.debug("Opened repository $repository.")

            return repository
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
        fun create(path: Path, config: Config): DatabaseRepository {
            if (Files.exists(path)) throw FileAlreadyExistsException(path.toFile())

            val databasePath = path.resolve(relativeDatabasePath)
            val versionPath = path.resolve(relativeVersionPath)
            val blobsPath = path.resolve(relativeBlobsPath)
            val configPath = path.resolve(relativeConfigPath)

            // Create directories.
            Files.createDirectories(path)
            Files.createDirectory(blobsPath)

            // Create the database.
            val db = DatabaseFactory.connect(databasePath)

            transaction(db) {
                SchemaUtils.create(
                    TimelineTable,
                    CleanupPolicyTable,
                    TimelineCleanupPolicyTable,
                    SnapshotTable,
                    VersionTable,
                    BlobTable,
                    BlockTable
                )
            }

            // Serialize the config.
            Files.newBufferedWriter(configPath).use {
                gson.toJson(config, it)
            }

            // Do this last to signify that the repository is valid.
            Files.writeString(versionPath, currentVersion.toString())

            val repository = open(path)

            logger.info("Created repository $repository.")

            return repository
        }

        /**
         * Returns whether there is a compatible repository at [path].
         */
        fun checkRepository(path: Path): Boolean = try {
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

