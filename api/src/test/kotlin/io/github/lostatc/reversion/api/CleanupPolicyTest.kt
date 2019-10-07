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

import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

class CleanupPolicyTest {
    val policyFactory: CleanupPolicyFactory = TruncatingCleanupPolicyFactory(ChronoUnit.MILLIS)

    @Test
    fun `time-based units don't throw`() {
        policyFactory.ofStaggered(24, ChronoUnit.HOURS)
    }

    @Test
    fun `date-based units don't throw`() {
        policyFactory.ofStaggered(12, ChronoUnit.MONTHS)
    }

    @Test
    fun `policy from versions doesn't throw`() {
        policyFactory.ofVersions(Int.MAX_VALUE)
        policyFactory.ofVersions(0)
    }

    @Test
    fun `policy from duration doesn't throw`() {
        policyFactory.ofDuration(0, ChronoUnit.YEARS)
        policyFactory.ofDuration(Long.MAX_VALUE, ChronoUnit.SECONDS)
    }
}
