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
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import io.github.lostatc.reversion.api.CleanupPolicy
import io.github.lostatc.reversion.storage.WorkDirectory
import java.nio.file.Path
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

class PolicyCommand(val parent: ReversionCommand) : CliktCommand(
    name = "policy", help = """
    Manage cleanup policies.

    Cleanup policies determine how old versions of files are deleted.
"""
) {

    val workPath: Path
        get() = parent.workPath

    init {
        subcommands(
            PolicyCreateCommand(this),
            PolicyListCommand(this),
            PolicyClearCommand(this)
        )
    }

    override fun run() = Unit
}

class PolicyCreateCommand(val parent: PolicyCommand) : CliktCommand(
    name = "create", help = """
    Create a new cleanup policy.

    By default, versions are kept forever and an infinite number of versions of each file are kept.
"""
) {
    val unit: TemporalUnit? by option("-u", "--unit", help = "A unit of time.")
        .choice(
            "seconds" to ChronoUnit.SECONDS,
            "minutes" to ChronoUnit.MINUTES,
            "hours" to ChronoUnit.HOURS,
            "days" to ChronoUnit.DAYS,
            "weeks" to ChronoUnit.WEEKS,
            "months" to ChronoUnit.MONTHS
        )

    val amount: Long? by option(
        "-a",
        "--amount",
        help = "The maximum amount of time to keep each version for in terms of the given unit."
    )
        .long()

    val versions: Int? by option(
        "-v",
        "--versions",
        help = "The maximum number of versions of each file to keep for each interval of the given unit."
    )
        .int()

    override fun run() {
        val workDirectory = WorkDirectory.open(parent.workPath)

        // Allow for smart casts.
        val unit = unit
        val amount = amount
        val versions = versions

        val policy = if (unit == null && amount == null) {
            if (versions == null) {
                CleanupPolicy.forever()
            } else {
                CleanupPolicy.ofVersions(versions)
            }
        } else if (unit != null && amount != null) {
            if (versions == null) {
                CleanupPolicy.ofDuration(amount, unit)
            } else {
                CleanupPolicy.ofStaggered(amount.toInt(), unit)
            }
        } else {
            throw CliktError("The options '--unit' and '--amount' must be specified together.")
        }

        workDirectory.timeline.cleanupPolicies += policy
    }
}

class PolicyListCommand(val parent: PolicyCommand) : CliktCommand(
    name = "list", help = """
    List the active cleanup policies.
"""
) {
    override fun run() {
        val workDirectory = WorkDirectory.open(parent.workPath)
        echo(workDirectory.timeline.cleanupPolicies.joinToString(separator = "\n") { it.description })
    }
}

class PolicyClearCommand(val parent: PolicyCommand) : CliktCommand(
    name = "clear", help = """
    Remove all active cleanup policies.
"""
) {
    override fun run() {
        val workDirectory = WorkDirectory.open(parent.workPath)
        workDirectory.timeline.cleanupPolicies = setOf()
    }
}
