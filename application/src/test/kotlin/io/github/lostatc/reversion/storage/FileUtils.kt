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

import io.github.lostatc.reversion.resolve
import java.nio.file.Files
import java.nio.file.Path

data class FileCreateContext(val parent: Path) {

    /**
     * Construct a new instance with the given path and children.
     */
    constructor(parent: Path, init: FileCreateContext.() -> Unit) : this(parent) {
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
    fun file(firstSegment: String, vararg segments: String, content: String = ""): FileCreateContext {
        val path = parent.resolve(firstSegment, *segments)
        Files.createFile(path)
        Files.writeString(path, content)
        return FileCreateContext(path)
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
        init: FileCreateContext.() -> Unit = { }
    ): FileCreateContext {
        val path = parent.resolve(firstSegment, *segments)
        Files.createDirectory(path)
        val context = FileCreateContext(path)
        context.init()
        return context
    }
}
