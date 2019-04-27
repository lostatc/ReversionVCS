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
import io.github.lostatc.reversion.storage.DatabaseStorageProvider
import java.nio.file.Path

class DatabaseStorageProviderTest : StorageProviderTest {
    override lateinit var tempPath: Path

    override val provider: StorageProvider = DatabaseStorageProvider()
}