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

package io.github.lostatc.reversion.gui.mvc

import com.jfoenix.controls.JFXComboBox
import com.jfoenix.controls.JFXListView
import com.jfoenix.controls.JFXTabPane
import com.jfoenix.controls.JFXTextField
import io.github.lostatc.reversion.api.IntegrityReport
import io.github.lostatc.reversion.cli.format
import io.github.lostatc.reversion.gui.MappedObservableList
import io.github.lostatc.reversion.gui.approvalDialog
import io.github.lostatc.reversion.gui.confirmationDialog
import io.github.lostatc.reversion.gui.controls.Card
import io.github.lostatc.reversion.gui.controls.Definition
import io.github.lostatc.reversion.gui.controls.ListItem
import io.github.lostatc.reversion.gui.dateTimeDialog
import io.github.lostatc.reversion.gui.infoDialog
import io.github.lostatc.reversion.gui.mvc.StorageModel.storageActor
import io.github.lostatc.reversion.gui.processingDialog
import io.github.lostatc.reversion.gui.sendNotification
import io.github.lostatc.reversion.gui.toDisplayProperty
import io.github.lostatc.reversion.gui.ui
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.TabPane
import javafx.scene.layout.StackPane
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import org.apache.commons.io.FileUtils
import java.time.Instant
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/**
 * Set the visibility of the contents of the tab pane.
 */
private fun TabPane.setContentsVisible(visible: Boolean) {
    for (tab in tabs) {
        tab.content.isVisible = visible
    }
}


/**
 * The controller for the view that is used to manage working directories.
 */
class WorkDirectoryManagerController {

    /**
     * The root node of the view.
     */
    @FXML
    private lateinit var root: StackPane

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
     * The list view that displays the files being ignored.
     */
    @FXML
    private lateinit var ignorePathList: JFXListView<Label>

    /**
     * The tab pane used for managing the currently selected working directory.
     */
    @FXML
    private lateinit var workDirectoryTabPane: JFXTabPane

    /**
     * A widget which shows the current number of snapshots in the working directory.
     */
    @FXML
    private lateinit var snapshotsDefinition: Definition

    /**
     * A widget which shows the current amount of storage used in the working directory.
     */
    @FXML
    private lateinit var storageUsedDefinition: Definition

    /**
     * A widget which shows the amount of storage that was saved through deduplication.
     */
    @FXML
    private lateinit var storageSavedDefinition: Definition

    /**
     * A widget which shows the time the most recent version was created.
     */
    @FXML
    private lateinit var latestVersionDefinition: Definition

    /**
     * A widget which shows the total number of tracked files.
     */
    @FXML
    private lateinit var trackedFilesDefinition: Definition

    private val model: WorkDirectoryManagerModel = WorkDirectoryManagerModel()

    @FXML
    fun initialize() {
        unitComboBox.items.setAll("", "Seconds", "Minutes", "Hours", "Days", "Weeks", "Months")

        // Bind the selected working directory in the model to the selected working directory in the view.
        workDirectoryList.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            model.selected = model.workDirectories.getOrNull(newValue.toInt())
        }

        // Bind the list of working directories in the view to the model.
        workDirectoryList.items = MappedObservableList(model.workDirectories) {
            Card().apply {
                titleProperty.bind(it.pathProperty.toDisplayProperty { it.fileName.toString() })
                subtitleProperty.bind(it.pathProperty.toDisplayProperty { it.toString() })
            }
        }

        model.loadWorkDirectories()

        // Make the working directory information pane initially invisible.
        workDirectoryTabPane.setContentsVisible(false)

