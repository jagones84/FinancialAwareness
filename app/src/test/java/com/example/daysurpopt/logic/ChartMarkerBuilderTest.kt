// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.OptimizationMarkerSnapshot
import com.example.daysurpopt.domain.OptimizationMode
import com.example.daysurpopt.domain.ParamsCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMarkerBuilderTest {

    @Test
    fun buildP1P2Markers_includes_current_true_scalar_pareto_knee_and_pareto_reference() {
        val current = FinancialInput(
            p1SavingRatioSurplus = 0.2,
            p2EtaFineRisparmioNoCapitale = 60,
            p3PercentualeCapitaleDaSpendereAnnualmente = 0.3,
            p4EtaAnticipataInizioSpesaCapitale = 65
        )
        val trueScalar = OptimizationMarkerSnapshot(
            mode = OptimizationMode.TRUE_SCALAR,
            params = ParamsCandidate(0.4, 62, 0.35, 68),
            objectiveValue = 0.5,
            avgUtility = 0.3,
            stdDevUtility = 0.1,
            stabilityIndex = 0.2,
            weightUsed = 0.5
        )
        val compromise = OptimizationMarkerSnapshot(
            mode = OptimizationMode.PARETO_KNEE,
            params = ParamsCandidate(0.45, 63, 0.32, 67),
            objectiveValue = 0.47,
            avgUtility = 0.29,
            stdDevUtility = 0.09,
            stabilityIndex = 0.18,
            weightUsed = 0.5
        )
        val pareto = OptimizationMarkerSnapshot(
            mode = OptimizationMode.PARETO_FRONT,
            params = ParamsCandidate(0.5, 64, 0.25, 69),
            objectiveValue = 0.45,
            avgUtility = 0.28,
            stdDevUtility = 0.07,
            stabilityIndex = 0.14,
            weightUsed = 0.8
        )

        val markers = ChartMarkerBuilder.buildP1P2Markers(
            inputs = current,
            currentObjective = 0.33,
            lastTrueScalar = trueScalar,
            lastParetoCompromise = compromise,
            lastParetoReference = pareto,
            currentLabel = "Current Inputs",
            trueScalarLabel = "True Scalar",
            paretoCompromiseLabel = "Pareto Knee",
            paretoReferenceLabel = "Pareto Reference"
        )

        assertEquals(4, markers.size)
        assertEquals("Current Inputs", markers[0]["name"])
        assertEquals("True Scalar", markers[1]["name"])
        assertEquals("Pareto Knee", markers[2]["name"])
        assertEquals("Pareto Reference", markers[3]["name"])
    }

    @Test
    fun buildP3P4Markers_uses_capital_spending_axes_for_all_snapshot_types() {
        val current = FinancialInput(
            p1SavingRatioSurplus = 0.2,
            p2EtaFineRisparmioNoCapitale = 60,
            p3PercentualeCapitaleDaSpendereAnnualmente = 0.3,
            p4EtaAnticipataInizioSpesaCapitale = 65
        )
        val trueScalar = OptimizationMarkerSnapshot(
            mode = OptimizationMode.TRUE_SCALAR,
            params = ParamsCandidate(0.4, 62, 0.35, 68),
            objectiveValue = 0.5,
            avgUtility = 0.3,
            stdDevUtility = 0.1,
            stabilityIndex = 0.2,
            weightUsed = 0.5
        )
        val compromise = OptimizationMarkerSnapshot(
            mode = OptimizationMode.PARETO_KNEE,
            params = ParamsCandidate(0.45, 63, 0.28, 69),
            objectiveValue = 0.47,
            avgUtility = 0.29,
            stdDevUtility = 0.09,
            stabilityIndex = 0.18,
            weightUsed = 0.5
        )

        val markers = ChartMarkerBuilder.buildP3P4Markers(
            inputs = current,
            currentObjective = 0.33,
            lastTrueScalar = trueScalar,
            lastParetoCompromise = compromise,
            lastParetoReference = null,
            currentLabel = "Current Inputs",
            trueScalarLabel = "True Scalar",
            paretoCompromiseLabel = "Pareto Knee",
            paretoReferenceLabel = "Pareto Reference"
        )

        assertEquals(listOf(0.3), markers[0]["x"])
        assertEquals(listOf(65), markers[0]["y"])
        assertEquals(listOf(0.35), markers[1]["x"])
        assertEquals(listOf(68), markers[1]["y"])
        assertEquals(listOf(0.28), markers[2]["x"])
        assertEquals(listOf(69), markers[2]["y"])
    }
}
