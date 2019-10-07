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

package io.github.lostatc.reversion.api

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

/**
 * A rule specifying how old versions of files are cleaned up.
 *
 * For the first [timeFrame] after a new version of a file is created, this policy will only keep [maxVersions] versions
 * of that file for every [minInterval] interval.
 *
 * Each [CleanupPolicy] has a [description] that is shown in the UI.
 *
 * @param [minInterval] The interval of time.
 * @param [timeFrame] The maximum amount of time to keep files for.
 * @param [maxVersions] The maximum number of versions to keep for each interval.
 * @param [description] A human-readable description of the policy.
 */
data class CleanupPolicy(
    val minInterval: Duration,
    val timeFrame: Duration,
    val maxVersions: Int,
    val description: String
)

/**
 * A factory for creating [CleanupPolicy] objects.
 */
interface CleanupPolicyFactory {
    /**
     * The plural name of the unit.
     */
    private val TemporalUnit.pluralName: String
        get() = when (this) {
            ChronoUnit.SECONDS -> "seconds"
            ChronoUnit.MINUTES -> "minutes"
            ChronoUnit.HOURS -> "hours"
            ChronoUnit.DAYS -> "days"
            ChronoUnit.WEEKS -> "weeks"
            ChronoUnit.MONTHS -> "months"
            else -> toString().toLowerCase()
        }

    /**
     * The singular name of the unit.
     */
    private val TemporalUnit.singularName: String
        get() = when (this) {
            ChronoUnit.SECONDS -> "second"
            ChronoUnit.MINUTES -> "minute"
            ChronoUnit.HOURS -> "hour"
            ChronoUnit.DAYS -> "day"
            ChronoUnit.WEEKS -> "week"
            ChronoUnit.MONTHS -> "month"
            else -> toString().toLowerCase()
        }

    /**
     * Creates a [CleanupPolicy].
     *
     * @param [minInterval] The interval of time.
     * @param [timeFrame] The maximum amount of time to keep files for.
     * @param [maxVersions] The maximum number of versions to keep for each interval.
     * @param [description] A human-readable description of the policy.
     */
    fun of(minInterval: Duration, timeFrame: Duration, maxVersions: Int, description: String): CleanupPolicy

    /**
     * Creates a [CleanupPolicy] that keeps staggered versions.
     *
     * The [minInterval][CleanupPolicy.minInterval] and [timeFrame][CleanupPolicy.timeFrame] of the created
     * [CleanupPolicy] may be based on estimated durations.
     *
     * @param [versions] The maximum number of versions to keep.
     * @param [unit] The amount of time between versions.
     */
    fun ofStaggered(versions: Int, unit: TemporalUnit): CleanupPolicy = of(
        minInterval = unit.duration,
        timeFrame = unit.duration.multipliedBy(versions.toLong()),
        maxVersions = 1,
        description = "For the last $versions ${unit.pluralName}, keep only the last version from each ${unit.singularName}."
    )

    /**
     * Creates a [CleanupPolicy] that keeps a given number of [versions] of each file.
     */
    fun ofVersions(versions: Int): CleanupPolicy = of(
        minInterval = ChronoUnit.FOREVER.duration,
        timeFrame = ChronoUnit.FOREVER.duration,
        maxVersions = versions,
        description = "Keep $versions versions of each file."
    )

    /**
     * Creates a [CleanupPolicy] that keeps each version for a given amount of time.
     *
     * The [minInterval][CleanupPolicy.minInterval] and [timeFrame][CleanupPolicy.timeFrame] of the created
     * [CleanupPolicy] may be based on estimated durations.
     *
     * @param [amount] The amount of time in terms of [unit].
     * @param [unit] The unit to measure the duration in.
     */
    fun ofDuration(amount: Long, unit: TemporalUnit): CleanupPolicy = of(
        minInterval = unit.duration.multipliedBy(amount),
        timeFrame = unit.duration.multipliedBy(amount),
        maxVersions = Int.MAX_VALUE,
        description = "Keep each version for $amount ${unit.pluralName}."
    )

    /**
     * Creates a [CleanupPolicy] that keeps each version forever.
     */
    fun forever(): CleanupPolicy = of(
        minInterval = ChronoUnit.FOREVER.duration,
        timeFrame = ChronoUnit.FOREVER.duration,
        maxVersions = Int.MAX_VALUE,
        description = "Keep each version forever."
    )
}

/**
 * Returns a new [Duration] truncated to the given [unit].
 *
 * This also reduces the duration to the longest duration which can be stored in a [Long] in terms of the given [unit].
 */
private fun Duration.inTermsOf(unit: TemporalUnit): Duration {
    val quotient = try {
        dividedBy(unit.duration)
    } catch (e: ArithmeticException) {
        Long.MAX_VALUE
    }

    return unit.duration.multipliedBy(quotient)
}


/**
 * A [CleanupPolicyFactory] that allows for serializing durations.
 *
 * This truncates durations in the [CleanupPolicy] to the given [unit] so that the [CleanupPolicy] is equal to
 * itself after being serialized and deserialized. If a duration is too long to be stored in a [Long] in terms of
 * [unit], it is shortened to the longest duration which can.
 *
 * @param [unit] The unit to serialize [Duration] objects as.
 */
data class TruncatingCleanupPolicyFactory(val unit: TemporalUnit) : CleanupPolicyFactory {
    override fun of(
        minInterval: Duration,
        timeFrame: Duration,
        maxVersions: Int,
        description: String
    ): CleanupPolicy {
        return CleanupPolicy(
            minInterval = minInterval.inTermsOf(unit),
            timeFrame = timeFrame.inTermsOf(unit),
            maxVersions = maxVersions,
            description = description
        )
    }
}
