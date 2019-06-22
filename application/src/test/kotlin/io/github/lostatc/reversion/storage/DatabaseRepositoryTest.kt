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

import io.github.lostatc.reversion.api.Checksum
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Deletes the blob storing data for the given [file].
 */
private fun DatabaseRepository.deleteBlob(file: Path) {
    val checksum = Checksum.fromFile(file, hashAlgorithm)
    Files.delete(getBlobPath(checksum))
}

/**
 * Corrupts the blob storing data for the given [file].
 */
private fun DatabaseRepository.corruptBlob(file: Path) {
    val checksum = Checksum.fromFile(file, hashAlgorithm)
    Files.writeString(getBlobPath(checksum), "corrupt data")
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseRepositoryTest : RepositoryTest {
    override lateinit var repository: DatabaseRepository

    lateinit var workPath: Path

    @BeforeEach
    fun createRepository(@TempDir tempPath: Path) {
        val repoPath = tempPath.resolve("repository")
        repository = DatabaseStorageProvider().createRepository(repoPath)
    }

    @BeforeEach
    fun createFiles(@TempDir tempPath: Path) {
        workPath = tempPath.resolve("work")

        FileCreateContext(workPath) {
            file("a", content = "apple")
            file("b", content = "banana")
            directory("c") {
                file("a", content = "orange")
            }
        }
    }

    @Test
    fun `identify corrupt files`() {
        val timeline = repository.createTimeline()
        val snapshot = timeline.createSnapshot(listOf(Paths.get("a"), Paths.get("b"), Paths.get("c", "a")), workPath)

        assertTrue(repository.verify(workPath).isValid)

        repository.deleteBlob(workPath.resolve("a"))
        repository.corruptBlob(workPath.resolve("c", "a"))

        val corruptVersions = setOf(
            snapshot.versions.getValue(Paths.get("a")),
            snapshot.versions.getValue(Paths.get("c", "a"))
        )
        val report = repository.verify(workPath)

        assertFalse(report.isValid)
        assertEquals(corruptVersions, report.corrupt)
    }

    @Test
    fun `distinguish files that can be repaired vs deleted`() {
        val timeline = repository.createTimeline()
        val snapshot = timeline.createSnapshot(listOf(Paths.get("a"), Paths.get("b"), Paths.get("c", "a")), workPath)

        assertTrue(repository.verify(workPath).isValid)

        repository.deleteBlob(workPath.resolve("a"))
        repository.deleteBlob(workPath.resolve("b"))

        // Delete the original file so that it cannot be repaired.
        Files.delete(workPath.resolve("a"))

        val deletedVersions = setOf(snapshot.versions.getValue(Paths.get("a")))
        val repairedVersions = setOf(snapshot.versions.getValue(Paths.get("b")))
        val report = repository.verify(workPath)

        assertEquals(deletedVersions, report.deleted)
        assertEquals(repairedVersions, report.repaired)
    }

    @Test
    fun `corrupt data is repaired`() {
        val paths = setOf(Paths.get("a"), Paths.get("b"), Paths.get("c", "a"))
        val timeline = repository.createTimeline()
        val snapshot = timeline.createSnapshot(paths, workPath)

        repository.deleteBlob(workPath.resolve("a"))
        repository.corruptBlob(workPath.resolve("b"))

        repository.verify(workPath).repair()

        assertTrue(repository.verify(workPath).isValid)
        assertEquals(paths, snapshot.versions.keys)
    }

    @Test
    fun `corrupt data is removed`() {
        val paths = setOf(Paths.get("a"), Paths.get("b"), Paths.get("c", "a"))
        val timeline = repository.createTimeline()
        val snapshot = timeline.createSnapshot(paths, workPath)

        repository.deleteBlob(workPath.resolve("a"))
        repository.corruptBlob(workPath.resolve("b"))

        // Delete the original files so they cannot be repaired.
        Files.delete(workPath.resolve("a"))
        Files.delete(workPath.resolve("b"))

        repository.verify(workPath).repair()

        assertTrue(repository.verify(workPath).isValid)
        assertEquals(setOf(Paths.get("c", "a")), snapshot.versions.keys)
    }
}
