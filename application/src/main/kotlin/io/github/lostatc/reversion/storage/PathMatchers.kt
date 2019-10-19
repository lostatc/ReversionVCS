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

import io.github.lostatc.reversion.OperatingSystem
import org.apache.commons.io.FileUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher

/**
 * Returns a [PathMatcher] which attempts to relativize the path against [base].
 */
private fun relativeMatcher(base: Path, matcher: (Path) -> Boolean): PathMatcher = PathMatcher {
    val relativePath = when {
        it.startsWith(base) -> base.relativize(it)
        !it.isAbsolute -> it
        else -> return@PathMatcher false
    }
    matcher(relativePath)
}

/**
 * Returns a [PathMatcher] which attempts to resolve the path against [base].
 */
private fun absoluteMatcher(base: Path, matcher: (Path) -> Boolean): PathMatcher = PathMatcher {
    val absolutePath = when {
        it.startsWith(base) -> it
        !it.isAbsolute -> base.resolve(it)
        else -> return@PathMatcher false
    }
    matcher(absolutePath)
}

/**
 * A [PathMatcher] used for ignoring files.
 */
interface IgnoreMatcher {
    /**
     * A description of the matcher to display in the UI.
     */
    val description: String

    /**
     * Returns a [PathMatcher] for matching paths in the given [workDirectory].
     */
    fun toPathMatcher(workDirectory: Path): PathMatcher
}

/**
 * A [PathMatcher] that matches paths matched by any of the given [matchers].
 */
data class MultiPathMatcher(val matchers: Iterable<PathMatcher>) : PathMatcher {
    override fun matches(path: Path): Boolean = matchers.any { it.matches(path) }
}

/**
 * A [PathMatcher] that matches paths which start with the relative [prefix].
 */
data class PrefixIgnoreMatcher(val prefix: Path) : IgnoreMatcher {
    override val description: String
        get() = prefix.toString()

    override fun toPathMatcher(workDirectory: Path): PathMatcher = if (prefix.isAbsolute) {
        absoluteMatcher(workDirectory) { it.startsWith(prefix) }
    } else {
        relativeMatcher(workDirectory) { it.startsWith(prefix) }
    }
}

/**
 * A [PathMatcher] that matches paths which match the relative glob [pattern].
 */
data class GlobIgnoreMatcher(val pattern: String) : IgnoreMatcher {
    override val description: String
        get() = "Files matching the glob '$pattern'"

    override fun toPathMatcher(workDirectory: Path): PathMatcher {
        val matcher = workDirectory.fileSystem.getPathMatcher("glob:$pattern")
        return relativeMatcher(workDirectory) { matcher.matches(it) }
    }
}

/**
 * A [PathMatcher] that matches paths which match the relative regex [pattern].
 */
data class RegexIgnoreMatcher(val pattern: String) : IgnoreMatcher {
    override val description: String
        get() = "Files matching the regex '$pattern'"

    override fun toPathMatcher(workDirectory: Path): PathMatcher {
        val matcher = workDirectory.fileSystem.getPathMatcher("regex:$pattern")
        return relativeMatcher(workDirectory) { matcher.matches(it) }
    }
}

/**
 * A [PathMatcher] that matches paths which are larger than [size] bytes.
 */
data class SizeIgnoreMatcher(val size: Long) : IgnoreMatcher {
    override val description: String
        get() = "Files larger than ${FileUtils.byteCountToDisplaySize(size)}"

    override fun toPathMatcher(workDirectory: Path): PathMatcher = absoluteMatcher(workDirectory) {
        // Ignore symlinks because [Files.size] may try to follow them, which we don't want.
        if (Files.isRegularFile(it)) Files.size(it) > size else false
    }
}

/**
 * A [PathMatcher] that matches paths with the given file [extension].
 */
data class ExtensionIgnoreMatcher(val extension: String) : IgnoreMatcher {
    override val description: String
        get() = "Files with the extension '$extension'"

    override fun toPathMatcher(workDirectory: Path): PathMatcher = relativeMatcher(workDirectory) {
        it.fileName
            .toString()
            .substringAfterLast('.')
            .equals(extension.trimStart('.'), ignoreCase = true)
    }
}

/**
 * Categories of files to ignore.
 */
enum class IgnoreCategory(val description: String) {
    HIDDEN("Hidden files"),
    CACHE("Cache files"),
    APPLICATION("Application data")
}

/**
 * A [PathMatcher] that matches paths belonging to a certain [category].
 */
data class CategoryIgnoreMatcher(val category: IgnoreCategory) : IgnoreMatcher {
    override val description: String
        get() = category.description

    override fun toPathMatcher(workDirectory: Path): PathMatcher = when (category) {
        IgnoreCategory.HIDDEN -> absoluteMatcher(workDirectory) {
            var current: Path? = it
            while (current != null) {
                if (Files.isHidden(current)) return@absoluteMatcher true
                current = current.parent
            }
            false
        }
        IgnoreCategory.CACHE -> absoluteMatcher(workDirectory) {
            it.startsWith(OperatingSystem.current.cacheDirectory)
        }
        IgnoreCategory.APPLICATION -> absoluteMatcher(workDirectory) {
            val os = OperatingSystem.current
            it.startsWith(os.configDirectory) || it.startsWith(os.dataDirectory)
        }
    }
}
