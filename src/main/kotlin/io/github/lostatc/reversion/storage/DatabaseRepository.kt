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
import io.github.lostatc.reversion.api.Config
import io.github.lostatc.reversion.api.ConfigProperty
import io.github.lostatc.reversion.api.Configurator
import io.github.lostatc.reversion.api.JsonConfig
import io.github.lostatc.reversion.api.io.Blob
import io.github.lostatc.reversion.api.io.Checksum
import io.github.lostatc.reversion.api.io.FixedSizeChunker
import io.github.lostatc.reversion.api.io.write
import io.github.lostatc.reversion.api.storage.CleanupPolicy
import io.github.lostatc.reversion.api.storage.IncompatibleRepositoryException
import io.github.lostatc.reversion.api.storage.InvalidRepositoryException
import io.github.lostatc.reversion.api.storage.OpenAttempt
import io.github.lostatc.reversion.api.storage.RepairAction
import io.github.lostatc.reversion.api.storage.Repository
import io.github.lostatc.reversion.api.storage.VerifyAction
import io.github.lostatc.reversion.api.storage.Version
import io.github.lostatc.reversion.api.storage.delete
import io.github.lostatc.reversion.gui.format
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
import org.sqlite.SQLiteConnection
import org.sqlite.core.DB
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.streams.asSequence

/**
 * A simple data class implementing [Map.Entry].
 */
data class SimpleEntry<out K, out V>(override val key: K, override val value: V) : Map.Entry<K, V>

/**
 * A [DB.ProgressObserver] that does nothing.
 */
private object NullProgressObserver : DB.ProgressObserver {
    override fun progress(remaining: Int, pageCount: Int) = Unit
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
     * Checks the integrity of the database [db] and returns whether it is valid.
     */
    fun checkIntegrity(db: Database): Boolean = transaction(db) {
        val connection = TransactionManager.current().connection as SQLiteConnection
        val result = connection
            .prepareStatement("PRAGMA integrity_check;")
            .executeQuery()
            .getString(1)

        result == "ok"
    }

    /**
     * Connects to the database at the given [path].
     *
     * @throws [SQLException] The database could not be connected to or is corrupt.
     */
    fun connect(path: Path): Database = databases.getOrPut(path) {
        val connection = Database.connect(
            "jdbc:sqlite:${path.toUri().path}",
            driver = "org.sqlite.JDBC",
            setupConnection = { configure(it) }
        )

        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

        if (!checkIntegrity(connection)) {
            throw SQLException("The database is corrupt.")
        }

        logger.info("Connected to database at '$path'.")

        connection
    }

    /**
     * Creates a backup of a database.
     *
     * @param [source] The path of the database to back up.
     * @param [destination] The path to back up the database to.
     */
    fun backup(source: Path, destination: Path) {
        val db = connect(source)
        transaction(db) {
            val connection = TransactionManager.current().connection as SQLiteConnection
            connection.database.backup("main", destination.toString(), NullProgressObserver)
        }
    }

    /**
     * Restores a backup of a database.
     *
     * @param [source] The path of the backup to restore.
     * @param [destination] The path to restore the backup to.
     */
    fun restore(source: Path, destination: Path) {
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
    }
}

/**
 * An implementation of [Repository] which is backed by a relational database.
 */
data class DatabaseRepository(override val path: Path, override val config: Config) : Repository {

    /**
     * The block size used by this repository.
     */
    val blockSize: Long by blockSizeProperty

    /**
     * The number of minutes to wait between database backups.
     */
    val backupInterval: Int by backupIntervalProperty

    override val jobs: Set<Repository.Job> = setOf(
        Repository.Job(Duration.ofMinutes(backupInterval.toLong())) {
            // Don't back up the database if it's corrupt.
            if (DatabaseFactory.checkIntegrity(db)) {
                DatabaseFactory.backup(databasePath, databaseBackupPath)
            }
        }
    )

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
    private val databasePath: Path = path.resolve(relativeDatabasePath)

    /**
     * The path of the repository's database backup.
     */
    private val databaseBackupPath: Path = path.resolve(relativeDatabaseBackupPath)

    /**
     * The path of the directory where blobs are stored.
     */
    private val blobsPath = path.resolve(relativeBlobsPath)

