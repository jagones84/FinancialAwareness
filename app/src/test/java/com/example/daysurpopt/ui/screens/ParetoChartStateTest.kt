// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.OptimizationMarkerSnapshot
import com.example.daysurpopt.domain.OptimizationMode
import com.example.daysurpopt.domain.ParamsCandidate
import com.example.daysurpopt.domain.ParetoPoint
import com.example.daysurpopt.domain.toOptimizationMarkerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParetoChartStateTest {

    @Test
    fun paretoPoint_toOptimizationMarkerSnapshot_preserves_params_metrics_and_weight() {
        val point = ParetoPoint(
            params = ParamsCandidate(0.40, 62, 0.35, 68),
            avgUtility = 0.31,
            stdDevUtility = 0.09,
            isFeasible = true,
            finalCapital = 80000.0,
            legacyGap = 12000.0,
            kneeScore = 0.07
        )

        val snapshot = point.toOptimizationMarkerSnapshot(
            mode = OptimizationMode.PARETO_FRONT,
            objectiveValue = 0.44,
            stabilityIndex = 0.18,
            weightUsed = 0.75
        )

        assertEquals(OptimizationMode.PARETO_FRONT, snapshot.mode)
        assertEquals(62, snapshot.params.p2)
        assertEquals(0.44, snapshot.objectiveValue, 1e-9)
        assertEquals(0.75, snapshot.weightUsed, 1e-9)
    }

    @Test
    fun applyOptimizationParamsForTest_copies_pareto_params_into_live_inputs() {
        val point = ParetoPoint(
            params = ParamsCandidate(0.45, 63, 0.30, 69),
            avgUtility = 0.32,
            stdDevUtility = 0.11,
            isFeasible = true,
            finalCapital = 82000.0,
            legacyGap = 10000.0
        )

        val updated = applyOptimizationParamsForTest(
            baseInputs = FinancialInput(),
            point = point
        )

        assertEquals(0.45, updated.p1SavingRatioSurplus, 1e-9)
        assertEquals(63, updated.p2EtaFineRisparmioNoCapitale)
        assertEquals(0.30, updated.p3PercentualeCapitaleDaSpendereAnnualmente, 1e-9)
        assertEquals(69, updated.p4EtaAnticipataInizioSpesaCapitale)
    }

    @Test
    fun shouldSyncSelectedParetoPointToReference_only_when_previous_selection_was_reference_like() {
        val reference = ParetoPoint(
            params = ParamsCandidate(0.25, 61, 0.20, 67),
            avgUtility = 0.24,
            stdDevUtility = 0.20,
            isFeasible = true,
            finalCapital = 72000.0,
            legacyGap = 7000.0
        )
        val manual = ParetoPoint(
            params = ParamsCandidate(0.45, 63, 0.35, 69),
            avgUtility = 0.32,
            stdDevUtility = 0.10,
            isFeasible = true,
            finalCapital = 84000.0,
            legacyGap = 13000.0
        )

        assertTrue(shouldSyncSelectedParetoPointToReference(previousSelection = null, previousReference = reference))
        assertTrue(shouldSyncSelectedParetoPointToReference(previousSelection = reference, previousReference = reference))
        assertFalse(shouldSyncSelectedParetoPointToReference(previousSelection = manual, previousReference = reference))
    }

    @Test
    fun shouldSyncAppliedParetoPointToReference_only_when_previous_applied_matches_reference() {
        val reference = ParetoPoint(
            params = ParamsCandidate(0.25, 61, 0.20, 67),
            avgUtility = 0.24,
            stdDevUtility = 0.20,
            isFeasible = true,
            finalCapital = 72000.0,
            legacyGap = 7000.0
        )
        val manualSnapshot = OptimizationMarkerSnapshot(
            mode = OptimizationMode.PARETO_FRONT,
            params = ParamsCandidate(0.45, 63, 0.35, 69),
            objectiveValue = 0.41,
            avgUtility = 0.32,
            stdDevUtility = 0.10,
            stabilityIndex = 0.10,
            weightUsed = 0.60
        )
        val referenceSnapshot = reference.toOptimizationMarkerSnapshot(
            mode = OptimizationMode.PARETO_FRONT,
            objectiveValue = 0.33,
            stabilityIndex = 0.20,
            weightUsed = 0.50
        )

        assertTrue(shouldSyncAppliedParetoPointToReference(previousApplied = null, previousReference = reference))
        assertTrue(
            shouldSyncAppliedParetoPointToReference(
                previousApplied = referenceSnapshot,
                previousReference = reference
            )
        )
        assertFalse(
            shouldSyncAppliedParetoPointToReference(
                previousApplied = manualSnapshot,
                previousReference = reference
            )
        )
    }
}
