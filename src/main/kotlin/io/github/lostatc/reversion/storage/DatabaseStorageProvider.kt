/*
 * Copyright © 2019 Garrett Powell
 *
 * This file is part of reversion.
 *
 * reversion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * reversion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with reversion.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.lostatc.reversion.storage

import io.github.lostatc.reversion.api.Repository
import io.github.lostatc.reversion.api.RepositoryConfig
import io.github.lostatc.reversion.api.StorageProvider
import java.nio.file.Path

/**
 * A storage provider which stores data in de-duplicated blobs and metadata in a relational database.
 */
object DatabaseStorageProvider : StorageProvider {
    override val name: String = "De-duplicated repository"

    override val description: String = """
        A repository format that can de-duplicate data at the file or block level. Data cannot be easily recovered
        without this program.
    """.trimIndent()

    override val config: RepositoryConfig =
        RepositoryConfig(DatabaseRepository.attributes)

    override fun openRepository(path: Path, config: RepositoryConfig): DatabaseRepository =
        DatabaseRepository.open(path, config)

    override fun createRepository(path: Path, config: RepositoryConfig): Repository =
        DatabaseRepository.create(path, config)

    override fun importRepository(source: Path, target: Path, config: RepositoryConfig): DatabaseRepository =
        DatabaseRepository.import(source, target, config)

    override fun checkRepository(path: Path): Boolean =
        DatabaseRepository.check(path)
}
