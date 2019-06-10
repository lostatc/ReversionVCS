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

package io.github.lostatc.reversion.gui.mvc

import com.jfoenix.controls.JFXComboBox
import com.jfoenix.controls.JFXListView
import com.jfoenix.controls.JFXTextField
import io.github.lostatc.reversion.gui.MappedList
import io.github.lostatc.reversion.gui.controls.Card
import io.github.lostatc.reversion.gui.controls.ListItem
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.layout.Pane
import javafx.stage.DirectoryChooser
import java.time.temporal.ChronoUnit

/**
 * The controller for the view that is used to manage working directories.
 */
class WorkDirectoryManagerController {
    /**
     * The text field where the user inputs the number of versions to keep for a new cleanup policy.
     */
    @FXML
    private lateinit var versionsTextField: JFXTextField

    /**
     * The text field where the user inputs the amount of time to keep files for for a new cleanup policy.
     */
    @FXML
    private lateinit var timeField: JFXTextField

    /**
     * The text field where the user inputs the unit of time to use for for a new cleanup policy.
     */
    @FXML
    private lateinit var unitComboBox: JFXComboBox<String>

    /**
     * The list view that displays the user's working directories.
     */
    @FXML
    private lateinit var workDirectoryList: JFXListView<Card>

    /**
     * The list view that displays the working directory's cleanup policies.
     */
    @FXML
    private lateinit var cleanupPolicyList: JFXListView<Label>

    /**
     * The pane containing information about the working directory.
     */
    @FXML
    private lateinit var infoPane: Pane

    private val model: WorkDirectoryManagerModel = WorkDirectoryManagerModel()

    @FXML
    fun initialize() {
        unitComboBox.items.setAll("", "Seconds", "Minutes", "Hours", "Days", "Weeks", "Months")

        // Bind the selected working directory in the model to the selected working directory in the view.
        workDirectoryList.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            model.selected = model.workDirectories.getOrNull(newValue.toInt())
        }

        // Bind the list of working directories in the view to the model.
        workDirectoryList.items = MappedList(model.workDirectories) {
            Card().apply {
                title = it.path.fileName.toString()
                subtitle = it.path.toString()
            }
        }

        model.loadWorkDirectories()

        // Make the version information pane initially invisible.
        infoPane.isVisible = false

        model.selectedProperty.addListener { _, _, newValue ->
            if (newValue == null) {
                infoPane.isVisible = false
            } else {
                // Bind the list of cleanup policies it the view to the model.
                cleanupPolicyList.items = MappedList(newValue.cleanupPolicies) {
                    ListItem(it.description)
                }

                infoPane.isVisible = true
            }
        }
    }

    /**
     * Open a file browser and add the selected directory as a new working directory.
     */
    @FXML
    fun addWorkDirectory() {
        val directory = DirectoryChooser().run {
            title = "Select directory"
            showDialog(versionsTextField.scene.window)?.toPath() ?: return
        }
        model.addWorkDirectory(directory)
    }

    /**
     * Add a new cleanup policy for the selected working directory with the values provided in the fields.
     */
    @FXML
    fun addCleanupPolicy() {
        model.selected?.addCleanupPolicy(
            versionsTextField.text?.toIntOrNull(),
            timeField.text?.toLongOrNull(),
            when (unitComboBox.selectionModel.selectedItem) {
                "Seconds" -> ChronoUnit.SECONDS
                "Minutes" -> ChronoUnit.MINUTES
                "Hours" -> ChronoUnit.HOURS
                "Days" -> ChronoUnit.DAYS
                "Weeks" -> ChronoUnit.WEEKS
                "Months" -> ChronoUnit.MONTHS
                else -> null
            }
        )

        // Clear the input.
        versionsTextField.text = ""
        timeField.text = ""
        unitComboBox.selectionModel.select(null)
    }

    /**
     * Remove the currently selected cleanup policy.
     */
    @FXML
    fun removeCleanupPolicy() {
        val selectedIndex = cleanupPolicyList.selectionModel.selectedIndex
        if (selectedIndex < 0) return
        model.selected?.cleanupPolicies?.removeAt(selectedIndex)
    }

    /**
     * Repair the repository associated with the currently selected working directory.
     */
    @FXML
    fun repairRepository() {
        model.repairRepository()
    }
}
