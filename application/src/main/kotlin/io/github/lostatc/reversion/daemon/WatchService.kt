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

package io.github.lostatc.reversion.daemon

import io.github.lostatc.reversion.OperatingSystem
import io.github.lostatc.reversion.getResourcePath

/**
 * A [Service] compatible with the current platform which executes [WatchDaemon].
 */
val watchService: Service = when (OperatingSystem.current()) {
    OperatingSystem.WINDOWS -> WindowsService(
        name = "io.github.lostatc.reversiond",
        executable = "reversiond",
        config = mapOf(
            "DisplayName" to "Reversion File Watcher",
            "Description" to "Watches a directory for changes and saves new versions of files."
        )
    )
    OperatingSystem.MAC -> LaunchdService(
        name = "io.github.lostatc.reversiond",
        propertyList = getResourcePath("/reversiond.plist")
    )
    OperatingSystem.LINUX -> SystemdService(
        name = "reversiond",
        serviceFile = getResourcePath("/reversiond.service")
    )
}
