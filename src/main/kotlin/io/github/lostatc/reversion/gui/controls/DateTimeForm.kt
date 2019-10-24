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

import com.jfoenix.controls.JFXDatePicker
import com.jfoenix.controls.JFXTimePicker
import io.github.lostatc.reversion.api.Form
import io.github.lostatc.reversion.api.FormResult
import io.github.lostatc.reversion.api.createBinding
import io.github.lostatc.reversion.api.loadFxml
import javafx.beans.property.ReadOnlyProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.layout.HBox
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * A [Form] for selecting a time and date.
 *
 * @param [initialValue] The initial time and date to select.
 */
class DateTimeForm(private val initialValue: Instant) : Form<Instant>, HBox() {
    @FXML
    private lateinit var datePicker: JFXDatePicker

    @FXML
    private lateinit var timePicker: JFXTimePicker

    private val _resultProperty = SimpleObjectProperty<FormResult<Instant>>()

    override val resultProperty: ReadOnlyProperty<FormResult<Instant>> = _resultProperty

    override val node: Node = this

    init {
        loadFxml(this, "/fxml/controls/DateTimePicker.fxml")
    }

    @FXML
    fun initialize() {
        datePicker.value = LocalDate.ofInstant(initialValue, ZoneId.systemDefault())
        timePicker.value = LocalTime.ofInstant(initialValue, ZoneId.systemDefault())

        _resultProperty.createBinding(datePicker.valueProperty(), timePicker.valueProperty()) {
            when {
                datePicker.value == null || timePicker.value == null -> {
                    FormResult.Invalid("You must enter a date and time.")
                }
                else -> {
                    val dateTime = LocalDateTime.of(datePicker.value, timePicker.value)
                    FormResult.Valid(dateTime.toInstant(ZoneId.systemDefault().rules.getOffset(dateTime)))
                }

            }
        }
    }

    override fun clear() {
        datePicker.value = null
        timePicker.value = null
    }
}
