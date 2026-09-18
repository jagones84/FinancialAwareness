// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.GAConfig
import com.example.daysurpopt.domain.ParamsCandidate
import com.example.daysurpopt.domain.ParetoPoint
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParetoOptimizationLogicTest {

    @Test
    fun nonDominatedFront_filters_dominated_points() {
        val points = listOf(
            ParetoPoint(ParamsCandidate(0.1, 60, 0.1, 65), 0.30, 0.10, true, 70000.0, 20000.0),
            ParetoPoint(ParamsCandidate(0.2, 60, 0.2, 65), 0.25, 0.15, true, 68000.0, 18000.0),
            ParetoPoint(ParamsCandidate(0.3, 60, 0.3, 65), 0.20, 0.25, true, 65000.0, 15000.0)
        )

        val front = ParetoOptimizationLogic.extractNonDominatedFront(points)

        assertEquals(1, front.size)
        assertTrue(front.first().avgUtility == 0.30)
    }

    @Test
    fun optimizeParetoParameters_returns_single_feasible_front_when_bounds_are_fixed() {
        val inputs = FinancialInput(
            p1SavingRatioSurplus = 0.5182,
            p2EtaFineRisparmioNoCapitale = 61,
            p3PercentualeCapitaleDaSpendereAnnualmente = 0.9171,
            p4EtaAnticipataInizioSpesaCapitale = 82,
            etaAttuale = 30,
            etaPensione = 67,
            etaMorte = 90,
            etaRicevimentoEredita = 65,
            soldiDaConservare = 50000.0,
            capitaleIniziale = 10000.0,
            valoreSpesaGiornalieraMaxUtilita = 10.0,
            sogliaMinimaFunzioneUtilita = 0.1,
            eredita = 0.0
        ).withDefaultAssumptionCurves()
        val fixed = ParamsCandidate(
            p1 = inputs.p1SavingRatioSurplus,
            p2 = inputs.p2EtaFineRisparmioNoCapitale,
            p3 = inputs.p3PercentualeCapitaleDaSpendereAnnualmente,
            p4 = inputs.p4EtaAnticipataInizioSpesaCapitale
        )
        val config = GAConfig(
            popSize = 4,
            generations = 2,
            pc = 0.7,
            pm = 0.08,
            min = fixed,
            max = fixed,
            maximize = true
        )

        val result = ParetoOptimizationLogic.optimizeParetoParameters(
            baseInputs = inputs,
            config = config,
            specificExpenses = emptyList<SpecificExpense>(),
            surplusData = SurplusInput()
        )

        assertFalse(result.points.isEmpty())
        assertEquals(1, result.points.size)
        assertTrue(result.points.first().isFeasible)
    }
}
