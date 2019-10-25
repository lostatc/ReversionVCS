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

import io.github.lostatc.reversion.api.Configurator
import io.github.lostatc.reversion.api.Form
import io.github.lostatc.reversion.api.io.Chunker
import io.github.lostatc.reversion.api.io.FixedSizeChunker
import io.github.lostatc.reversion.api.io.ZpaqChunker
import io.github.lostatc.reversion.api.storage.OpenAttempt
import io.github.lostatc.reversion.api.storage.Repository
import io.github.lostatc.reversion.api.storage.StorageProvider
import io.github.lostatc.reversion.gui.controls.ConfiguratorForm
import java.nio.file.Path

/**
 * The [Chunker] to use to optimize for performance.
 */
val PERFORMANCE_CHUNKER: Chunker = FixedSizeChunker(Long.MAX_VALUE)

/**
 * The [Chunker] to use to optimize for saving storage space.
 */
val STORAGE_CHUNKER: Chunker = ZpaqChunker(22)

/**
 * A storage provider which stores data in de-duplicated blobs and metadata in a relational database.
 */
/**
 * A storage provider which stores data in de-duplicated blobs and metadata in a relational database.
 */
class DatabaseStorageProvider : StorageProvider {
    override val name: String = "De-duplicated repository"

    override val description: String = """
        A repository format that can de-duplicate data at the file or block level. Data cannot be easily recovered
        without this program.
    """.trimIndent()

    override fun configure(): Form<Configurator> = ConfiguratorForm()

    override fun openRepository(path: Path): OpenAttempt<Repository> =
        DatabaseRepository.open(path)

    override fun createRepository(path: Path, configurator: Configurator): DatabaseRepository =
        DatabaseRepository.create(path, configurator)

    override fun checkRepository(path: Path): Boolean =
        DatabaseRepository.checkRepository(path)

}
