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

import com.jfoenix.controls.JFXComboBox
import com.jfoenix.controls.JFXListView
import com.jfoenix.controls.JFXTabPane
import com.jfoenix.controls.JFXToggleButton
import io.github.lostatc.reversion.DEFAULT_PROVIDER
import io.github.lostatc.reversion.api.FormResult
import io.github.lostatc.reversion.api.createBinding
import io.github.lostatc.reversion.api.storage.RepairAction
import io.github.lostatc.reversion.api.toDisplayProperty
import io.github.lostatc.reversion.api.toMappedProperty
import io.github.lostatc.reversion.daemon.WatchDaemon
import io.github.lostatc.reversion.gui.MappedObservableList
import io.github.lostatc.reversion.gui.MappingCellFactory
import io.github.lostatc.reversion.gui.approvalDialog
import io.github.lostatc.reversion.gui.confirmationDialog
import io.github.lostatc.reversion.gui.controls.Card
import io.github.lostatc.reversion.gui.controls.CategoryIgnoreMatcherForm
import io.github.lostatc.reversion.gui.controls.DateTimeForm
import io.github.lostatc.reversion.gui.controls.Definition
import io.github.lostatc.reversion.gui.controls.ExtensionIgnoreMatcherForm
import io.github.lostatc.reversion.gui.controls.GlobIgnoreMatcherForm
import io.github.lostatc.reversion.gui.controls.IgnoreMatcherForm
import io.github.lostatc.reversion.gui.controls.ListItem
import io.github.lostatc.reversion.gui.controls.PolicyForm
import io.github.lostatc.reversion.gui.controls.PrefixIgnoreMatcherForm
import io.github.lostatc.reversion.gui.controls.RegexIgnoreMatcherForm
import io.github.lostatc.reversion.gui.controls.SizeIgnoreMatcherForm
import io.github.lostatc.reversion.gui.controls.StaggeredPolicyForm
import io.github.lostatc.reversion.gui.controls.TimePolicyForm
import io.github.lostatc.reversion.gui.controls.VersionPolicyForm
import io.github.lostatc.reversion.gui.controls.description
import io.github.lostatc.reversion.gui.formDialog
import io.github.lostatc.reversion.gui.format
import io.github.lostatc.reversion.gui.infoDialog
import io.github.lostatc.reversion.gui.models.StorageModel.storageActor
import io.github.lostatc.reversion.gui.models.WorkDirectoryManagerModel
import io.github.lostatc.reversion.gui.models.WorkDirectoryModel
import io.github.lostatc.reversion.gui.processingDialog
import io.github.lostatc.reversion.gui.sendNotification
import io.github.lostatc.reversion.storage.IgnoreMatcher
import io.github.lostatc.reversion.storage.WorkDirectory
import javafx.fxml.FXML
import javafx.scene.Group
import javafx.scene.control.Label
import javafx.scene.control.TabPane
import javafx.scene.layout.StackPane
import javafx.stage.DirectoryChooser
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.format.FormatStyle

/**
 * Set the visibility of the contents of the tab pane.
 */
private fun TabPane.setContentsVisible(visible: Boolean) {
    for (tab in tabs) {
        tab.content.isVisible = visible
    }
}

/**
 * The list of forms for creating cleanup policies.
 */
private val policyForms = listOf<PolicyForm>(
    VersionPolicyForm(),
    TimePolicyForm(),
    StaggeredPolicyForm()
)

/**
 * A cell factory for the combo box for selecting a cleanup policy type.
 */
private val policyCellFactory = MappingCellFactory<PolicyForm> {
    when (it) {
        is VersionPolicyForm -> "Keep a specific number of versions"
        is TimePolicyForm -> "Delete versions which are older than"
        is StaggeredPolicyForm -> "Keep staggered versions"
        else -> ""
    }
}

/**
 * A cell factory for the combo box for selecting an ignore matcher type.
 */
