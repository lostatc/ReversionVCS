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

import io.github.lostatc.reversion.api.Snapshot
import jnr.ffi.Pointer
import org.apache.commons.io.input.TeeInputStream
import ru.serce.jnrfuse.ErrorCodes
import ru.serce.jnrfuse.FuseFillDir
import ru.serce.jnrfuse.FuseStubFS
import ru.serce.jnrfuse.struct.FileStat
import ru.serce.jnrfuse.struct.FuseFileInfo
import ru.serce.jnrfuse.struct.Timespec
import java.io.Closeable
import java.io.InputStream
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission

/**
 * The size of the buffer used when reading and discarding bytes.
 */
private const val SKIP_BUFFER_SIZE: Int = 2048

/**
 * Sets this buffer's limit, not exceeding its capacity.
 */
private fun Buffer.limitOrCapacity(newLimit: Int): Buffer = apply {
    limit(minOf(newLimit, capacity()))
}

/**
 * Reads and discards [bytes] bytes from the channel.
 */
private fun ReadableByteChannel.skip(bytes: Int) {
    val skipBuffer = ByteBuffer.allocate(SKIP_BUFFER_SIZE)
    var remaining = bytes

    while (remaining > 0) {
        skipBuffer.clear()
        skipBuffer.limitOrCapacity(remaining)
        remaining -= read(skipBuffer)
    }
}

/**
 * Advances the [position][SeekableByteChannel.position] by the given number of [bytes].
 */
private fun SeekableByteChannel.advance(bytes: Int) {
    position(position() + bytes)
}

/**
 * A seekable source of data that wraps an [InputStream].
 *
 * This class allows for efficiently accessing data which has previously been read from an [InputStream] without having
 * to re-open the stream and re-read data. It caches data which has already been read in the file system and allows for
 * seeking on that data. New data is lazily read from the given [inputStream] as it is needed.
 */
private class SeekableDataSource(inputStream: InputStream) : Closeable {
    /**
     * The temporary file which data is cached to.
     */
    private val cacheFile: Path = Files.createTempFile("reversion-", "")

    /**
     * The channel which new data is read from.
     */
    private val inputChannel: ReadableByteChannel = Channels.newChannel(
        TeeInputStream(inputStream, Files.newOutputStream(cacheFile), true)
    )

    /**
     * The channel which cached data is read from.
     */
    private val cacheChannel: SeekableByteChannel = Files.newByteChannel(cacheFile, StandardOpenOption.READ)

    /**
     * The current position which data is read from.
     *
     * @see [SeekableByteChannel.position]
     */
    val position: Long
        get() = cacheChannel.position()

    /**
     * Sets the [position] to [newPosition].
     *
     * @see [SeekableByteChannel.position]
     */
    fun seek(newPosition: Long) {
        val cacheSize = cacheChannel.size()

        // If the new position is past the end of the cache, read more data from the input stream.
        if (newPosition > cacheSize) {
            val remaining = (newPosition - cacheSize).toInt()
            inputChannel.skip(remaining)
        }

        cacheChannel.position(newPosition)
    }

    /**
     * Reads data from this data source into the given [buffer].
     *
     * @see [ReadableByteChannel.read]
     */
    fun read(buffer: ByteBuffer) {
        // Read data from the cache first and then the input stream once the end of the cache is reached.
        cacheChannel.read(buffer)
        val bytesRead = inputChannel.read(buffer)
        if (bytesRead > 0) cacheChannel.advance(bytesRead)
    }

    override fun close() {
        inputChannel.close()
        cacheChannel.close()
        Files.deleteIfExists(cacheFile)
    }
}

/**
 * The integer file mode for the given permission.
 */
private val PosixFilePermission.mode: Int
    get() = when (this) {
        PosixFilePermission.OWNER_READ -> FileStat.S_IRUSR
        PosixFilePermission.OWNER_WRITE -> FileStat.S_IWUSR
        PosixFilePermission.OWNER_EXECUTE -> FileStat.S_IXUSR
        PosixFilePermission.GROUP_READ -> FileStat.S_IRGRP
        PosixFilePermission.GROUP_WRITE -> FileStat.S_IWGRP
        PosixFilePermission.GROUP_EXECUTE -> FileStat.S_IXGRP
        PosixFilePermission.OTHERS_READ -> FileStat.S_IROTH
        PosixFilePermission.OTHERS_WRITE -> FileStat.S_IWOTH
        PosixFilePermission.OTHERS_EXECUTE -> FileStat.S_IXOTH
    }

/**
 * The integer file mode for the given set of permissions.
 */
