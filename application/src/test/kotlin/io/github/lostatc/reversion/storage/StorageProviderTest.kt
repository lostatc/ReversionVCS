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

import io.github.lostatc.reversion.api.StorageProvider
import io.github.lostatc.reversion.api.UnsupportedFormatException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path

interface StorageProviderTest {
    /**
     * A temporary directory to be created before each test.
     */
    val tempPath: Path

    /**
     * The storage provider to test.
     */
    val provider: StorageProvider

    @Test
    fun `incompatible repository is not opened`() {
        val repoPath = tempPath.resolve("repository")
        Files.createDirectories(repoPath)

        assertThrows<UnsupportedFormatException> {
            provider.openRepository(repoPath)
        }
    }

    @Test
    fun `already existing repository is not created`() {
        val repoPath = tempPath.resolve("repository")
        Files.createFile(repoPath)

        assertThrows<FileAlreadyExistsException> {
            provider.createRepository(repoPath)
        }
    }

    @Test
    fun `created repository is valid`() {
        val repoPath = tempPath.resolve("repository")

        provider.createRepository(repoPath)

        assertTrue(provider.checkRepository(repoPath))
    }
}