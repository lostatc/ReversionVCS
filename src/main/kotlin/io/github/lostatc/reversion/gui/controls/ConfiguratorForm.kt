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

import io.github.lostatc.reversion.api.Configurator
import io.github.lostatc.reversion.api.Form
import io.github.lostatc.reversion.api.FormResult
import io.github.lostatc.reversion.api.createBinding
import io.github.lostatc.reversion.api.io.Chunker
import io.github.lostatc.reversion.api.loadFxml
import io.github.lostatc.reversion.storage.DatabaseRepository
import io.github.lostatc.reversion.storage.PERFORMANCE_CHUNKER
import io.github.lostatc.reversion.storage.STORAGE_CHUNKER
import javafx.beans.property.ReadOnlyProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Toggle
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.VBox

/**
 * A [Form] for configuring a [DatabaseRepository].
 */
class ConfiguratorForm : Form<Configurator>, VBox() {

    @FXML
    private lateinit var chunkerGroup: ToggleGroup

    @FXML
    private lateinit var storageToggle: Toggle

    @FXML
    private lateinit var performanceToggle: Toggle

    /**
     * The currently selected [Chunker].
     */
    private val chunkerProperty = SimpleObjectProperty<Chunker?>(null)

    private val _resultProperty = SimpleObjectProperty<FormResult<Configurator>>()

    override val resultProperty: ReadOnlyProperty<FormResult<Configurator>> = _resultProperty

    override val node: Node = this

    init {
        loadFxml(this, "/fxml/forms/ConfiguratorForm.fxml")
    }

    @FXML
    fun initialize() {
        chunkerProperty.createBinding(chunkerGroup.selectedToggleProperty()) {
            when (chunkerGroup.selectedToggle) {
                storageToggle -> STORAGE_CHUNKER
                performanceToggle -> PERFORMANCE_CHUNKER
                else -> null
            }
        }
        _resultProperty.createBinding(chunkerProperty) {
            when (val chunker = chunkerProperty.value) {
                null -> FormResult.Invalid()
                else -> FormResult.Valid(
                    Configurator {
                        it[DatabaseRepository.chunkerProperty] = chunker
                    }
                )
            }
        }
    }

    override fun clear() {
        chunkerGroup.selectToggle(null)
    }

}
