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

import io.github.lostatc.reversion.api.Checksum
import io.github.lostatc.reversion.api.IntegrityReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

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
    fun `verify the integrity of the repository`() {
        val timeline = repository.createTimeline("test")
        val snapshot = timeline.createSnapshot(listOf(Paths.get("a"), Paths.get("b"), Paths.get("c", "a")), workPath)

        assertTrue(repository.verify().isValid)

        Files.delete(repository.getBlobPath((Checksum.fromFile(workPath.resolve("a"), repository.hashAlgorithm))))
        Files.writeString(
            repository.getBlobPath(Checksum.fromFile(workPath.resolve("c", "a"), repository.hashAlgorithm)),
            "corrupt data"
        )

        val expectedReport = IntegrityReport(
            setOf(snapshot.versions.getValue(Paths.get("a")), snapshot.versions.getValue(Paths.get("c", "a")))
        )

        assertEquals(expectedReport, repository.verify())

    }
}