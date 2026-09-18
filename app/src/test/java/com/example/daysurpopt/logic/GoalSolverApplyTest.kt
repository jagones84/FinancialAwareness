// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.CurvePoint
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SurplusInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Applying the Goal Solver answer must reproduce the goal in the OFFICIAL
 * simulation. The required capital is only valid for the goal plan shape
 * (stop work at X, save until X, spend exactly the utility minimum: p3 = 0),
 * so "Apply" must install the WHOLE plan, not just the initial capital.
 * Applying the capital alone leaves the user's own p2/p3/p4/pension age in
 * place and the simulation then contradicts the solver's promise.
 */
class GoalSolverApplyTest {

    private fun customDegradationPoints(floor: Double): List<CurvePoint> = listOf(
        CurvePoint(40.0, 1.0),
        CurvePoint(60.0, 0.8),
        CurvePoint(82.0, floor)
    )

    private fun baseInputs(threshold: Double): FinancialInput {
        val base = FinancialInput(
            eredita = 80000.0,
            etaRicevimentoEredita = 90,
            soldiDaConservare = 0.0,
            tfrNetto = 0.0,
            tassoGuadagnoInteresse = 0.02,
            tassoInteresseDebito = 0.07,
            sogliaMinimaFunzioneUtilita = threshold,
            capitaleIniziale = 0.0,
            valoreSpesaGiornalieraMaxUtilita = 82.0,
            etaAttuale = 40,
            etaPensione = 65,
            etaMorte = 82,
            p1SavingRatioSurplus = 0.40,
            p2EtaFineRisparmioNoCapitale = 51,
            p3PercentualeCapitaleDaSpendereAnnualmente = 0.40,
            p4EtaAnticipataInizioSpesaCapitale = 57,
            bonusStdWeight = 0.0
        )
        return base.copy(degradationCurvePoints = customDegradationPoints(0.5))
            .withDefaultAssumptionCurves()
    }

    private fun zeroIncomeSurplus(): SurplusInput = SurplusInput(
        stipendioMensile = 0.0,
        premioRisultatoNettoAnnuale = 0.0,
        tredicesimaQuattordicesimaNetto = 0.0,
        pensioneMensileNetta = 0.0,
        tredicesimaQuattordicesimaNettoPensione = 0.0,
        mutuoAffitto = 0.0,
        mutuoAffittoFinoEta = 0,
        condominioLavorativa = 0.0,
        bolletteLavorativa = 0.0,
        ciboLavorativa = 0.0,
        veicoliLavorativa = 0.0,
        palestraLavorativa = 0.0,
        trasportiViaggiLavorativa = 0.0,
        saluteLavorativa = 0.0,
        vacanzeLavorativa = 0.0,
        shoppingLavorativa = 0.0,
        altroLavorativa = 0.0,
        condominioPensione = 0.0,
        bollettePensione = 0.0,
        ciboPensione = 0.0,
        veicoliPensione = 0.0,
        palestraPensione = 0.0,
        trasportiViaggiPensione = 0.0,
        salutePensione = 0.0,
        vacanzePensione = 0.0,
        shoppingPensione = 0.0,
        altroPensione = 0.0
    )

    private val stopWorkAge = 40
    private val threshold = 0.3

    @Test
    fun apply_builds_full_goal_plan_not_capital_only() {
        val base = baseInputs(threshold)
        val result = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs = base,
            specificExpenses = emptyList(),
            surplusData = zeroIncomeSurplus(),
            stopWorkAge = stopWorkAge,
            threshold = threshold
        )
        assertTrue(result.isFeasible)
        val capital = result.requiredCapital!!

        val applied = GoalSolverLogic.buildGoalApplyInputs(base, result)

        assertEquals("Stop work must move the pension age", stopWorkAge, applied.etaPensione)
        assertEquals("Saving must stop at the stop-work age", stopWorkAge, applied.p2EtaFineRisparmioNoCapitale)
        assertEquals("Capital spending must start at the stop-work age", stopWorkAge, applied.p4EtaAnticipataInizioSpesaCapitale)
        assertEquals("The goal plan spends exactly the utility minimum", 0.0, applied.p3PercentualeCapitaleDaSpendereAnnualmente, 0.0)
        assertEquals("The happiness threshold must be installed", threshold, applied.sogliaMinimaFunzioneUtilita, 0.0)
        assertEquals("The required capital must be installed", capital, applied.capitaleIniziale, 1e-6)
        assertEquals("User curves must be preserved", base.degradationCurvePoints, applied.degradationCurvePoints)
    }

    @Test
    fun applied_goal_plan_satisfies_goal_in_official_simulation() {
        val base = baseInputs(threshold)
        val result = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs = base,
            specificExpenses = emptyList(),
            surplusData = zeroIncomeSurplus(),
            stopWorkAge = stopWorkAge,
            threshold = threshold
        )
        assertTrue(result.isFeasible)

        val applied = GoalSolverLogic.buildGoalApplyInputs(base, result)
        val years = calculateSimulation(applied, emptyList(), zeroIncomeSurplus())

        assertTrue(years.isNotEmpty())
        assertTrue(
            "Official simulation of the applied plan must never go into debt",
            years.all { it.debtAmount <= 1e-6 }
        )
        assertTrue(
            "Official simulation of the applied plan must never violate the legacy",
            years.none { it.violazioneLascito }
        )
        assertTrue(
            "Official simulation of the applied plan must keep every monthly utility sample >= threshold",
            years.all { year ->
                year.monthlyUtilitySamples.all { it >= threshold - 1e-6 }
            }
        )
    }

    /**
     * Answers "P3 = 0 means not spending capital?": after the stop-work age the
     * plan has no work income, so the engine funds the utility minimum FROM
     * CAPITAL, and it spends exactly that amount - no more, no less. The spent
     * amount is the unknown "how much capital do I need per year for the
     * threshold", solved year by year by the engine (that is why no single
     * percentage P3 can express it). Characterization lock of that behavior.
     */
    @Test
    fun applied_goal_plan_spends_capital_exactly_at_the_threshold_after_stop() {
        val base = baseInputs(threshold)
        val result = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs = base,
            specificExpenses = emptyList(),
            surplusData = zeroIncomeSurplus(),
            stopWorkAge = stopWorkAge,
            threshold = threshold
        )
        assertTrue(result.isFeasible)
        val required = result.requiredCapital!!

        val applied = GoalSolverLogic.buildGoalApplyInputs(base, result)
        val years = calculateSimulation(applied, emptyList(), zeroIncomeSurplus())

        val afterStop = years.filter { it.eta >= stopWorkAge }
        assertTrue(afterStop.isNotEmpty())
        assertTrue(
            "With no income after the stop age the plan must erode capital to fund the threshold",
            years.last().capitaleFineAnno < required * 0.5
        )
        afterStop.forEach { year ->
            year.monthlyUtilitySamples.forEach { sample ->
                assertEquals(
                    "After the stop the plan must spend exactly the threshold amount (utility = T, solved per month)",
                    threshold,
                    sample,
                    1e-6
                )
            }
        }
    }
}
