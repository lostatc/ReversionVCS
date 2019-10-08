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

package io.github.lostatc.reversion.gui.controls

import com.jfoenix.controls.JFXComboBox
import com.jfoenix.controls.JFXTextField
import io.github.lostatc.reversion.api.CleanupPolicy
import io.github.lostatc.reversion.gui.createBinding
import javafx.beans.property.Property
import javafx.beans.property.ReadOnlyProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Node
import javafx.scene.layout.HBox
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

/**
 * The combo box options for selecting a unit of time.
 */
private val timeUnits: Map<String, TemporalUnit> = linkedMapOf(
    "Seconds" to ChronoUnit.SECONDS,
    "Minutes" to ChronoUnit.MINUTES,
    "Hours" to ChronoUnit.HOURS,
    "Days" to ChronoUnit.DAYS,
    "Weeks" to ChronoUnit.WEEKS,
    "Months" to ChronoUnit.MONTHS
)

/**
 * An form for user input that encapsulates multiple controls.
 */
interface Form<T> {
    /**
     * A property for [result].
     */
    val resultProperty: ReadOnlyProperty<T?>

    /**
     * The node representing the form.
     */
    val node: Node

    /**
     * The output of the form, or `null` if the form is invalid or incomplete.
     */
    val result: T?
        get() = resultProperty.value

    /**
     * Clear the fields of the form.
     */
    fun clear()
}

/**
 * A [Form] which is a form used to create a [CleanupPolicy].
 */
interface PolicyForm : Form<CleanupPolicy>

/**
 * A custom control which is a form used to create a [CleanupPolicy] based on a number of versions.
 */
class VersionPolicyForm : PolicyForm, HBox() {
    @FXML
    private lateinit var versionsField: JFXTextField

    private val _resultProperty: Property<CleanupPolicy?> = SimpleObjectProperty()

    override val resultProperty: ReadOnlyProperty<CleanupPolicy?> = _resultProperty

    override val node: Node = this

    init {
        FXMLLoader(this::class.java.getResource("/fxml/forms/VersionPolicyForm.fxml")).apply {
            classLoader = this@VersionPolicyForm::class.java.classLoader
            setRoot(this@VersionPolicyForm)
            setController(this@VersionPolicyForm)
            load()
        }
    }

    @FXML
    fun initialize() {
        _resultProperty.createBinding(versionsField.textProperty()) {
            val versions = versionsField.text.toIntOrNull() ?: return@createBinding null
            CleanupPolicy.ofVersions(versions)
        }
    }

    override fun clear() {
        versionsField.text = ""
    }
}

/**
 * A custom control which is a form used to create a [CleanupPolicy] based on a duration.
 */
class TimePolicyForm : PolicyForm, HBox() {
    @FXML
    private lateinit var timeField: JFXTextField

    @FXML
    private lateinit var timeComboBox: JFXComboBox<String>

    private val _resultProperty: Property<CleanupPolicy?> = SimpleObjectProperty()

    override val resultProperty: ReadOnlyProperty<CleanupPolicy?> = _resultProperty

    override val node: Node = this

    init {
        FXMLLoader(this::class.java.getResource("/fxml/forms/TimePolicyForm.fxml")).apply {
            classLoader = this@TimePolicyForm::class.java.classLoader
            setRoot(this@TimePolicyForm)
            setController(this@TimePolicyForm)
            load()
        }
    }

    @FXML
    fun initialize() {
        timeComboBox.items.setAll(timeUnits.keys)

        _resultProperty.createBinding(
            timeField.textProperty(),
            timeComboBox.selectionModel.selectedItemProperty()
        ) {
            val amount = timeField.text.toLongOrNull() ?: return@createBinding null
            val unit = timeUnits[timeComboBox.selectionModel.selectedItem] ?: return@createBinding null
            CleanupPolicy.ofDuration(amount, unit)
        }
    }

    override fun clear() {
        timeField.text = ""
        timeComboBox.selectionModel.select(null)
    }
}

/**
 * A custom control which is a form used to create a [CleanupPolicy] with staggered versions.
 */
class StaggeredPolicyForm : PolicyForm, HBox() {
    @FXML
    private lateinit var versionsField: JFXTextField

    @FXML
    private lateinit var timeComboBox: JFXComboBox<String>

    private val _resultProperty: Property<CleanupPolicy?> = SimpleObjectProperty()

    override val resultProperty: ReadOnlyProperty<CleanupPolicy?> = _resultProperty

    override val node: Node = this

    init {
        FXMLLoader(this::class.java.getResource("/fxml/forms/StaggeredPolicyForm.fxml")).apply {
            classLoader = this@StaggeredPolicyForm::class.java.classLoader
            setRoot(this@StaggeredPolicyForm)
            setController(this@StaggeredPolicyForm)
            load()
        }
    }

    @FXML
    fun initialize() {
        timeComboBox.items.setAll(timeUnits.keys)

        _resultProperty.createBinding(
            versionsField.textProperty(),
            timeComboBox.selectionModel.selectedItemProperty()
        ) {
            val versions = versionsField.text.toIntOrNull() ?: return@createBinding null
            val unit = timeUnits[timeComboBox.selectionModel.selectedItem] ?: return@createBinding null
            CleanupPolicy.ofStaggered(versions, unit)
        }
    }

    override fun clear() {
        versionsField.text = ""
        timeComboBox.selectionModel.select(null)
    }
}
