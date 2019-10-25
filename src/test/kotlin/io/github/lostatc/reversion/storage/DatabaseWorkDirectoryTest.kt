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

import io.github.lostatc.reversion.TEST_CHUNK_BITS
import io.github.lostatc.reversion.TEST_CHUNK_SIZE
import io.github.lostatc.reversion.api.Configurator
import io.github.lostatc.reversion.api.io.FixedSizeChunker
import io.github.lostatc.reversion.api.io.ZpaqChunker
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseWorkDirectoryTest : WorkDirectoryTest {
    override val provider = DatabaseStorageProvider()

    override val configurator: Configurator = Configurator.Default

    override lateinit var workPath: Path

    override lateinit var workDirectory: WorkDirectory

    override lateinit var contents: Map<Path, ByteArray>
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FixedSizeDatabaseWorkDirectoryTest : WorkDirectoryTest {
    override val provider = DatabaseStorageProvider()

    override val configurator: Configurator = Configurator {
        it[DatabaseRepository.chunkerProperty] = FixedSizeChunker(TEST_CHUNK_SIZE)
    }

    override lateinit var workPath: Path

    override lateinit var workDirectory: WorkDirectory

    override lateinit var contents: Map<Path, ByteArray>
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZpaqDatabaseWorkDirectoryTest : WorkDirectoryTest {
    override val provider = DatabaseStorageProvider()

    override val configurator: Configurator = Configurator {
        it[DatabaseRepository.chunkerProperty] = ZpaqChunker(TEST_CHUNK_BITS)
    }

    override lateinit var workPath: Path

    override lateinit var workDirectory: WorkDirectory

    override lateinit var contents: Map<Path, ByteArray>
}
