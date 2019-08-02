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

package io.github.lostatc.reversion.daemon

import io.github.lostatc.reversion.HOME_DIRECTORY
import io.github.lostatc.reversion.OperatingSystem
import io.github.lostatc.reversion.getResourcePath
import io.github.lostatc.reversion.resolve
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * An exception which signals that a process returned a non-zero exit code.
 *
 * @param [process] The process which returned a non-zero exit code.
 */
data class ProcessFailedException(
    override val message: String,
    val process: Process,
    override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * An exception which signals that there was a problem interacting with a service.
 */
data class ServiceException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * Waits for this process to complete and returns its stdout as a string.
 */
private fun Process.getOutput(): String = IOUtils.toString(inputStream, Charset.defaultCharset()).trim()

/**
 * Waits for this process to complete and returns its stderr as a string.
 */
private fun Process.getError(): String = IOUtils.toString(errorStream, Charset.defaultCharset()).trim()

/**
 * Waits for this process to complete and executes the [handler] if it returns with a non-zero exit code.
 *
 * @param [handler] A function which is passed an exception providing information about the process.
 */
private fun Process.onFail(handler: (ProcessFailedException) -> Unit) {
    if (waitFor() != 0) handler(ProcessFailedException(message = getError(), process = this))
}

/**
 * A wrapper for managing a service.
 *
 * If the current platform is not supported, the constructor throws an [UnsupportedOperationException].
 */
interface Service {
    /**
     * Starts the service and configures it to start on boot.
     *
     * @throws [IllegalStateException] The service is not installed.
     * @throws [ServiceException] The service failed to start.
     */
    fun start()

    /**
     * Stops the service and configures it to not start on boot.
     *
     * @throws [IllegalStateException] The service is not installed.
     * @throws [ServiceException] The service failed to stop.
     */
    fun stop()

    /**
     * Installs the service.
     *
     * @return `true` if the service was successfully installed, `false` if it was already installed.
     *
     * @throws [ServiceException] The service failed to install.
     */
    fun install(): Boolean

    /**
     * Uninstalls the service.
     *
     * @return `true` if the service was successfully uninstalled, `false` if it wasn't installed.
     *
     * @throws [ServiceException] The service failed to uninstall.
     */
    fun uninstall(): Boolean

    /**
     * Returns whether the service is installed.
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
    val args: List<String> = emptyList(),
    val config: Map<String, String> = emptyMap()
) : Service {

    init {
        if (!OperatingSystem.WINDOWS.isCurrent) {
            throw UnsupportedOperationException("This implementation does not support the current platform.")
        }
    }

    override fun start() {
        if (!isInstalled()) throw IllegalStateException("The service is not installed.")

        ProcessBuilder(nssmCommand, "start", name).start().apply {
            onFail { throw ServiceException("The service failed to start.", it) }
        }
    }

    override fun stop() {
        if (!isInstalled()) throw IllegalStateException("The service is not installed.")

        ProcessBuilder(nssmCommand, "stop", name).start().apply {
            onFail { throw ServiceException("The service failed to stop.", it) }
        }
    }

    override fun install(): Boolean {
        if (isInstalled()) return false

        ProcessBuilder(nssmCommand, "install", name, executable, *args.toTypedArray()).start().apply {
            onFail { throw ServiceException("The service failed to install.", it) }
        }

        for ((property, value) in config.entries) {
            ProcessBuilder(nssmCommand, "set", name, property, value).start().apply {
                onFail { throw ServiceException("The service failed to install.", it) }
            }
        }

        return true
    }

    override fun uninstall(): Boolean {
        if (!isInstalled()) return false

        ProcessBuilder(nssmCommand, "remove", name, "confirm").start().apply {
            onFail { throw ServiceException("The service failed to uninstall.", it) }
        }

        return true
    }

    override fun isInstalled(): Boolean = ProcessBuilder("sc", "query", name).start().waitFor() == 0

    companion object {
        /**
         * The command to use for managing services.
         *
         * This command will automatically trigger a UAC prompt if executed without privileges.
         */
        private val nssmCommand: String = getResourcePath("/bin/nssm.exe").toString()
    }
}

