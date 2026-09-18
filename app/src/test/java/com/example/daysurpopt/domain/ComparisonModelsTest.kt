// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ComparisonModelsTest {

    @Test
    fun computeDeltaObjectives_includes_legacy_gap_and_final_capital() {
        val p1 = ObjectiveResults(0.0, 0.0, 0.0, 0.10, 0.20, true, 60000.0, 10000.0)
        val p2 = ObjectiveResults(0.0, 0.0, 0.0, 0.08, 0.18, true, 70000.0, 20000.0)

        val delta = DeltaCalculator.computeDeltaObjectives(p1, p2)

        assertEquals(10000.0, delta.deltaLegacyGap, 1e-9)
        assertEquals(10000.0, delta.deltaFinalCapital, 1e-9)
    }
}
