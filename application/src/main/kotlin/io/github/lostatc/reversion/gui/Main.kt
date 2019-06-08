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

package io.github.lostatc.reversion.gui

import com.jfoenix.assets.JFoenixResources
import com.jfoenix.controls.JFXSnackbar
import io.github.lostatc.reversion.gui.controls.sendNotification
import io.github.lostatc.reversion.gui.mvc.VersionManagerController
import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.stage.Stage
import javafx.util.Duration
import org.slf4j.LoggerFactory

class Reversion : Application() {
    override fun start(primaryStage: Stage) {
        val rootLoader = FXMLLoader(this::class.java.getResource("/fxml/VersionManager.fxml"))
        val rootNode = rootLoader.load<Pane>()
        val rootControl = rootLoader.getController<VersionManagerController>()
        val snackbar = JFXSnackbar(rootNode)

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val logger = LoggerFactory.getLogger("io.github.lostatc.reversion.gui")
            logger.error(throwable.message, throwable)

            System.err.println("Error: ${throwable.message}")

            throwable.message?.let { snackbar.sendNotification(it, Duration.seconds(5.0)) }
        }

        primaryStage.apply {
            title = "Reversion"
            scene = Scene(rootNode).apply {
                stylesheets.addAll(
                    JFoenixResources.load("css/jfoenix-fonts.css").toExternalForm(),
                    JFoenixResources.load("css/jfoenix-design.css").toExternalForm(),
                    this::class.java.getResource("/css/application.css").toExternalForm()
                )
            }
            width = 900.0
            height = 600.0
            minWidth = 450.0
            minHeight = 300.0
            setOnCloseRequest {
                rootControl.cleanup()
            }
            show()
        }
    }
}

fun main(args: Array<String>) {
    Application.launch(Reversion::class.java, *args)
}
