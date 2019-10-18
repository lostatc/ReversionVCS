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
import com.jfoenix.controls.JFXTabPane
import com.jfoenix.controls.JFXTextField
import com.jfoenix.controls.JFXToggleButton
import io.github.lostatc.reversion.api.RepairAction
import io.github.lostatc.reversion.cli.format
import io.github.lostatc.reversion.gui.MappedObservableList
import io.github.lostatc.reversion.gui.approvalDialog
import io.github.lostatc.reversion.gui.confirmationDialog
import io.github.lostatc.reversion.gui.controls.Card
import io.github.lostatc.reversion.gui.controls.Definition
import io.github.lostatc.reversion.gui.controls.ListItem
import io.github.lostatc.reversion.gui.dateTimeDialog
import io.github.lostatc.reversion.gui.infoDialog
import io.github.lostatc.reversion.gui.processingDialog
import io.github.lostatc.reversion.gui.sendNotification
import io.github.lostatc.reversion.gui.toDisplayProperty
import io.github.lostatc.reversion.gui.toSorted
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.TabPane
import javafx.scene.layout.StackPane
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import java.nio.file.Path
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

    /**
     * A toggle button which toggles tracking changes for the selected working directory.
     */
    @FXML
    private lateinit var trackChangesToggle: JFXToggleButton

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

        // Load working directories and handle errors by prompting the user.
        model.loadWorkDirectories(::workDirectoryHandler)

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
                ignorePathList.items = MappedObservableList(newValue.ignoredPaths.toSorted()) {
                    ListItem(it.toString())
                }

                // Set whether this working directory is tracking changes.
                trackChangesToggle.isSelected = newValue.trackingChanges
                newValue.trackingChangesProperty.addListener { _, _, newTrackingValue ->
                    trackChangesToggle.isSelected = newTrackingValue
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
     * Get a [WorkDirectoryModel] for the given [path], handling any errors opening the repository.
     */
    private suspend fun workDirectoryHandler(path: Path): WorkDirectoryModel? =
        WorkDirectoryModel.fromPath(path).onFail { failure ->
            if (failure.actions.all { repair(it) }) {
                WorkDirectoryModel.fromPath(path).onFail {
                    sendNotification("Even though the repository was successfully repaired, it could not be opened.")
                    return null
                }
            } else {
                sendNotification("Attempts to repair the repository either failed or were cancelled.")
                return null
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
        model.addWorkDirectory(directory, ::workDirectoryHandler)
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

    /**
     * Prompt the user for whether they want to delete the selected working directory.
     */
    @FXML
    fun deleteWorkDirectory() {
        model.launch {
            val deleteApproval = approvalDialog(
            title = "Delete version history",
            text = "Are you sure you want to permanently delete all past versions in this directory? This will not affect the current versions of your files."
            ).prompt(root)

            if (deleteApproval) {
                model.deleteWorkDirectory()
            }
        }
    }

    /**
     * Toggle whether changes are being tracked for the selected working directory.
     */
    @FXML
    fun toggleTrackChanges() {
        model.selected?.setTrackChanges(trackChangesToggle.isSelected)
    }

    /**
     * Walk the user through running a repair action.
     *
     * @param [action] The repair action to execute, or `null` to inform the user that no action needs to be taken.
     *
     * @return `true` if the repair succeeded, `false` if it failed.
     */
    private suspend fun repair(action: RepairAction?): Boolean {
        val selected = model.selected ?: return true

        // If no corruption was detected, inform the user and continue to the next action.
        if (action == null) {
            infoDialog("No corruption detected", "This test found nothing that needs to be repaired.").prompt(root)
            return true
        }

        // If corruption was detected, prompt the user for whether to attempt the repair.
        val repairApproved = approvalDialog("Corruption detected", action.message).prompt(root)

        if (repairApproved) {
            // Attempt the repair.
            val repairJob = selected.executeAsync { action.repair() }
            processingDialog("Repairing...", repairJob).dialog.show(root)
            val repairResult = repairJob.await()

            return if (repairResult.success) {
                infoDialog("Repair successful", repairResult.message).prompt(root)
                true
            } else {
                infoDialog("Repair failed", repairResult.message).prompt(root)
                false
            }
        }

        return false
    }

    /**
     * Walk the user through verifying and repairing the repository.
     */
    private suspend fun verify() {
        val selected = model.selected ?: return

        val verifyActions = selected.executeAsync { workDirectory.repository.verify(workDirectory.path) }.await()

        if (verifyActions.isEmpty()) {
            infoDialog("Nothing to do", "This storage backend doesn't support repair.").prompt(root)
        }

        for (verifyAction in verifyActions) {
            // Ask the user if they want to check for corruption.
            val verifyConfirmation = verifyAction.message?.let {
                confirmationDialog("Check for corruption", it).prompt(root)
            }
            if (verifyConfirmation == false) continue

            // Check for corruption.
            val verifyJob = selected.executeAsync { verifyAction.verify() }
            processingDialog("Checking for corruption...", verifyJob).dialog.show(root)


            // Repair the repository.
            repair(verifyJob.await())
        }
    }

    /**
     * Prompt the user for whether they want to verify the repository.
     */
    @FXML
    fun promptVerify() {
        model.launch { verify() }
    }

    /**
     * Prompts the user for a time and date to mount a snapshot.
     */
    @FXML
    fun mountSnapshot() {
        model.launch {
            val instant = dateTimeDialog(
                "Choose a time and date",
                "Choose the time and date that you want to see files from.",
                Instant.now()
            ).prompt(root)

            if (instant != null) {
                model.mountSnapshot(instant)
            }
        }
    }
}
