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

package io.github.lostatc.reversion.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import io.github.lostatc.reversion.cli.repo.Repo
import io.github.lostatc.reversion.cli.snapshot.Snapshot
import io.github.lostatc.reversion.cli.tag.Tag
import io.github.lostatc.reversion.cli.timeline.Timeline
import io.github.lostatc.reversion.cli.workdir.WorkDir

/**
 * The main command of the CLI.
 */
class Reversion : CliktCommand() {
    init {
        subcommands(
            Repo(),
            Timeline(),
            Snapshot(),
            Tag(),
            WorkDir()
        )
    }

    override fun run() {

    }
}
