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

package io.github.lostatc.reversion

import io.github.lostatc.reversion.api.io.Blob
import io.github.lostatc.reversion.api.io.readBytes
import io.github.lostatc.reversion.api.io.resolve
import io.github.lostatc.reversion.api.io.toByteArray
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random

/**
 * Reads the bytes of this buffer into a string.
 */
fun ByteBuffer.readString(): String = flip().toByteArray().toString(Charset.defaultCharset())

/**
 * Read the contents of this channel into a [ByteArray].
 */
fun ReadableByteChannel.toByteArray(): ByteArray = readBytes().flip().toByteArray()

/**
 * Read the contents of this blob into a [ByteArray].
 */
fun Blob.toByteArray(): ByteArray = openChannel().use { it.toByteArray() }

/**
 * Reads the bytes in this blob into a string.
 */
fun Blob.readString(): String = toByteArray().toString(Charset.defaultCharset())

/**
 * Creates a [Blob] containing the given [text].
 */
fun Blob.Companion.fromString(text: String): Blob = fromBytes(ByteBuffer.wrap(text.toByteArray()))

/**
 * Return an array of the given [size] filled with random bytes.
 */
fun randomBytes(size: Int): ByteArray = ByteArray(size).also { Random().nextBytes(it) }

/**
 * A builder for creating a file tree for testing.
 */
data class FileTreeBuilder(val parent: Path) {

    /**
     * The mutable backing property of [contents].
     */
    private val _contents = mutableMapOf<Path, ByteArray>()

    /**
     * The contents of each file created by [file].
     */
    val contents: Map<Path, ByteArray> = _contents

    /**
     * Construct a new instance with the given path and children.
     */
    constructor(parent: Path, init: FileTreeBuilder.() -> Unit) : this(parent) {
        Files.createDirectories(parent)
        init()
    }

    /**
     * Create a new file with the given path and random contents.
     *
     * @param [firstSegment] The first segment of the path relative to [parent].
     * @param [segments] The remaining segments of the path relative to [parent].
     * @param [size] The size of the file to create.
     */
    fun file(firstSegment: String, vararg segments: String, size: Int = 0): FileTreeBuilder {
        val path = parent.resolve(firstSegment, *segments)
        Files.createFile(path)

        val contents = randomBytes(size)
        _contents[path] = contents
        Files.write(path, contents)

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

        val builder = FileTreeBuilder(path)
        builder.init()
        _contents.putAll(builder.contents)

        return builder
    }
}