        model.selectedProperty.addListener { _, _, newValue ->
            if (newValue == null) {
                workDirectoryTabPane.setContentsVisible(false)
            } else {
                // Bind the list of cleanup policies it the view to the model.
                cleanupPolicyList.items = MappedObservableList(newValue.cleanupPolicies) {
                    ListItem(it.description)
                }

                // Bind the list of ignored paths in the view to the model.
                ignorePathList.items = MappedObservableList(newValue.ignoredPaths) {
                    ListItem(it.toString())
                }

                // Bind statistics in the view to the model.
                snapshotsDefinition.valueProperty.bind(
                    newValue.snapshotsProperty.toDisplayProperty { it.toString() }
                )
                storageUsedDefinition.valueProperty.bind(
                    newValue.storageUsedProperty.toDisplayProperty { FileUtils.byteCountToDisplaySize(it) }
                )
                storageSavedDefinition.valueProperty.bind(
                    newValue.storageSavedProperty.toDisplayProperty { FileUtils.byteCountToDisplaySize(it) }
                )
                latestVersionDefinition.valueProperty.bind(
                    newValue.latestVersionProperty.toDisplayProperty { it.format(FormatStyle.MEDIUM) }
                )
                trackedFilesDefinition.valueProperty.bind(
                    newValue.trackedFilesProperty.toDisplayProperty { it.toString() }
                )

                workDirectoryTabPane.setContentsVisible(true)
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
     * Add new files to be ignored.
     */
    @FXML
    fun addIgnoreFile() {
        val newPath = FileChooser().run {
            title = "Select files to ignore"
            initialDirectory = model.selected?.path?.toFile()
            showOpenMultipleDialog(workDirectoryTabPane.scene.window)?.map { it.toPath() } ?: return
        }

        model.selected?.ignoredPaths?.addAll(newPath)
    }

    /**
     * Add a new directory to be ignored.
     */
    @FXML
    fun addIgnoreDirectory() {
        val newPath = DirectoryChooser().run {
            title = "Select directory to ignore"
            initialDirectory = model.selected?.path?.toFile()
            showDialog(workDirectoryTabPane.scene.window)?.toPath() ?: return
        }

        model.selected?.ignoredPaths?.add(newPath)
    }

    /**
     * Remove a file from the list of ignored files.
     */
    @FXML
    fun removeIgnorePath() {
        val selectedIndex = ignorePathList.selectionModel.selectedIndex
        if (selectedIndex < 0) return

        model.selected?.ignoredPaths?.removeAt(selectedIndex)
    }

    @FXML
    fun deleteWorkDirectory() {
        val dialog = approvalDialog(
            title = "Delete version history",
            text = "Are you sure you want to permanently delete all past versions in this directory? This will not affect the current versions of your files.",
            action = { model.deleteWorkDirectory() }
        )

        dialog.show(root)
    }


    /**
     * Repair the repository and show the user a dialog to indicate progress.
     */
    private fun repair(report: IntegrityReport) {
        val job = storageActor.sendBlockingAsync { report.repair() }
        val dialog = processingDialog(title = "Repairing...", job = job)
        dialog.show(root)
    }

    /**
     * Prompt the user for whether they want to repair the repository.
     */
    private fun promptRepair(report: IntegrityReport) {
        val dialog = if (report.isValid) {
            infoDialog(
                title = "No corruption detected",
                text = "There are no corrupt versions in this directory."
            )
        } else {
            confirmationDialog(
                title = "Corruption detected",
                text = "There are ${report.corrupt.size} corrupt versions in this directory. ${report.repaired.size} of them will be repaired. ${report.deleted.size} of them cannot be repaired and will be deleted. This may take a while. Do you want to repair?",
                action = { repair(report) }
            )
        }
        dialog.show(root)
    }

    /**
     * Verify the repository and show the user a dialog to indicate progress.
     */
    private fun verify() {
        val selected = model.selected ?: return
        val job = selected.executeAsync { workDirectory.repository.verify(workDirectory.path) } ui { promptRepair(it) }
        val dialog = processingDialog(title = "Checking for corruption...", job = job)
        dialog.show(root)
    }

    /**
     * Prompt the user for whether they want to verify the repository.
     */
    @FXML
    fun promptVerify() {
        val dialog = confirmationDialog(
            title = "Check for corruption",
            text = "This will check the versions in this directory for corruption. If corrupt data is found, you will have the option to repair it. This may take a while. Do you want to check for corruption?",
            action = { verify() }
        )
        dialog.show(root)
    }

    /**
     * Prompts the user for a time and date to mount a snapshot.
     */
    @FXML
    fun mountSnapshot() {
        val dialog = dateTimeDialog(
            title = "Choose a time and date",
            text = "Choose the time and date that you want to see files from.",
            default = Instant.now()
        ) { time ->
            time?.let { model.mountSnapshot(it) } ?: sendNotification("You must select a time and date.")
        }
        dialog.show(root)
    }
}
