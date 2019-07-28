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
import io.github.lostatc.reversion.processTemplate
import java.nio.file.Path

/**
 * Creates a [Service] which executes [WatchDaemon] and is compatible with the current platform.
 *
 * @param [workDirectory] The path of the working directory to watch for changes.
 */
fun createWatchService(workDirectory: Path): Service = when (OperatingSystem.current()) {
    OperatingSystem.WINDOWS -> WindowsService(
        name = "reversiond@$workDirectory",
        executable = "reversiond",
        args = listOf(workDirectory.toString()),
        config = mapOf(
            "DisplayName" to "Reversion File Watcher",
            "Description" to "Watches a directory for changes and saves new versions of files"
        )
    )
    OperatingSystem.MAC -> LaunchdService(
        propertyList = processTemplate(
            "launchd.plist.ftl",
            mapOf(
                "label" to "io.github.lostatc.reversion@$workDirectory",
                "executable" to "reversiond",
                "argument" to workDirectory.toString()
            )
        ),
        name = "io.github.lostatc.reversion@$workDirectory"
    )
    OperatingSystem.LINUX -> SystemdService(
        serviceFile = processTemplate(
            "systemd.service.ftl",
            mapOf(
                "description" to "Reversion File Watcher",
                "executable" to "reversiond"
            )
        ),
        serviceName = "reversiond",
        argument = workDirectory.toString()
    )
}
