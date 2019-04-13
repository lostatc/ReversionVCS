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

package io.github.lostatc.reversion

import io.github.lostatc.reversion.api.StorageProvider
import io.github.lostatc.reversion.storage.DatabaseStorageProvider
import net.harawata.appdirs.AppDirs
import net.harawata.appdirs.AppDirsFactory
import java.nio.file.Path
import java.nio.file.Paths

private val appDirs: AppDirs = AppDirsFactory.getInstance()

/**
 * The environment variable which stores the path of the default repository.
 */
private const val DEFAULT_REPO_ENV: String = "REVERSION_DEFAULT_REPO"

/**
 * The path of the program's data directory.
 */
val DATA_DIR: Path = Paths.get(appDirs.getUserDataDir("Reversion", null, "Garrett Powell"))

/**
 * A pattern representing the path of the file that errors are logged to.
 */
val LOG_FILE_PATTERN: String = DATA_DIR.resolve("errors.log").toUri().path.toString()

/**
 * The path of the default repository.
 */
val DEFAULT_REPO: Path = System.getenv(DEFAULT_REPO_ENV)?.let { Paths.get(it) } ?: DATA_DIR.resolve("repository")

/**
 * The default storage provider.
 */
val DEFAULT_PROVIDER: StorageProvider = DatabaseStorageProvider()
