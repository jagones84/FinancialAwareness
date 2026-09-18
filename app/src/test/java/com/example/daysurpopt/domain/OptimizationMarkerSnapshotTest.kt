// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OptimizationMarkerSnapshotTest {

    @Test
    fun optimizationMarkerSnapshot_preserves_mode_params_and_weight() {
        val snapshot = OptimizationMarkerSnapshot(
            mode = OptimizationMode.TRUE_SCALAR,
            params = ParamsCandidate(0.25, 61, 0.40, 70),
            objectiveValue = 0.42,
            avgUtility = 0.31,
            stdDevUtility = 0.08,
            stabilityIndex = 0.27,
            weightUsed = 0.55,
            kneeScore = 0.11
        )

        assertEquals(OptimizationMode.TRUE_SCALAR, snapshot.mode)
        assertEquals(61, snapshot.params.p2)
        assertEquals(0.55, snapshot.weightUsed, 1e-9)
    }

    @Test
    fun paretoPoint_toOptimizationMarkerSnapshot_preserves_metrics_and_supplied_objective() {
        val point = ParetoPoint(
            params = ParamsCandidate(0.3, 61, 0.4, 68),
            avgUtility = 0.25,
            stdDevUtility = 0.10,
            isFeasible = true,
            finalCapital = 70000.0,
            legacyGap = 20000.0,
            kneeScore = 0.09
        )

        val snapshot = point.toOptimizationMarkerSnapshot(
            mode = OptimizationMode.PARETO_FRONT,
            objectiveValue = 0.44,
            stabilityIndex = 0.2,
            weightUsed = 0.8
        )

        assertEquals(OptimizationMode.PARETO_FRONT, snapshot.mode)
        assertEquals(0.44, snapshot.objectiveValue, 1e-9)
        assertEquals(0.09, snapshot.kneeScore ?: error("Missing knee score"), 1e-9)
        assertEquals(0.8, snapshot.weightUsed, 1e-9)
    }
}
