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

package io.github.lostatc.reversion.gui

import io.github.lostatc.reversion.getResourceUri
import javafx.application.Platform
import javafx.stage.Stage
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import kotlin.system.exitProcess

/**
 * The icon for the application.
 */
private val applicationIcon: Image by lazy {
    Toolkit.getDefaultToolkit().getImage(getResourceUri("/icon.png").toURL())
}

/**
 * The tray icon to show that the program is running.
 */
private val trayIcon: TrayIcon by lazy {
    val menu = PopupMenu().apply {
        add(
            MenuItem("Open").apply {
                addActionListener { Platform.runLater { Reversion().start(Stage()) } }
            }
        )
        add(
            MenuItem("Quit").apply {
                addActionListener { exitProcess(0) }
            }
        )
    }

    TrayIcon(applicationIcon, "Reversion", menu)
}

/**
 * Show a system tray icon for the application if the system tray is supported on this platform.
 */
fun showTrayIcon() {
    if (SystemTray.isSupported()) {
        SystemTray.getSystemTray().add(trayIcon)
    }
}
