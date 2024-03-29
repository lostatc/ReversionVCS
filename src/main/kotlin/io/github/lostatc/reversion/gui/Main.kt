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

import com.jfoenix.assets.JFoenixResources
import com.jfoenix.controls.JFXSnackbar
import io.github.lostatc.reversion.getResourceUri
import io.github.lostatc.reversion.gui.controllers.MainSceneController
import io.github.lostatc.reversion.gui.controls.sendNotification
import javafx.application.Application
import javafx.application.Platform
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.layout.Pane
import javafx.stage.Stage
import javafx.util.Duration

/**
 * A snack bar which displays notifications across the program.
 */
private val notificationBar: JFXSnackbar = JFXSnackbar()

/**
 * Send a notification to be displayed.
 */
fun sendNotification(message: String) {
    notificationBar.sendNotification(message, Duration.seconds(5.0))
}

/**
 * The GUI application.
 */
class Reversion : Application() {
    override fun start(primaryStage: Stage) {
        val rootLoader = FXMLLoader(this::class.java.getResource("/fxml/views/MainScene.fxml"))
        val rootNode = rootLoader.load<Pane>()
        val rootControl = rootLoader.getController<MainSceneController>()
        notificationBar.registerSnackbarContainer(rootNode)

        // We want to be able to re-open the primary stage after it is closed.
        Platform.setImplicitExit(false)

        primaryStage.apply {
            title = "Reversion"
            icons.add(Image(getResourceUri("/icon.png").toString()))
            scene = Scene(rootNode).apply {
                stylesheets.addAll(
                    this::class.java.getResource("/css/fonts.css").toExternalForm(),
                    JFoenixResources.load("css/jfoenix-design.css").toExternalForm(),
                    this::class.java.getResource("/css/application.css").toExternalForm()
                )
            }
            width = 900.0
            height = 600.0
            minWidth = 450.0
            minHeight = 300.0
            setOnCloseRequest {
                notificationBar.unregisterSnackbarContainer(rootNode)
                rootControl.cleanup()
            }
            show()
        }
    }
}