private val ignoreCellFactory = MappingCellFactory<IgnoreMatcherForm> {
    when (it) {
        is PrefixIgnoreMatcherForm -> "Ignore a file or directory"
        is SizeIgnoreMatcherForm -> "Ignore files over a certain size"
        is ExtensionIgnoreMatcherForm -> "Ignore files with a certain file extension"
        is CategoryIgnoreMatcherForm -> "Ignore a category of files"
        is GlobIgnoreMatcherForm -> "Ignore files matching a glob pattern"
        is RegexIgnoreMatcherForm -> "Ignore files matching a regex"
        else -> ""
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
     * The combo box used for selecting a cleanup policy input form.
     */
    @FXML
    private lateinit var policyTypeComboBox: JFXComboBox<PolicyForm>

    /**
     * A container which holds the selected cleanup policy input form.
     */
    @FXML
    private lateinit var policyFormContainer: Group

    /**
     * A label which shows a preview of the policy to be created.
     */
    @FXML
    private lateinit var policyPreviewLabel: Label

    /**
     * The list view that displays the files being ignored.
     */
    @FXML
    private lateinit var ignoreMatcherList: JFXListView<Label>

    /**
     * The combo box for selecting a type of ignore matcher.
     */
    @FXML
    private lateinit var ignoreTypeComboBox: JFXComboBox<IgnoreMatcherForm>

    /**
     * A container which holds the selected ignore matcher form.
     */
    @FXML
    private lateinit var ignoreFormContainer: Group

    /**
     * A label which shows a preview of the ignore matcher to be created.
     */
    @FXML
    private lateinit var ignorePreviewLabel: Label

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

    /**
     * The model which contains state for this controller.
     */
    private val model: WorkDirectoryManagerModel = WorkDirectoryManagerModel()

    @FXML
    fun initialize() {
        // The list of forms for creating ignore matchers.
        val ignoreForms = listOf<IgnoreMatcherForm>(
            PrefixIgnoreMatcherForm(model.selectedProperty.toMappedProperty { it?.path ?: Paths.get("") }),
            SizeIgnoreMatcherForm(),
            ExtensionIgnoreMatcherForm(),
            CategoryIgnoreMatcherForm(),
            GlobIgnoreMatcherForm(),
            RegexIgnoreMatcherForm()
        )

        // Control how the cleanup policy types are displayed in the combo box.
        policyTypeComboBox.cellFactory = policyCellFactory
        policyTypeComboBox.buttonCell = policyCellFactory.call(null)

        // Bind the contents of a node so that it contains the selected cleanup policy form.
        policyTypeComboBox.selectionModel.selectedItemProperty().addListener { _, _, newValue ->
            policyFormContainer.children.setAll(newValue.node)
            policyPreviewLabel.textProperty().createBinding(newValue.resultProperty) { newValue.result.description }
        }

        // Set the items in the combo box for selecting a type of cleanup policy.
        policyTypeComboBox.items.setAll(policyForms)
        policyTypeComboBox.selectionModel.select(0)

        // Control how ignore matcher types are displayed in the combo box.
        ignoreTypeComboBox.cellFactory = ignoreCellFactory
        ignoreTypeComboBox.buttonCell = ignoreCellFactory.call(null)

        // Bind the contents of a node so that it contains the selected ignore matcher form.
        ignoreTypeComboBox.selectionModel.selectedItemProperty().addListener { _, _, newValue ->
            // This value may be `null` if a working directory has not been selected yet.
            if (newValue != null) {
                ignoreFormContainer.children.setAll(newValue.node)
                ignorePreviewLabel.textProperty().createBinding(newValue.resultProperty) { newValue.result.description }
            }
        }

        // Set the items in the combo box for selecting a type of ignore matcher.
        ignoreTypeComboBox.items.setAll(ignoreForms)
        ignoreTypeComboBox.selectionModel.select(0)

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

        // Asynchronously load the registered working directories and handle errors by prompting the user.
        model.launch {
            for (path in WatchDaemon.registered.toSet()) {
                launch { openWorkDirectory(path) }
            }
        }

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
                ignoreMatcherList.items = MappedObservableList(newValue.ignoreMatchers) {
                    ListItem(it.description)
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
     * Attempt to open the working directory at [path] and prompt the user if repairs need to be made.
     *
     * @return A model representing the working directory, or `null` if it could not be opened.
     */
    private suspend fun openWorkDirectory(path: Path): WorkDirectoryModel? =
        WorkDirectoryModel.open(path).onFail { failure ->
            // Attempt to repair the repository.
            if (failure.actions.all { repair(it) }) {
                // All the repair actions succeeded.
                WorkDirectoryModel.open(path).onFail {
                    sendNotification("Even though the repository was successfully repaired, it could not be opened.")
                    return null
                }
            } else {
                // At least one repair action failed.
                sendNotification("Attempts to repair the repository either failed or were cancelled.")
                return null
            }
        }

    /**
     * Open a file browser and add the selected directory as a working directory.
     *
     * If the directory is already an initialized working directory, it is opened and registered. If not, it is created
     * and registered.
     */
    @FXML
    fun addWorkDirectory() {
        // Prompt the user to select a directory.
        val directory = DirectoryChooser().run {
            title = "Select directory"
            showDialog(root.scene.window)?.toPath() ?: return
        }

        model.launch {
            // Check if the working directory has already been registered.
            if (WatchDaemon.registered.contains(directory)) {
                sendNotification("This directory is already being tracked.")
            } else {
                if (WorkDirectory.isWorkDirectory(directory)) {
                    // Open an existing working directory and register it.
                    openWorkDirectory(directory)?.apply { model.addWorkDirectory(this) } ?: return@launch
                } else {
                    // Create a new working directory and register it. Prompt the user to configure it.
                    val configForm = DEFAULT_PROVIDER.configure()
                    val result = formDialog("Configure the directory", "", configForm).prompt(root)

                    val configurator = result.onInvalid {
                        it.message?.let { message -> sendNotification(message) }
                        return@launch
                    }

                    WorkDirectoryModel.create(directory, configurator).apply { model.addWorkDirectory(this) }
                }
            }
        }
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

    @FXML
    fun hideWorkDirectory() {
        model.hideWorkDirectory()
    }

    /**
     * Add a new cleanup policy for the selected working directory with the values provided in the cleanup policy form.
     */
    @FXML
    fun addCleanupPolicy() {
        val policyForm = policyTypeComboBox.selectionModel.selectedItem
        val policy = policyForm.result.onInvalid { return }
        model.selected?.cleanupPolicies?.add(policy) ?: return
        policyForm.clear()
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
     * Add the selected [IgnoreMatcher] specified by the selected [IgnoreMatcherForm].
     */
    @FXML
    fun addIgnoreMatcher() {
        val ignoreForm = ignoreTypeComboBox.selectionModel.selectedItem
        val ignoreMatcher = ignoreForm.result.onInvalid { return }
        model.selected?.ignoreMatchers?.add(ignoreMatcher) ?: return
        ignoreForm.clear()
    }

    /**
     * Remove the selected [IgnoreMatcher].
     */
    @FXML
    fun removeIgnoreMatcher() {
        val selectedIndex = ignoreMatcherList.selectionModel.selectedIndex
        if (selectedIndex < 0) return
        model.selected?.ignoreMatchers?.removeAt(selectedIndex)
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
        // If no corruption was detected, inform the user and continue to the next action.
        if (action == null) {
            infoDialog("No corruption detected", "This test found nothing that needs to be repaired.").prompt(root)
            return true
        }

        // If corruption was detected, prompt the user for whether to attempt the repair.
        val repairApproved = approvalDialog("Corruption detected", action.message).prompt(root)

        if (repairApproved) {
            val repairJob = storageActor.sendAsync { action.repair() }
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
            return
        }

        for (verifyAction in verifyActions) {
            // Ask the user if they want to check for corruption.
            val verifyConfirmation = confirmationDialog("Check for corruption", verifyAction.message).prompt(root)
            if (!verifyConfirmation) continue

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
            val result = formDialog(
                "Choose a time and date",
                "Choose the time and date that you want to see files from.",
                DateTimeForm(Instant.now())
            ).prompt(root)

            when (result) {
                is FormResult.Valid -> model.mountSnapshot(result.value)
                is FormResult.Invalid -> result.message?.let { sendNotification(it) }
            }
        }
    }
}
