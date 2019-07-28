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

import io.github.lostatc.reversion.HOME_DIRECTORY
import io.github.lostatc.reversion.getResourcePath
import io.github.lostatc.reversion.resolve
import java.nio.file.Files
import java.nio.file.Path

/**
 * A wrapper for managing a service.
 */
interface Service {
    /**
     * Starts the service and enables it to start on boot.
     *
     * @return `true` if the service was successfully started, `false` if it was already started.
     *
     * @throws [IllegalStateException] The service is not installed.
     */
    fun start(): Boolean

    /**
     * Stops the service and disables it from starting on boot.
     *
     * @return `true` if the service was successfully stopped, `false` if it wasn't running.
     *
     * @throws [IllegalStateException] The service is not installed.
     */
    fun stop(): Boolean

    /**
     * Returns whether the service is currently running.
     */
    fun isRunning(): Boolean

    /**
     * Installs the service.
     *
     * @return `true` if the service was successfully installed, `false` if it was already installed.
     */
    fun install(): Boolean

    /**
     * Uninstalls the service.
     *
     * @return `true` if the service was successfully uninstalled, `false` if it wasn't installed.
     */
    fun uninstall(): Boolean

    /**
     * Returns whether the service is currently installed.
     */
    fun isInstalled(): Boolean
}

/**
 * A [Service] that uses a Windows service.
 *
 * @param [name] The unique name of the service.
 * @param [executable] The name of the binary to execute when the service is started.
 * @param [args] The list of arguments to pass to the [executable].
 * @param [config] A map of config keys to their values.
 */
data class WindowsService(
    val name: String,
    val executable: String,
    val args: List<String>,
    val config: Map<String, String>
) : Service {
    override fun start(): Boolean {
        if (!isInstalled()) throw IllegalStateException("This service is not installed.")
        if (isRunning()) return false
        ProcessBuilder(elevateCommand, nssmCommand, "start", name).start()
        return true
    }

    override fun stop(): Boolean {
        if (!isInstalled()) throw IllegalStateException("This service is not installed.")
        if (!isRunning()) return false
        ProcessBuilder(elevateCommand, nssmCommand, "stop", name).start()
        return true
    }

    override fun isRunning(): Boolean {
        TODO("not implemented")
    }

    override fun install(): Boolean {
        if (isInstalled()) return false
        ProcessBuilder(elevateCommand, nssmCommand, "install", name, executable).start().waitFor()
        for ((property, value) in config.entries) {
            ProcessBuilder(nssmCommand, "set", name, property, value).start()
        }
        return true
    }

    override fun uninstall(): Boolean {
        if (!isInstalled()) return false
        ProcessBuilder(elevateCommand, nssmCommand, "remove", name).start()
        return true
    }

    override fun isInstalled(): Boolean {
        TODO("not implemented")
    }

    companion object {
        /**
         * The command to use for triggering a UAC prompt to elevate privileges.
         */
        private val elevateCommand: String = getResourcePath("/bin/elevate.exe").toString()

        /**
         * The command to use for managing services.
         */
        private val nssmCommand: String = getResourcePath("/bin/nssm.exe").toString()
    }
}

/**
 * A [Service] that uses a launchd agent.
 *
 * @param [propertyList] The contents of the property list file which describes the agent.
 * @param [name] The unique name of the service.
 */
// TODO: Add support for specifying an argument.
data class LaunchdService(val propertyList: String, val name: String) : Service {
    override fun start(): Boolean {
        TODO("not implemented")
    }

    override fun stop(): Boolean {
        TODO("not implemented")
    }

    override fun isRunning(): Boolean {
        TODO("not implemented")
    }

    override fun install(): Boolean {
        TODO("not implemented")
    }

    override fun uninstall(): Boolean {
        TODO("not implemented")
    }

    override fun isInstalled(): Boolean {
        TODO("not implemented")
    }

}

/**
 * A [Service] which uses a systemd user service.
 *
 * @param [serviceFile] The contents of the systemd unit file which describes the service.
 * @param [serviceName] The unique name of the service without the '.service' suffix.
 * @param [argument] The argument to pass to instantiate the service with, or `null` for no argument.
 */
data class SystemdService(val serviceFile: String, val serviceName: String, val argument: String?) : Service {
    /**
     * The full name of the service to pass to `systemctl`.
     */
    private val serviceSpecifier: String
        get() = if (argument == null) "$serviceName.service" else "$serviceName@$argument.service"

    /**
     * The path to install the systemd service file to.
     */
    private val installPath: Path
        get() = unitDirectory.resolve(if (argument == null) "$serviceName.service" else "$serviceName@.service")

    override fun start(): Boolean {
        if (!isInstalled()) throw IllegalStateException("This service is not installed.")
        if (isRunning()) return false
        ProcessBuilder("systemctl", "--user", "start", serviceSpecifier).start()
        ProcessBuilder("systemctl", "--user", "enable", serviceSpecifier).start()
        return true
    }

    override fun stop(): Boolean {
        if (!isInstalled()) throw IllegalStateException("This service is not installed.")
        if (!isRunning()) return false
        ProcessBuilder("systemctl", "--user", "stop", serviceSpecifier).start()
        ProcessBuilder("systemctl", "--user", "disable", serviceSpecifier).start()
        return true
    }

    override fun isRunning(): Boolean {
        val process = ProcessBuilder("systemctl", "--user", "is-active", serviceSpecifier).start()
        return process.waitFor() == 0
    }

    override fun install(): Boolean {
        if (isInstalled()) return false
        Files.writeString(installPath, serviceFile)
        return true
    }

    override fun uninstall(): Boolean = Files.deleteIfExists(installPath)

    override fun isInstalled(): Boolean = Files.isRegularFile(installPath)

    companion object {
        /**
         * The path of the directory containing systemd unit files for the current user.
         */
        private val unitDirectory: Path =
            HOME_DIRECTORY.resolve(".local", "share", "systemd", "user")
    }
}
