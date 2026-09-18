// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParetoModelsTest {

    @Test
    fun dominates_requires_better_or_equal_on_all_objectives_and_strict_improvement_on_one() {
        val a = ParetoPoint(
            params = ParamsCandidate(0.2, 60, 0.1, 65),
            avgUtility = 0.30,
            stdDevUtility = 0.10,
            isFeasible = true,
            finalCapital = 80000.0,
            legacyGap = 30000.0
        )
        val b = ParetoPoint(
            params = ParamsCandidate(0.3, 61, 0.2, 67),
            avgUtility = 0.25,
            stdDevUtility = 0.15,
            isFeasible = true,
            finalCapital = 78000.0,
            legacyGap = 28000.0
        )

        assertTrue(a.dominates(b))
        assertFalse(b.dominates(a))
    }

    @Test
    fun infeasible_point_never_dominates_feasible_point() {
        val feasible = ParetoPoint(
            params = ParamsCandidate(0.2, 60, 0.1, 65),
            avgUtility = 0.20,
            stdDevUtility = 0.30,
            isFeasible = true,
            finalCapital = 50000.0,
            legacyGap = 0.0
        )
        val infeasible = feasible.copy(isFeasible = false, legacyGap = -1000.0)

        assertFalse(infeasible.dominates(feasible))
        assertTrue(feasible.constraintDominates(infeasible))
    }
}
