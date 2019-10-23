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

package io.github.lostatc.reversion.gui.controllers

import com.jfoenix.controls.JFXSpinner
import io.github.lostatc.reversion.gui.ActorEvent
import io.github.lostatc.reversion.gui.models.StorageModel.storageActor
import javafx.fxml.FXML
import javafx.scene.layout.Pane

/**
 * The controller for the main scene of the program.
 */
class MainSceneController {

    @FXML
    private lateinit var workDirectoryManager: Pane

    @FXML
    private lateinit var workDirectoryManagerController: WorkDirectoryManagerController

    @FXML
    private lateinit var versionManager: Pane

    @FXML
    private lateinit var versionManagerController: VersionManagerController

    /**
     * A spinner which indicates that something is processing in the background.
     */
    @FXML
    private lateinit var processingSpinner: JFXSpinner

    @FXML
    fun initialize() {
        // Display the spinner whenever something is processing in the background.
        storageActor.addEventHandler(ActorEvent.BUSY) {
            processingSpinner.isVisible = true
        }
        storageActor.addEventHandler(ActorEvent.WAITING) {
            processingSpinner.isVisible = false
        }
    }

    /**
     * This function is run before the program exits.
     */
    fun cleanup() {
        versionManagerController.cleanup()
    }
}