/**
 * A [Service] that uses a launchd agent.
 *
 * @param [name] The unique name of the service.
 * @param [propertyList] The property list file which describes the agent.
 */
data class LaunchdService(val name: String, val propertyList: Path) : Service {

    init {
        if (!OperatingSystem.MAC.isCurrent) {
            throw UnsupportedOperationException("This implementation does not support the current platform.")
        }
    }

    /**
     * The path to install the plist file to.
     */
    private val agentPath: Path = agentDirectory.resolve("$name.plist")

    override fun start() {
        if (!isInstalled()) throw IllegalStateException("The service is not installed.")

        ProcessBuilder("launchctl", "bootstrap", "user/${getUid()}", agentPath.toString()).start().apply {
            onFail { throw ServiceException("The service failed to start.", it) }
        }
    }

    override fun stop() {
        if (!isInstalled()) throw IllegalStateException("The service is not installed.")

        ProcessBuilder("launchctl", "bootout", "user/${getUid()}", agentPath.toString()).start().apply {
            onFail { throw ServiceException("The service failed to stop.", it) }
        }
    }

    override fun install(): Boolean {
        if (isInstalled()) return false

        try {
            Files.createDirectories(agentPath.parent)
            Files.copy(propertyList, agentPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            throw ServiceException("The service failed to install.", e)
        }

        return true
    }

    override fun uninstall(): Boolean {
        if (!isInstalled()) return false

        try {
            Files.deleteIfExists(agentPath)
        } catch (e: IOException) {
            throw ServiceException("The service failed to uninstall.", e)
        }

        return true
    }

    override fun isInstalled(): Boolean = Files.isRegularFile(agentPath)

    companion object {
        /**
         * The path of the directory containing user agents.
         */
        private val agentDirectory: Path = HOME_DIRECTORY.resolve("Library", "LaunchAgents")

        /**
         * Returns the current user's UID as a string.
         */
        private fun getUid(): String = ProcessBuilder("id", "-u").start().getOutput()
    }
}

/**
 * A [Service] which uses a systemd user service.
 *
 * @param [name] The unique name of the service.
 * @param [serviceFile] The path of the systemd unit file which describes the service.
 */
data class SystemdService(val name: String, val serviceFile: Path) : Service {

    init {
        if (!OperatingSystem.LINUX.isCurrent) {
            throw UnsupportedOperationException("This implementation does not support the current platform.")
        }
    }

    /**
     * The full name of the service to pass to `systemctl`.
     */
    private val unitName: String
        get() = name.removeSuffix(".service") + ".service"

    /**
     * The path to install the systemd service file to.
     */
    private val unitPath: Path
        get() = unitDirectory.resolve(unitName)

    override fun start() {
        if (!isInstalled()) throw IllegalStateException("The service is not installed.")

        ProcessBuilder("systemctl", "--user", "start", unitName).start().apply {
            onFail { throw ServiceException("The service failed to start.", it) }
        }
        ProcessBuilder("systemctl", "--user", "enable", unitName).start().apply {
            onFail { throw ServiceException("The service failed to start.", it) }
        }
    }

    override fun stop() {
        if (!isInstalled()) throw IllegalStateException("The service is not installed.")

        ProcessBuilder("systemctl", "--user", "stop", unitName).start().apply {
            onFail { throw ServiceException("The service failed to stop.", it) }
        }
        ProcessBuilder("systemctl", "--user", "disable", unitName).start().apply {
            onFail { throw ServiceException("The service failed to stop.", it) }
        }
    }

    override fun install(): Boolean {
        if (isInstalled()) return false

        try {
            Files.createDirectories(unitPath.parent)
            Files.copy(serviceFile, unitPath, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            throw ServiceException("The service failed to install.", e)
        }

        return true
    }

    override fun uninstall(): Boolean {
        if (!isInstalled()) return false

        try {
            Files.deleteIfExists(unitPath)
        } catch (e: IOException) {
            throw ServiceException("The service failed to uninstall.", e)
        }

        return true
    }

    override fun isInstalled(): Boolean = Files.isRegularFile(unitPath)

    companion object {
        /**
         * The path of the directory containing systemd unit files for the current user.
         */
        private val unitDirectory: Path =
            HOME_DIRECTORY.resolve(".local", "share", "systemd", "user")
    }
}
