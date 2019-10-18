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

import io.github.lostatc.reversion.api.Config
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseFuseFileSystemTest : FuseFileSystemTest {
    override val provider: DatabaseStorageProvider = DatabaseStorageProvider()

    override val config: Config = DatabaseStorageProvider().getConfig()

    override lateinit var workPath: Path

    override lateinit var mountPath: Path

    override lateinit var fileSystem: FuseFileSystem
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockDeduplicatedDatabaseFuseFileSystemTest : FuseFileSystemTest {
    override val provider: DatabaseStorageProvider = DatabaseStorageProvider()

    override val config: Config = DatabaseStorageProvider().run {
        val config = getConfig()
        config[DatabaseRepository.blockSizeProperty] = "2"
        config
    }

    override lateinit var workPath: Path

    override lateinit var mountPath: Path

    override lateinit var fileSystem: FuseFileSystem
}
