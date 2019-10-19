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
import io.github.lostatc.reversion.gui.MappingCellFactory
import io.github.lostatc.reversion.gui.createBinding
import io.github.lostatc.reversion.gui.loadFxml
import io.github.lostatc.reversion.gui.parseBytes
import io.github.lostatc.reversion.storage.CategoryIgnoreMatcher
import io.github.lostatc.reversion.storage.ExtensionIgnoreMatcher
import io.github.lostatc.reversion.storage.GlobIgnoreMatcher
import io.github.lostatc.reversion.storage.IgnoreCategory
import io.github.lostatc.reversion.storage.IgnoreMatcher
import io.github.lostatc.reversion.storage.PrefixIgnoreMatcher
import io.github.lostatc.reversion.storage.RegexIgnoreMatcher
import io.github.lostatc.reversion.storage.SizeIgnoreMatcher
import javafx.beans.property.ReadOnlyProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import java.nio.file.Path
import java.nio.file.Paths

interface IgnoreMatcherForm : Form<IgnoreMatcher>

/**
 * A [Form] for creating a [PrefixIgnoreMatcher].
 *
 * @param [base] The base path for paths to match.
 */
class PrefixIgnoreMatcherForm(private val base: ReadOnlyProperty<Path>) : IgnoreMatcherForm, HBox() {

    @FXML
    private lateinit var pathField: JFXTextField

    private val _resultProperty = SimpleObjectProperty<IgnoreMatcher?>()

    override val resultProperty: ReadOnlyProperty<IgnoreMatcher?> = _resultProperty

    override val node: Node = this

    init {
        loadFxml(this, "/fxml/forms/PrefixIgnoreMatcherForm.fxml")
    }

    override fun clear() {
        pathField.text = ""
    }

    @FXML
    fun initialize() {
        _resultProperty.createBinding(pathField.textProperty(), base) {
            // Pass a relative path so that it will work if the directory is moved/synced elsewhere.
            val path = Paths.get(pathField.text)
            when {
                pathField.text.isBlank() -> null
                path.startsWith(base.value) -> PrefixIgnoreMatcher(base.value.relativize(path))
                !path.isAbsolute -> PrefixIgnoreMatcher(path)
                else -> null
            }
        }
    }

    /**
     * Open a dialog to select a file to ignore.
     */
    @FXML
    fun selectFile() {
        pathField.text = FileChooser().run {
            title = "Select a file to ignore"
            showOpenDialog(pathField.scene.window)?.toPath()?.toString() ?: return
        }

    }

    /**
     * Open a dialog to select a directory to ignore.
     */
    @FXML
    fun selectDirectory() {
        pathField.text = DirectoryChooser().run {
            title = "Select a directory to ignore"
            showDialog(pathField.scene.window)?.toPath()?.toString() ?: return
        }
    }
}

/**
 * A [Form] for creating a [GlobIgnoreMatcher].
 */
class GlobIgnoreMatcherForm : IgnoreMatcherForm, VBox() {

    @FXML
    private lateinit var patternField: JFXTextField

    private val _resultProperty = SimpleObjectProperty<IgnoreMatcher?>()

    override val resultProperty: ReadOnlyProperty<IgnoreMatcher?> = _resultProperty

    override val node: Node = this

    init {
        loadFxml(this, "/fxml/forms/GlobIgnoreMatcherForm.fxml")
    }

    override fun clear() {
        patternField.text = ""
    }

    @FXML
    fun initialize() {
        _resultProperty.createBinding(patternField.textProperty()) {
            if (patternField.text.isBlank()) null else GlobIgnoreMatcher(patternField.text)
        }

    }
}

/**
 * A [Form] for creating a [RegexIgnoreMatcher].
 */
class RegexIgnoreMatcherForm : IgnoreMatcherForm, VBox() {

    @FXML
    private lateinit var patternField: JFXTextField

    private val _resultProperty = SimpleObjectProperty<IgnoreMatcher?>()

    override val resultProperty: ReadOnlyProperty<IgnoreMatcher?> = _resultProperty

    override val node: Node = this

    init {
        loadFxml(this, "/fxml/forms/RegexIgnoreMatcherForm.fxml")
    }

    override fun clear() {
        patternField.text = ""
    }

    @FXML
    fun initialize() {
        _resultProperty.createBinding(patternField.textProperty()) {
            if (patternField.text.isBlank()) null else RegexIgnoreMatcher(patternField.text)
        }

    }
}

/**
 * A [Form] for creating a [SizeIgnoreMatcher].
 */
class SizeIgnoreMatcherForm : IgnoreMatcherForm, VBox() {

    @FXML
    private lateinit var sizeField: JFXTextField

    private val _resultProperty = SimpleObjectProperty<IgnoreMatcher?>()

    override val resultProperty: ReadOnlyProperty<IgnoreMatcher?> = _resultProperty

    override val node: Node = this

    init {
        loadFxml(this, "/fxml/forms/SizeIgnoreMatcherForm.fxml")
    }

    override fun clear() {
        sizeField.text = ""
    }

    @FXML
    fun initialize() {
        _resultProperty.createBinding(sizeField.textProperty()) {
            parseBytes(sizeField.text)?.let { SizeIgnoreMatcher(it) }
        }
    }
}

/**
 * A [Form] for creating an [ExtensionIgnoreMatcher].
 */
class ExtensionIgnoreMatcherForm : IgnoreMatcherForm, VBox() {

    @FXML
    private lateinit var extensionField: JFXTextField

    private val _resultProperty = SimpleObjectProperty<IgnoreMatcher?>()

    override val resultProperty: ReadOnlyProperty<IgnoreMatcher?> = _resultProperty

    override val node: Node = this

    init {
        loadFxml(this, "/fxml/forms/ExtensionIgnoreMatcherForm.fxml")
    }

    override fun clear() {
        extensionField.text = ""
    }

    @FXML
    fun initialize() {
        _resultProperty.createBinding(extensionField.textProperty()) {
            if (extensionField.text.isBlank()) null else ExtensionIgnoreMatcher(extensionField.text)
        }
    }
}

/**
 * A cell factory for the combo box for selecting a category of files to ignore.
 */
private val categoryCellFactory = MappingCellFactory<IgnoreCategory> { it.description }

/**
 * A [Form] for creating a [CategoryIgnoreMatcher].
 */
class CategoryIgnoreMatcherForm : IgnoreMatcherForm, VBox() {

    @FXML
    private lateinit var categoryComboBox: JFXComboBox<IgnoreCategory>

    private val _resultProperty = SimpleObjectProperty<IgnoreMatcher?>()

    override val resultProperty: ReadOnlyProperty<IgnoreMatcher?> = _resultProperty

    override val node: Node = this

    init {
        loadFxml(this, "/fxml/forms/CategoryIgnoreMatcherForm.fxml")
    }

    override fun clear() {
        _resultProperty.value = null
    }

    @FXML
    fun initialize() {
        categoryComboBox.cellFactory = categoryCellFactory
        categoryComboBox.buttonCell = categoryCellFactory.call(null)

        categoryComboBox.selectionModel.selectedItemProperty().addListener { _, _, newValue ->
            _resultProperty.value = CategoryIgnoreMatcher(newValue)
        }

        categoryComboBox.items.setAll(*IgnoreCategory.values())
        categoryComboBox.selectionModel.select(0)
    }
}
