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

package io.github.lostatc.reversion.api.io

import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

/**
 * A [ReadableByteChannel] which allows for setting a [limit] on how many bytes can be read from it.
 *
 * @param [channel] The channel to wrap.
 * @param [limit] The maximum number of bytes which can be read from the channel.
 */
class BoundedByteChannel(
    private val channel: ReadableByteChannel,
    val limit: Long
) : ReadableByteChannel by channel {
    private var position = 0

    private val remaining
        get() = limit - position

    override fun read(dst: ByteBuffer): Int {
        val originalLimit = dst.limit()

        if (remaining == 0L) return -1
        if (remaining < dst.limit()) {
            dst.limit(dst.position() + remaining.toInt())
        }

        val bytesRead = channel.read(dst)
        if (bytesRead > -1) {
            position += bytesRead
        }

        dst.limit(originalLimit)
        return bytesRead
    }

}

/**
 * A [ReadableByteChannel] which reads from each channel in [channels] in order.
 *
 * Channels are opened lazily and closed once they have been exhausted
 */
class SequenceByteChannel(private val channels: Sequence<ReadableByteChannel>) : ReadableByteChannel {
    private val iterator = channels.iterator()

    private var current: ReadableByteChannel? = null

    private var isClosed = false

    override fun isOpen(): Boolean = !isClosed

    override fun close() {
        current?.close()
        isClosed = true
    }

    override fun read(dst: ByteBuffer): Int {
        val bytesRead = current?.read(dst) ?: -1

        if (bytesRead == -1) {
            current?.close()
            if (!iterator.hasNext()) return -1
            current = iterator.next()
        }

        return maxOf(bytesRead, 0)
    }
}

/**
 * Put as many bytes as possible from [source] into this buffer.
 */
private fun ByteBuffer.putSome(source: ByteBuffer): ByteBuffer {
    if (source.remaining() > remaining()) {
        val originalLimit = source.limit()
        source.limit(source.position() + remaining())
        put(source)
        source.limit(originalLimit)
    } else {
        put(source)
    }

    return this
}

/**
 * A [ReadableByteChannel] which reads bytes from the given [buffer].
 */
class BufferByteChannel(private val buffer: ByteBuffer) : ReadableByteChannel {
    var isClosed = false

    override fun isOpen(): Boolean = !isClosed

    override fun close() {
        isClosed = true
    }

    override fun read(dst: ByteBuffer): Int {
        if (!buffer.hasRemaining()) return -1
        val startPosition = buffer.position()
        dst.putSome(buffer)
        return buffer.position() - startPosition
    }
}

/**
 * The size of the buffer to use for I/O operations.
 */
private const val BUFFER_SIZE: Int = 4096

/**
 * Copy all the bytes in [source] to [dest] and return the number of bytes copied.
 */
fun copyChannel(source: ReadableByteChannel, dest: WritableByteChannel): Long {
    val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
    var bytesCopied = 0L

    while (source.read(buffer) != -1 || buffer.position() != 0) {
        buffer.flip()
        bytesCopied += dest.write(buffer)
        buffer.compact()
    }

    return bytesCopied
}

/**
 * Resolves [firstSegment] and each of the given [segments] against this path.
 *
 * @see [Path.resolve]
 */
fun Path.resolve(firstSegment: String, vararg segments: String): Path =
    segments.fold(resolve(firstSegment)) { path, segment -> path.resolve(segment) }

/**
 * Reads all the bytes from this channel into a [ByteBuffer].
 */
fun ReadableByteChannel.readBytes(): ByteBuffer {
    var buffer = ByteBuffer.allocateDirect(4096)
    while (read(buffer) != -1) {
        if (!buffer.hasRemaining()) {
            buffer = ByteBuffer.allocateDirect(buffer.capacity() * 2).put(buffer.flip())
        }
    }
    return buffer
}

/**
 * Returns a [ByteArray] containing the bytes in this buffer.
 */
fun ByteBuffer.toByteArray(): ByteArray {
    val array = ByteArray(limit())
    get(array)
    return array
}

/**
 * Reads the bytes of this buffer into a string.
 */
fun ByteBuffer.readString(): String = flip().toByteArray().toString(Charset.defaultCharset())

/**
 * Reads the bytes in this blob into a string.
 */
fun Blob.readString(): String = openChannel().use {
    it.readBytes().readString()
}

/**
 * Creates a [Blob] containing the given [text].
 */
fun Blob.Companion.fromString(text: String): Blob = fromBytes(ByteBuffer.wrap(text.toByteArray()))

/**
 * A builder for creating a file tree for testing.
 */
data class FileTreeBuilder(val parent: Path) {

    /**
     * Construct a new instance with the given path and children.
     */
    constructor(parent: Path, init: FileTreeBuilder.() -> Unit) : this(parent) {
        Files.createDirectories(parent)
        init()
    }

    /**
     * Create a new file with the given path and contents.
     *
     * @param [firstSegment] The first segment of the path relative to [parent].
     * @param [segments] The remaining segments of the path relative to [parent].
     * @param [content] The contents of the file.
     */
    fun file(firstSegment: String, vararg segments: String, content: String = ""): FileTreeBuilder {
        val path = parent.resolve(firstSegment, *segments)
        Files.createFile(path)
        Files.writeString(path, content)
        return FileTreeBuilder(path)
    }

    /**
     * Create a new directory with the given path and children.
     *
     * @param [firstSegment] The first segment of the path relative to [parent].
     * @param [segments] The remaining segments of the path relative to [parent].
     * @param [init] A scoped function in which files and directories can be created as children of this directory.
     */
    fun directory(
        firstSegment: String,
        vararg segments: String,
        init: FileTreeBuilder.() -> Unit = { }
    ): FileTreeBuilder {
        val path = parent.resolve(firstSegment, *segments)
        Files.createDirectory(path)
        val context = FileTreeBuilder(path)
        context.init()
        return context
    }
}