private val Set<PosixFilePermission>.mode: Int
    get() = fold(0) { mode, permission -> mode or permission.mode }

/**
 * The integer permissions mode to use for directories.
 */
private const val DIR_PERMISSIONS: Int = FileStat.ALL_READ or FileStat.S_IXUGO or FileStat.S_IWUSR

/**
 * The integer permissions mode to use for files which don't have permission information stored in the repository.
 */
private const val DEFAULT_FILE_PERMISSIONS: Int = FileStat.ALL_READ or FileStat.S_IWUSR

/**
 * Returns whether this snapshot contains a path that starts with the given relative [path].
 */
private fun Snapshot.containsDirectory(path: Path): Boolean =
    path == Paths.get("") || versions.keys.any { it.startsWith(path) }

/**
 * Returns the paths in this snapshot which are children of the given [parent].
 *
 * @param [parent] The relative path of a file in this snapshot or the parent of a file in this snapshot.
 */
private fun Snapshot.getChildren(parent: Path): Set<Path> = versions.keys
    .filterNot { it == parent }
    .filter { parent == Paths.get("") || it.startsWith(parent) }
    .map { parent.relativize(it).getName(0) }
    .toSet()

/**
 * Sets the value of this [Timespec] to the given [time].
 */
private fun Timespec.set(time: FileTime) {
    val instant = time.toInstant()
    tv_sec.set(instant.epochSecond)
    tv_nsec.set(instant.nano)
}

/**
 * A read-only FUSE file system implementation for reading data from a snapshot.
 *
 * @param [workDirectory] The path of the working directory the snapshot was taken from.
 * @param [snapshot] The path of the snapshot to show versions from.
 */
data class FuseFileSystem(val workDirectory: Path, val snapshot: Snapshot) : FuseStubFS() {

    /**
     * A map of open files and their data sources.
     */
    private val dataSources: MutableMap<Path, SeekableDataSource> = mutableMapOf()

    /**
     * Creates a relative [Path] from the given [path] string.
     */
    private fun getRelativePath(path: String): Path = Paths.get(path).let { it.root.relativize(it) }

    override fun getattr(path: String, stat: FileStat): Int {
        // Return an error if the file doesn't exist.
        val relativePath = getRelativePath(path)
        val version = snapshot.versions[relativePath]

        stat.run {
            st_uid.set(context.uid.get())
            st_gid.set(context.gid.get())
        }

        when {
            // The path is a file.
            version != null -> stat.run {
                st_mode.set(FileStat.S_IFREG or (version.permissions?.mode ?: DEFAULT_FILE_PERMISSIONS))
                st_size.set(version.size)
                st_mtim.set(version.lastModifiedTime)
            }

            // The path is a directory.
            snapshot.containsDirectory(relativePath) -> stat.run {
                st_mode.set(FileStat.S_IFDIR or DIR_PERMISSIONS)
            }

            // The path doesn't exist.
            else -> return -ErrorCodes.ENOENT()
        }

        return 0
    }

    override fun readdir(path: String, buf: Pointer, filter: FuseFillDir, offset: Long, fi: FuseFileInfo): Int {
        // Get the immediate children of the directory.
        val relativePath = getRelativePath(path)
        val children = snapshot.getChildren(relativePath)

        // Return an error if the directory doesn't exist. The repository doesn't store empty directories.
        if (children.isEmpty()) return -ErrorCodes.ENOENT()

        for (child in children) {
            filter.apply(buf, child.fileName.toString(), null, 0)
        }

        return 0
    }

    override fun open(path: String, fi: FuseFileInfo): Int {
        // Return an error if the file doesn't exist.
        val relativePath = getRelativePath(path)
        val version = snapshot.versions[relativePath] ?: return -ErrorCodes.ENOENT()

        dataSources[relativePath] = SeekableDataSource(version.data.newInputStream())

        return 0
    }

    override fun release(path: String, fi: FuseFileInfo): Int {
        val relativePath = getRelativePath(path)
        dataSources.remove(relativePath)?.close()

        return 0
    }

    override fun read(path: String, buf: Pointer, size: Long, offset: Long, fi: FuseFileInfo): Int {
        // Return an error if the file hasn't been opened.
        val relativePath = getRelativePath(path)
        val dataSource = dataSources[relativePath] ?: return -ErrorCodes.EBADF()

        val buffer = ByteBuffer.allocate(size.toInt())
        dataSource.seek(offset)
        dataSource.read(buffer)

        buf.put(0, buffer.array(), 0, buffer.position())
        return buffer.position()
    }
}
