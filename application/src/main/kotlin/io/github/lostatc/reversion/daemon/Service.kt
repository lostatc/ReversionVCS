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
import io.github.lostatc.reversion.OperatingSystem
import io.github.lostatc.reversion.getResourcePath
import io.github.lostatc.reversion.resolve
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

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
 * Exit the process.
 *
 * This is necessary because `prunsrv.exe` expects a method with this signature.
 */
fun exitProcess(args: Array<String>) {
    exitProcess(0)
}

/**
 * A wrapper for managing a service.
 *
 * If the current platform is not supported, the constructor throws an [UnsupportedOperationException].
 */
interface Service {
    /**
     * Installs the service, starts it and configures it to start on boot.
     *
     * @throws [ServiceException] The service failed to install/start.
     */
    fun install()

    /**
     * Uninstalls and stops the service.
     *
     * @throws [ServiceException] The service failed to uninstall/stop.
     */
    fun uninstall()
}

/**
 * A [Service] that uses a Windows service.
 *
 * @param [name] The unique name of the service.
 * @param [className] The name of the class to execute.
 * @param [methodName] The name of the method to execute.
 * @param [displayName] The display name of the service.
 * @param [description] The description of the service.
 */
data class WindowsService(
    val name: String,
    val className: String,
    val methodName: String = "main",
    val displayName: String = name,
    val description: String = ""
) : Service {

    init {
        if (!OperatingSystem.WINDOWS.isCurrent) {
            throw UnsupportedOperationException("This implementation does not support the current platform.")
        }
    }

    override fun install() {
        ProcessBuilder(
            elevateCommand,
            serviceCommand,
            "install",
            name,
            "--Install", serviceCommand,
            "--Startup", "auto",
            "--DisplayName", displayName,
            "--Description", description,
            "--StartMode", "jvm",
            "--StartClass", className,
            "--StartMethod", methodName,
            "--StopMode", "jvm",
            "--StopClass", "io.github.lostatc.reversion.daemon.ServiceKt",
            "--StopMethod", "exitProcess"
        ).start().apply {
            onFail { throw ServiceException("The service failed to install.", it) }
        }

        ProcessBuilder(elevateCommand, serviceCommand, "start", name).start().apply {
            onFail { throw ServiceException("The service failed to start.", it) }
        }
    }

    override fun uninstall() {
        ProcessBuilder(elevateCommand, serviceCommand, "delete", name).start().apply {
            onFail { throw ServiceException("The service failed to uninstall.", it) }
        }
    }

    companion object {
        /**
         * The path of the binary for managing windows services.
         */
        private val serviceCommand: String = getResourcePath("/bin/prunsrv.exe").toString()

        /**
         * The path of the binary for elevating privileges.
         */
        private val elevateCommand: String = getResourcePath("/bin/elevate.exe").toString()
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

    override fun install() {
        if (!isInstalled()) {
            try {
                Files.createDirectories(agentPath.parent)
                Files.copy(propertyList, agentPath, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: IOException) {
                throw ServiceException("The service failed to install.", e)
            }
        }

        ProcessBuilder("launchctl", "bootstrap", "user/${getUid()}", agentPath.toString()).start().apply {
            onFail { throw ServiceException("The service failed to start.", it) }
        }
    }

    override fun uninstall() {
        ProcessBuilder("launchctl", "bootout", "user/${getUid()}", agentPath.toString()).start().apply {
            onFail { throw ServiceException("The service failed to stop.", it) }
        }

        try {
            Files.deleteIfExists(agentPath)
        } catch (e: IOException) {
            throw ServiceException("The service failed to uninstall.", e)
        }
    }

    private fun isInstalled(): Boolean = Files.isRegularFile(agentPath)

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

    override fun install() {
        if (!isInstalled()) {
            try {
                Files.createDirectories(unitPath.parent)
                Files.copy(serviceFile, unitPath, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: IOException) {
                throw ServiceException("The service failed to install.", e)
            }
        }

        ProcessBuilder("systemctl", "--user", "start", unitName).start().apply {
            onFail { throw ServiceException("The service failed to start.", it) }
        }
        ProcessBuilder("systemctl", "--user", "enable", unitName).start().apply {
            onFail { throw ServiceException("The service failed to start.", it) }
        }
    }

    override fun uninstall() {
        ProcessBuilder("systemctl", "--user", "stop", unitName).start().apply {
            onFail { throw ServiceException("The service failed to stop.", it) }
        }
        ProcessBuilder("systemctl", "--user", "disable", unitName).start().apply {
            onFail { throw ServiceException("The service failed to stop.", it) }
        }

        try {
            Files.deleteIfExists(unitPath)
        } catch (e: IOException) {
            throw ServiceException("The service failed to uninstall.", e)
        }
    }

    private fun isInstalled(): Boolean = Files.isRegularFile(unitPath)

    companion object {
        /**
         * The path of the directory containing systemd unit files for the current user.
         */
        private val unitDirectory: Path =
            HOME_DIRECTORY.resolve(".local", "share", "systemd", "user")
    }
}
