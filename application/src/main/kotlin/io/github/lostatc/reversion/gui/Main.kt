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
import io.github.lostatc.reversion.gui.controllers.MainSceneController
import io.github.lostatc.reversion.gui.controls.sendNotification
import javafx.application.Application
import javafx.application.Platform
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.stage.Stage
import javafx.util.Duration
import org.slf4j.LoggerFactory

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
        // Log uncaught exceptions.
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val logger = LoggerFactory.getLogger("io.github.lostatc.reversion.gui")
            logger.error(throwable.message, throwable)

            System.err.println("Error: ${throwable.message}")

            throwable.message?.let { sendNotification(it) }
        }

        val rootLoader = FXMLLoader(this::class.java.getResource("/fxml/MainScene.fxml"))
        val rootNode = rootLoader.load<Pane>()
        val rootControl = rootLoader.getController<MainSceneController>()
        notificationBar.registerSnackbarContainer(rootNode)

        // We want to be able to re-open the primary stage after it is closed.
        Platform.setImplicitExit(false)
        currentPrimaryStage = primaryStage

        primaryStage.apply {
            title = "Reversion"
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
                rootControl.cleanup()

                // Don't close the stage, but hide it so that it can be re-opened later.
                it.consume()
                hide()
            }
            show()
        }
    }

    companion object {
        /**
         * The primary stage of the application, or `null` if it hasn't been started.
         */
        private var currentPrimaryStage: Stage? = null

        /**
         * Start the UI if it has not already been started or show the primary stage if it has.
         */
        fun launchOrShow() {
            try {
                launch(Reversion::class.java)
            } catch (e: IllegalStateException) {
                Platform.runLater { currentPrimaryStage?.show() }
            }
        }
    }
}
