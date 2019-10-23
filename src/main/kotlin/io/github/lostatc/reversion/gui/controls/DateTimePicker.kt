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

package io.github.lostatc.reversion.gui.controls

import com.jfoenix.controls.JFXDatePicker
import com.jfoenix.controls.JFXTimePicker
import io.github.lostatc.reversion.api.getValue
import io.github.lostatc.reversion.api.loadFxml
import io.github.lostatc.reversion.api.setValue
import javafx.beans.InvalidationListener
import javafx.beans.property.Property
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXML
import javafx.scene.layout.HBox
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Objects

/**
 * A control for prompting the user to select a date and time.
 */
class DateTimePicker : HBox() {
    @FXML
    private lateinit var datePicker: JFXDatePicker

    @FXML
    private lateinit var timePicker: JFXTimePicker

    /**
     * A property for [instant].
     */
    val instantProperty: Property<Instant?> = SimpleObjectProperty(null)

    /**
     * The date and time currently selected.
     */
    var instant: Instant? by instantProperty

    init {
        loadFxml(this, "/fxml/controls/DateTimePicker.fxml")
    }

    @FXML
    fun initialize() {
        val updateListener = InvalidationListener {
            val date = datePicker.value ?: return@InvalidationListener
            val time = timePicker.value ?: return@InvalidationListener
            val dateTime = LocalDateTime.of(date, time)
            instant = dateTime.toInstant(ZoneId.systemDefault().rules.getOffset(dateTime))
        }

        datePicker.valueProperty().addListener(updateListener)
        timePicker.valueProperty().addListener(updateListener)

        instantProperty.addListener { _, _, newValue ->
            val date = LocalDate.ofInstant(newValue, ZoneId.systemDefault())
            val time = LocalTime.ofInstant(newValue, ZoneId.systemDefault())
            datePicker.value = date
            timePicker.value = time
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DateTimePicker) return false
        return instant == other.instant
    }

    override fun hashCode(): Int = Objects.hash(instant)
}
