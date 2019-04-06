/*
 * Copyright © 2019 Wren Powell
 *
 * This file is part of reversion.
 *
 * reversion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * reversion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with reversion.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.lostatc.reversion.storage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.lostatc.reversion.api.*
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
data class DatabaseRepository(override val path: Path, override val config: Config) : Repository {
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
    val hashAlgorithm: String by hashAlgorithmProperty

    /**
     * The block size used by this repository.
     */
    val blockSize: Long by blockSizeProperty

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
         * The relative path of the JSON config file.
         */
        private val relativeConfigPath: Path = Paths.get("config.json")

        /**
         * The property which stores the hash algorithm.
         */
        val hashAlgorithmProperty: ConfigProperty<String> = ConfigProperty.of(
            key = "hashFunc",
            name = "Hash algorithm",
            default = "SHA-256",
            description = "The name of the algorithm used to calculate checksums."
        )

        /**
         * The property which stores the block size.
         */
        val blockSizeProperty: ConfigProperty<Long> = ConfigProperty.of(
            key = "blockSize",
            name = "Block size",
            default = Long.MAX_VALUE,
            description = "The maximum size of the blocks that files stored in the repository are split into."
        )

        /**
         * A list of the properties supported by this repository.
         */
        val properties: List<ConfigProperty<*>> = listOf(
            hashAlgorithmProperty,
            blockSizeProperty
        )

        /**
         * An object used for serializing data as JSON.
         */
        private val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Config::class.java, ConfigSerializer)
            .registerTypeAdapter(Config::class.java, ConfigDeserializer(properties))
            .create()

        /**
         * Opens the repository at [path] and returns it.
         *
         * @param [path] The path of the repository.
         *
         * @throws [UnsupportedFormatException] There is no compatible repository at [path].
         */
        fun open(path: Path): DatabaseRepository {
            if (!check(path))
                throw UnsupportedFormatException("The format of the repository at '$path' is not supported.")

            val configPath = path.resolve(relativeConfigPath)
            val config = Files.newBufferedReader(configPath).use {
                gson.fromJson(it, Config::class.java)
            }

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

            // Serialize the config.
            Files.newBufferedWriter(configPath).use {
                gson.toJson(config, it)
            }

            // Do this last to signify that the repository is valid.
            Files.writeString(versionPath, currentVersion.toString())

            return open(path)
        }

        /**
         * Imports a repository from a file and returns it.
         *
         * This is guaranteed to support importing the file created by [Repository.export].
         *
         * @param [source] The file to import the repository from.
         * @param [target] The path to create the repository at.
         *
         * @throws [IOException] An I/O error occurred.
         */
        fun import(source: Path, target: Path): DatabaseRepository {
            ZipUtil.unpack(source.toFile(), target.toFile())
            return open(target)
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
