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

package storage

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

/**
 * An interface for classes that need to test access to the file system.
 *
 * This interface creates a temporary directory before each test and deletes it after each test.
 */
interface FileSystemTest {
    /**
     * The path of the temporary directory.
     */
    var tempPath: Path

    @BeforeEach
    fun createTempDirectory() {
        tempPath = Files.createTempDirectory("reversion-")
    }

    @AfterEach
    fun deleteTempDirectory() {
        Files.walk(tempPath)
            .asSequence()
            .sortedDescending()
            .forEach { Files.deleteIfExists(it) }
    }
}
