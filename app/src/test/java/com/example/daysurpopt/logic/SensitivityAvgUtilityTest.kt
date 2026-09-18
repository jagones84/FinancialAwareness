// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.R
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SimulationYear
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sensitivity analysis must measure the sensitivity of the AVERAGE UTILITY
 * (happiness) with respect to each input, NOT of the scalarized objective
 * fObjW = Avg * ((1 - w) + w * Stability). The objective blends stability into
 * the metric, which makes rows like the interest rate answer a different
 * question than "what happens to my happiness if ...".
 *
 * The user-facing unit steps stay unchanged: e.g. the Daily Surplus row is
 * expressed per +100 EUR/month of extra earnings.
 */
class SensitivityAvgUtilityTest {

    private val inputs = FinancialInput(
        eredita = 20000.0,
        soldiDaConservare = 10000.0,
        tfrNetto = 25000.0,
        tassoGuadagnoInteresse = 0.02,
        tassoInteresseDebito = 0.07,
        sogliaMinimaFunzioneUtilita = 0.1,
        capitaleIniziale = 15000.0,
        valoreSpesaGiornalieraMaxUtilita = 82.0,
        etaAttuale = 30,
        etaPensione = 65,
        etaRicevimentoEredita = 55,
        etaMorte = 82,
        p1SavingRatioSurplus = 0.40,
        p2EtaFineRisparmioNoCapitale = 51,
        p3PercentualeCapitaleDaSpendereAnnualmente = 0.40,
        p4EtaAnticipataInizioSpesaCapitale = 57,
        bonusStdWeight = 0.15
    ).withDefaultAssumptionCurves()

    private val surplus = SurplusInput(
        stipendioMensile = 2500.0,
        mutuoAffitto = 600.0,
        mutuoAffittoFinoEta = 60
    )
    private val expenses = emptyList<SpecificExpense>()

    private fun averageUtility(years: List<SimulationYear>): Double {
        val samples = years.flatMap { year ->
            if (year.monthlyUtilitySamples.isNotEmpty()) year.monthlyUtilitySamples
            else listOf(year.funzioneUtilita)
        }
        return samples.average()
    }

    @Test
    fun interest_rate_row_measures_average_utility_finite_difference() = runBlocking {
        val results = OptimizationLogic.runSensitivityAnalysis(inputs, expenses, surplus)
        val row = results.first { it.nameResId == R.string.sens_int_rate }

        val baseYears = calculateSimulation(inputs, expenses, surplus)
        val u0 = averageUtility(baseYears)
        val perturbed = inputs.copy(tassoGuadagnoInteresse = inputs.tassoGuadagnoInteresse + 0.001)
        val u1 = averageUtility(calculateSimulation(perturbed, expenses, surplus))
        // The row is "pt / 1pp": utility change per +1 percentage point of rate.
        // The implementation perturbs by +0.1pp (0.001 absolute) and reports (u1-u0)/0.1,
        // which equals dU/d(rate) * 0.01.
        val expected = (u1 - u0) * 10.0

        assertTrue("Scenario must react to the perturbation (u1=$u1, u0=$u0)", u1 != u0)
        assertTrue("Scenario must have non-zero utility spread so fObjW != AvgUtility", calculateStandardDeviation(baseYears.flatMap { it.monthlyUtilitySamples }) > 0.0)
        assertEquals(
            "Interest-rate sensitivity must equal d(AverageUtility)/d(rate), got ${row.scaledImpact} expected $expected",
            expected,
            row.scaledImpact,
            1e-6 + 1e-6 * kotlin.math.abs(expected)
        )
    }

    @Test
    fun surplus_row_is_positive_and_expressed_per_100eur_month() = runBlocking {
        val results = OptimizationLogic.runSensitivityAnalysis(inputs, expenses, surplus)
        val row = results.first { it.nameResId == R.string.sens_surplus }

        val u0 = averageUtility(calculateSimulation(inputs, expenses, surplus))
        val u1 = averageUtility(calculateSimulation(inputs, expenses, surplus, surplusOffset = 1.0))
        val expected = (u1 - u0) * (100.0 * 12.0 / 365.25)

        assertTrue("Extra earnings must raise average utility (u0=$u0, u1=$u1)", expected > 0.0)
        assertEquals(R.string.unit_pt_100eur, row.unitResId)
        assertEquals(
            "Daily-surplus sensitivity must equal d(AverageUtility) per +100 EUR/month, got ${row.scaledImpact} expected $expected",
            expected,
            row.scaledImpact,
            1e-6 + 1e-6 * kotlin.math.abs(expected)
        )
    }

    @Test
    fun bonus_weight_row_is_not_reported() = runBlocking {
        val results = OptimizationLogic.runSensitivityAnalysis(inputs, expenses, surplus)
        assertFalse(
            "bonusStdWeight defines the objective, it does not move the average utility: it must not appear",
            results.any { it.nameResId == R.string.sens_bonus_weight }
        )
    }
}
