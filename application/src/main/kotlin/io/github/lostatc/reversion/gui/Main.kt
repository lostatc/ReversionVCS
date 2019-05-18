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
import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage

class Reversion : Application() {
    override fun start(primaryStage: Stage) {
        // TODO: Change
        val rootLoader = FXMLLoader(this::class.java.getResource("/fxml/VersionManager.fxml"))
        val rootNode = rootLoader.load<Parent>()
        val rootControl = rootLoader.getController<VersionManagerController>()

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
