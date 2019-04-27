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

import io.github.lostatc.reversion.api.StorageProvider
import io.github.lostatc.reversion.api.UnsupportedFormatException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files

interface StorageProviderTest : FileSystemTest {
    /**
     * The storage provider to test.
     */
    val provider: StorageProvider

    @Test
    fun `cannot open incompatible repository`() {
        val repoPath = tempPath.resolve("repository")
        Files.createDirectories(repoPath)

        assertThrows<UnsupportedFormatException> {
            provider.openRepository(repoPath)
        }
    }

    @Test
    fun `cannot create repository that already exists`() {
        val repoPath = tempPath.resolve("repository")
        Files.createFile(repoPath)

        assertThrows<FileAlreadyExistsException> {
            provider.createRepository(repoPath)
        }
    }

    @Test
    fun `cannot import incompatible archive`() {
        val archivePath = tempPath.resolve("archive")
        val targetPath = tempPath.resolve("repository")
        Files.createFile(archivePath)

        assertThrows<UnsupportedFormatException> {
            provider.importRepository(archivePath, targetPath)
        }
    }

    @Test
    fun `created repository is valid`() {
        val repoPath = tempPath.resolve("repository")

        provider.createRepository(repoPath)

        assertTrue(provider.checkRepository(repoPath))
    }

    @Test
    fun `exported repository is valid`() {
        val archivePath = tempPath.resolve("archive")
        val repoPath = tempPath.resolve("repository")
        Files.createFile(archivePath)

        val repository = provider.createRepository(repoPath)
        repository.export(archivePath)

        assertTrue(provider.checkArchive(archivePath))
    }
}