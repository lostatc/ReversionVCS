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

package io.github.lostatc.reversion.gui.controllers

import com.jfoenix.controls.JFXComboBox
import com.jfoenix.controls.JFXListView
import com.jfoenix.controls.JFXTabPane
import com.jfoenix.controls.JFXToggleButton
import io.github.lostatc.reversion.api.IntegrityReport
import io.github.lostatc.reversion.gui.MappedObservableList
import io.github.lostatc.reversion.gui.MappingCellFactory
import io.github.lostatc.reversion.gui.approvalDialog
import io.github.lostatc.reversion.gui.confirmationDialog
import io.github.lostatc.reversion.gui.controls.Card
import io.github.lostatc.reversion.gui.controls.CategoryIgnoreMatcherForm
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
import io.github.lostatc.reversion.gui.createBinding
import io.github.lostatc.reversion.gui.dateTimeDialog
import io.github.lostatc.reversion.gui.infoDialog
import io.github.lostatc.reversion.gui.models.StorageModel.storageActor
import io.github.lostatc.reversion.gui.models.WorkDirectoryManagerModel
import io.github.lostatc.reversion.gui.models.format
import io.github.lostatc.reversion.gui.processingDialog
import io.github.lostatc.reversion.gui.sendNotification
import io.github.lostatc.reversion.gui.toDisplayProperty
import io.github.lostatc.reversion.gui.ui
import io.github.lostatc.reversion.storage.IgnoreMatcher
import javafx.fxml.FXML
import javafx.scene.Group
import javafx.scene.control.Label
import javafx.scene.control.TabPane
import javafx.scene.layout.StackPane
import javafx.stage.DirectoryChooser
import org.apache.commons.io.FileUtils
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
    private val model: WorkDirectoryManagerModel =
        WorkDirectoryManagerModel()

    @FXML
    fun initialize() {
        // Control how the cleanup policy types are displayed in the combo box.
        policyTypeComboBox.cellFactory = policyCellFactory
        policyTypeComboBox.buttonCell = policyCellFactory.call(null)

        // Bind the contents of a node so that it contains the selected cleanup policy form.
        policyTypeComboBox.selectionModel.selectedItemProperty().addListener { _, _, newValue ->
            policyFormContainer.children.setAll(newValue.node)
            policyPreviewLabel.textProperty().createBinding(newValue.resultProperty) {
                newValue.result?.description ?: ""
            }
        }

        // Set the items in the combo box for selecting a type of cleanup policy.
        policyTypeComboBox.items.setAll(policyForms)
        policyTypeComboBox.selectionModel.select(0)

        // Control how ignore matcher types are displayed in the combo box.
        ignoreTypeComboBox.cellFactory = ignoreCellFactory
        ignoreTypeComboBox.buttonCell = ignoreCellFactory.call(null)

        // Bind the contents of a node so that it contains the selected ignore matcher form.
        ignoreTypeComboBox.selectionModel.selectedItemProperty().addListener { _, _, newValue ->
            ignoreFormContainer.children.setAll(newValue.node)
            ignorePreviewLabel.textProperty().createBinding(newValue.resultProperty) {
                newValue.result?.description ?: ""
            }
        }

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
                // The list of forms for creating ignore matchers.
                val ignoreForms = listOf<IgnoreMatcherForm>(
                    PrefixIgnoreMatcherForm(newValue.path),
                    SizeIgnoreMatcherForm(),
                    ExtensionIgnoreMatcherForm(),
                    CategoryIgnoreMatcherForm(),
                    GlobIgnoreMatcherForm(),
                    RegexIgnoreMatcherForm()
                )

                // Set the items in the combo box for selecting a type of ignore matcher.
                ignoreTypeComboBox.items.setAll(ignoreForms)
                ignoreTypeComboBox.selectionModel.select(0)

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
     * Open a file browser and add the selected directory as a new working directory.
     */
    @FXML
    fun addWorkDirectory() {
        val directory = DirectoryChooser().run {
            title = "Select directory"
            showDialog(root.scene.window)?.toPath() ?: return
        }
        model.addWorkDirectory(directory)
    }

    /**
     * Add a new cleanup policy for the selected working directory with the values provided in the cleanup policy form.
     */
    @FXML
    fun addCleanupPolicy() {
        val policyForm = policyTypeComboBox.selectionModel.selectedItem
        val policy = policyForm.result ?: return
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
        val ignoreMatcher = ignoreForm.result ?: return
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
     * Prompt the user for whether they want to delete the selected working directory.
     */
    @FXML
    fun deleteWorkDirectory() {
        val dialog = approvalDialog(
            title = "Delete version history",
            text = "Are you sure you want to permanently delete all past versions in this directory? This will not affect the current versions of your files.",
            action = { model.deleteWorkDirectory() }
        )

        dialog.show(root)
    }

    @FXML
    fun hideWorkDirectory() {
        model.hideWorkDirectory()
    }

    /**
     * Toggle whether changes are being tracked for the selected working directory.
     */
    @FXML
    fun toggleTrackChanges() {
        model.selected?.setTrackChanges(trackChangesToggle.isSelected)
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
