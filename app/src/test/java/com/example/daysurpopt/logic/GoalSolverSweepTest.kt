// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.CurvePoint
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Goal Solver answer is a LOCUS, not a single number: for every saving
 * ratio P1 used while still working, there is a different minimum initial
 * capital that allows quitting at the stop-work age and never letting the
 * utility drop below the threshold. The sweep returns the (P1, capital_i)
 * table; the capital must be non-increasing in P1 (saving more today means
 * needing less capital today). Applying a row must install THAT row's plan.
 */
class GoalSolverSweepTest {

    private fun baseInputs(threshold: Double, p1: Double): FinancialInput {
        val base = FinancialInput(
            eredita = 0.0,
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
            p1SavingRatioSurplus = p1,
            p2EtaFineRisparmioNoCapitale = 51,
            p3PercentualeCapitaleDaSpendereAnnualmente = 0.40,
            p4EtaAnticipataInizioSpesaCapitale = 57,
            bonusStdWeight = 0.0
        )
        return base.copy(
            degradationCurvePoints = listOf(
                CurvePoint(40.0, 1.0),
                CurvePoint(60.0, 0.8),
                CurvePoint(82.0, 0.5)
            )
        ).withDefaultAssumptionCurves()
    }

    private fun workingSurplus(): SurplusInput = SurplusInput(
        stipendioMensile = 2500.0,
        premioRisultatoNettoAnnuale = 0.0,
        tredicesimaQuattordicesimaNetto = 0.0,
        pensioneMensileNetta = 0.0,
        tredicesimaQuattordicesimaNettoPensione = 0.0,
        mutuoAffitto = 600.0,
        mutuoAffittoFinoEta = 60,
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

    private val expenses = emptyList<SpecificExpense>()
    private val stopWorkAge = 55
    private val threshold = 0.25

    @Test
    fun sweep_returns_monotone_locus_over_p1_grid() {
        val sweep = GoalSolverLogic.solveCapitalVsSavingRatio(
            baseInputs = baseInputs(threshold, p1 = 0.4),
            specificExpenses = expenses,
            surplusData = workingSurplus(),
            stopWorkAge = stopWorkAge,
            threshold = threshold
        )

        assertEquals(threshold, sweep.threshold, 0.0)
        assertEquals(stopWorkAge, sweep.stopWorkAge)
        assertEquals("Default grid: 0%..100% step 10%", 11, sweep.rows.size)

        val feasible = sweep.rows.filter { it.isFeasible }
        assertTrue("Scenario must be feasible for at least the high-saving rows", feasible.isNotEmpty())
        assertEquals("Rows must be sorted by ascending P1", feasible.sortedBy { it.p1 }, feasible)

        val slack = GoalSolverLogic.DEFAULT_CAPITAL_TOLERANCE + 1.0
        for (i in 1 until feasible.size) {
            val prev = feasible[i - 1].requiredCapital
            val curr = feasible[i].requiredCapital
            assertNotNull(prev)
            assertNotNull(curr)
            assertTrue(
                "Required capital must be non-increasing in P1 (P1=${feasible[i - 1].p1} -> ${feasible[i].p1}: $prev -> $curr)",
                curr!! <= prev!! + slack
            )
        }

        val p1Zero = feasible.firstOrNull { it.p1 == 0.0 }
        val p1Full = feasible.lastOrNull { it.p1 == 1.0 }
        if (p1Zero != null && p1Full != null && p1Zero.isFeasible && p1Full.isFeasible) {
            assertTrue(
                "Saving nothing (P1=0) must need at least as much capital as saving everything",
                p1Full.requiredCapital!! <= p1Zero.requiredCapital!! + slack
            )
        }
    }

    @Test
    fun sweep_row_matches_single_solve_for_same_p1() {
        val base = baseInputs(threshold, p1 = 0.4)
        val sweep = GoalSolverLogic.solveCapitalVsSavingRatio(
            baseInputs = base,
            specificExpenses = expenses,
            surplusData = workingSurplus(),
            stopWorkAge = stopWorkAge,
            threshold = threshold
        )
        val single = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs = base.copy(p1SavingRatioSurplus = 0.4),
            specificExpenses = expenses,
            surplusData = workingSurplus(),
            stopWorkAge = stopWorkAge,
            threshold = threshold
        )

        val row = sweep.rows.first { kotlin.math.abs(it.p1 - 0.4) < 1e-9 }
        assertEquals(single.isFeasible, row.isFeasible)
        assertEquals(
            "The P1=40% row must equal the single solve for P1=40%",
            single.requiredCapital!!,
            row.requiredCapital!!,
            1e-6
        )
    }

    @Test
    fun sweep_marks_current_plan_row_even_off_grid() {
        val sweep = GoalSolverLogic.solveCapitalVsSavingRatio(
            baseInputs = baseInputs(threshold, p1 = 0.37),
            specificExpenses = expenses,
            surplusData = workingSurplus(),
            stopWorkAge = stopWorkAge,
            threshold = threshold
        )

        assertEquals("Off-grid current P1 adds one row to the 11 grid rows", 12, sweep.rows.size)
        val currentRows = sweep.rows.filter { it.isCurrentPlan }
        assertEquals("Exactly one row must be flagged as the current plan", 1, currentRows.size)
        assertEquals(0.37, currentRows.single().p1, 1e-9)
    }

    @Test
    fun sweep_returns_all_infeasible_when_threshold_unreachable() {
        val sweep = GoalSolverLogic.solveCapitalVsSavingRatio(
            baseInputs = baseInputs(threshold = 0.6, p1 = 0.4),
            specificExpenses = expenses,
            surplusData = workingSurplus(),
            stopWorkAge = stopWorkAge,
            threshold = 0.6
        )

        assertEquals(11, sweep.rows.size)
        assertTrue(sweep.rows.none { it.isFeasible })
        assertTrue(sweep.rows.all { it.requiredCapital == null })
        assertTrue("Must still report the max achievable utility", sweep.maxAchievableUtility > 0.0 && sweep.maxAchievableUtility < 0.6)
    }

    @Test
    fun apply_row_builds_plan_with_row_p1_and_capital() {
        val row = GoalSweepRow(p1 = 0.7, requiredCapital = 123456.0, isFeasible = true, isCurrentPlan = false)

        val applied = GoalSolverLogic.buildGoalApplyInputs(
            baseInputs = baseInputs(threshold, p1 = 0.4),
            threshold = threshold,
            stopWorkAge = stopWorkAge,
            row = row
        )

        assertEquals("Applying a row must install that row's saving ratio", 0.7, applied.p1SavingRatioSurplus, 0.0)
        assertEquals(123456.0, applied.capitaleIniziale, 1e-6)
        assertEquals(stopWorkAge, applied.etaPensione)
        assertEquals(stopWorkAge, applied.p2EtaFineRisparmioNoCapitale)
        assertEquals(stopWorkAge, applied.p4EtaAnticipataInizioSpesaCapitale)
        assertEquals(0.0, applied.p3PercentualeCapitaleDaSpendereAnnualmente, 0.0)
        assertEquals(threshold, applied.sogliaMinimaFunzioneUtilita, 0.0)
    }
}