    /**
     * The connection to the repository's database.
     */
    val db: Database = DatabaseFactory.connect(databasePath)

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
            Blob.chunkFile(file, FixedSizeChunker(blockSize))
        } catch (e: IOException) {
            return null
        }

        return blobs.find { it.checksum == checksum }

    }

    override fun verify(workDirectory: Path): List<VerifyAction> = listOf(
        object : VerifyAction {
            override val message: String =
                "This will verify the integrity of the database for this directory. This shouldn't take long. Do you want to continue?"

            override fun verify(): RepairAction? = verifyDatabase(path)
        },
        object : VerifyAction {
            override val message: String =
                "This will check the versions in this directory for corruption. This may take a while. Do you want to continue?"

            override fun verify(): RepairAction? = verifyVersions(workDirectory)
        }
    )

    /**
     * Verifies the integrity of the versions in this repository.
     *
     * @return A [RepairAction] if some versions are corrupt or `null` if no action needs to be taken.
     */
    private fun verifyVersions(workDirectory: Path): RepairAction? {
        // The set of corrupt versions.
        val corruptVersions = mutableSetOf<Version>()

        // The number of versions to be deleted.
        var deletedVersions = 0

        // The list of actions to take to repair the repository.
        val actions = mutableListOf<() -> Unit>()

        transaction(db) {
            val corruptBlobs = getCorruptBlobs()

            // Iterate over each corrupt blob.
            blobs@ for (blobEntity in corruptBlobs) {
                val blobPath = getBlobPath(blobEntity.checksum)

                val versions = blobEntity.blocks.map { DatabaseVersion(it.version, this@DatabaseRepository) }
                val versionPaths = versions.map { it.path }.distinct()

                // Because this blob is corrupt, all versions containing it are corrupt.
                corruptVersions.addAll(versions)

                // IteratInte over the path of each version the corrupt blob is in.
                for (relativePath in versionPaths) {
                    val absolutePath = workDirectory.resolve(relativePath)

                    // Find the missing blob in the current version of the file. Go to the next version otherwise.
                    val missingBlob = findBlob(absolutePath, blobEntity.checksum) ?: continue

                    // If the missing blob was found, use it to repair the repository and go to the next blob.
                    actions.add { missingBlob.write(blobPath, CREATE, TRUNCATE_EXISTING) }

                    continue@blobs
                }

                // The missing blob was not found in the file system. Delete all versions containing the blob.
                actions.add {
                    for (version in versions) {
                        version.delete()
                    }
                }

                // Get the number of deleted versions.
                deletedVersions = versions.size
            }
        }

        // If a version is corrupt and was not deleted, it was repaired. We don't know whether a version will be
        // repaired until all blobs have been checked.
        val repairedVersions = corruptVersions.size - deletedVersions

        logger.info("Checked repository for corrupt versions.")

        if (corruptVersions.isEmpty()) {
            return null
        }

        return object : RepairAction {
            override val message: String =
                "Some versions are corrupt. There are ${corruptVersions.size} corrupt versions in this directory. $repairedVersions of them will be repaired. $deletedVersions of them cannot be repaired and will be deleted. This may take a while. Do you want to repair?"

            override fun repair(): RepairAction.Result = try {
                for (action in actions) {
                    action()
                }
                logger.info("Successfully repaired corrupt versions.")
                RepairAction.Result(true, "All corrupt versions were repaired or deleted.")
            } catch (e: IOException) {
                logger.warn("Repairing corrupt versions failed.", e)
                RepairAction.Result(
                    false,
                    "An error occurred while repairing corrupt versions. See the logs for more information."
                )
            }
        }
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
         * The relative path of the backup copy of the database.
         */
        private val relativeDatabaseBackupPath: Path = Paths.get("manifest.db.bak")

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
        val blockSizeProperty: ConfigProperty<Long> = ConfigProperty.of("blockSize", Long.MAX_VALUE)

        /**
         * The property which stores the backup interval.
         */
        val backupIntervalProperty: ConfigProperty<Int> = ConfigProperty.of("backupInterval", 15)

        /**
         * An object used for serializing data as JSON.
         */
        private val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .create()

        /**
         * Verify the integrity of the database of the [Repository] at [path].
         *
         * This method is in the companion object because it may not be possible to instantiate a [DatabaseRepository]
         * if the database is corrupt.
         *
         * @return A [RepairAction] if the database is corrupt or `null` if no action needs to be taken.
         */
        private fun verifyDatabase(path: Path): RepairAction? {
            val databasePath = path.resolve(relativeDatabasePath)
            val backupPath = path.resolve(relativeDatabaseBackupPath)

            // Check the database for corruption.
            try {
                val database = DatabaseFactory.connect(databasePath)
                if (DatabaseFactory.checkIntegrity(database)) return null
            } catch (e: SQLException) {
                // Database is corrupt. Attempt repair.
            }

            // Check if a backup of the database exists.
            if (Files.notExists(backupPath)) {
                return object : RepairAction {
                    override val message: String =
                        "The database could not be read and there are no backups to restore. Would you like to continue?"

                    override fun repair(): RepairAction.Result =
                        RepairAction.Result(false, "The database could not be restored.")
                }
            }

            val backupTime = Files.getLastModifiedTime(backupPath).toInstant()

            return object : RepairAction {
                override val message: String =
                    "The database could not be read. The most recent backup was made at ${backupTime.format()}. Restoring from this backup will cause all versions created after that time to be lost. Do you want to restore?"

                override fun repair(): RepairAction.Result {
                    // Attempt to restore from a database backup.
                    DatabaseFactory.restore(source = backupPath, destination = databasePath)

                    logger.info("Restored database backup from ${backupTime.format()}.")

                    // Try to open the repository again.
                    return try {
                        DatabaseFactory.connect(databasePath)
                        logger.info("Successfully repaired corrupt database.")
                        RepairAction.Result(
                            true,
                            "The database was successfully recovered from the backup. Versions created since ${backupTime.format()} have been lost."
                        )
                    } catch (e: SQLException) {
                        logger.warn("Repairing corrupt database failed", e)
                        RepairAction.Result(
                            false,
                            "After restoring from a backup, the database still could not be read."
                        )
                    }
                }
            }
        }

        /**
         * Opens the repository at [path] and returns it.
         *
         * @param [path] The path of the repository.
         *
         * @throws [IncompatibleRepositoryException] There is no compatible repository at [path].
         * @throws [InvalidRepositoryException] The repository is compatible but cannot be read.
         * @throws [IOException] An I/O error occurred.
         */
        fun open(path: Path): OpenAttempt<Repository> {
            if (!checkRepository(path)) {
                throw IncompatibleRepositoryException("The format of the repository at '$path' is not supported.")
            }

            val configPath = path.resolve(relativeConfigPath)
            val config = JsonConfig(configPath, gson)
            config.read()

            return try {
                val repository = DatabaseRepository(path, config)
                logger.info("Opened repository $repository.")
                OpenAttempt.Success(repository)
            } catch (e: SQLException) {
                val action = verifyDatabase(path)
                    ?: error("The repository could not be opened but the database reported no corruption.")
                OpenAttempt.Failure(sequenceOf(action))
            }
        }

        /**
         * Creates a repository at [path] and returns it.
         *
         * @param [path] The path of the repository.
         * @param [configurator] An object which configures the repository.
         *
         * @throws [FileAlreadyExistsException] There is already a file at [path].
         * @throws [IOException] An I/O error occurred.
         */
        fun create(path: Path, configurator: Configurator = Configurator.Default): DatabaseRepository {
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
            val config = JsonConfig(configPath, gson)
            configurator.configure(config)
            config.write()

            // Do this last to signify that the repository is valid.
            Files.writeString(versionPath, currentVersion.toString())

            return open(path).onFail { error("The newly-created repository is corrupt.") } as DatabaseRepository
        }

        /**
         * Returns whether there is a compatible repository at [path].
         */
        fun checkRepository(path: Path): Boolean = try {
            val versionPath = path.resolve(relativeVersionPath)
            val version = UUID.fromString(Files.readString(versionPath))
            version in supportedVersions
        } catch (e: IOException) {
            logger.warn("There is not a compatible repository at '$path'.", e)
            false
        } catch (e: IllegalArgumentException) {
            logger.warn("There is not a compatible repository at '$path'.", e)
            false
        }
    }
}

