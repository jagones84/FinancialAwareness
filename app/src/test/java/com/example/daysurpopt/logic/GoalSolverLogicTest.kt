// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.CurvePoint
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SurplusInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Goal Solver: answers "how much capital do I need to accumulate so that I can
 * stop working at age X while keeping my happiness (utility) at or above T?".
 *
 * Semantics mapped onto existing engine primitives:
 *  - "Stop working at X" -> etaPensione = X (work-income bucket stops at X, pension
 *    income from the current SurplusInput applies after X; pass a zero-income
 *    SurplusInput for a pure capital-based plan).
 *  - "Happiness >= T" -> sogliaMinimaFunzioneUtilita = T; the engine already forces
 *    the minimum monthly spend achieving it, drawing capital (debt when exhausted).
 *  - "Feasible plan" -> no debt ever, no legacy violation, all utility samples >= T.
 *  - The what-if plan forces p3 = 0 (spend exactly the utility minimum) and
 *    p2 = p4 = stopWorkAge; the bisection variable is capitaleIniziale.
 *
 * The feasibility predicate is monotone in initial capital, so bisection converges.
 */
class GoalSolverLogicTest {

    private fun customDegradationPoints(floor: Double): List<CurvePoint> = listOf(
        CurvePoint(40.0, 1.0),
        CurvePoint(60.0, 0.8),
        CurvePoint(82.0, floor)
    )

    private fun baseInputs(threshold: Double, initialCapital: Double, degradationFloor: Double? = null): FinancialInput {
        val base = FinancialInput(
            eredita = 80000.0,
            etaRicevimentoEredita = 90,
            soldiDaConservare = 0.0,
            tfrNetto = 0.0,
            tassoGuadagnoInteresse = 0.02,
            tassoInteresseDebito = 0.07,
            sogliaMinimaFunzioneUtilita = threshold,
            capitaleIniziale = initialCapital,
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
        return if (degradationFloor != null) {
            base.copy(degradationCurvePoints = customDegradationPoints(degradationFloor))
                .withDefaultAssumptionCurves()
        } else {
            base.withDefaultAssumptionCurves()
        }
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

    @Test
    fun solveMinimumCapital_matchesReferenceBisectionScenario() {
        val result = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs = baseInputs(0.3, 0.0, degradationFloor = 0.5),
            specificExpenses = emptyList(),
            surplusData = zeroIncomeSurplus(),
            stopWorkAge = 40,
            threshold = 0.3
        )

        println("Minimum capital to stop working at 40 with happiness >= 0.3: ${result.requiredCapital}")

        assertTrue(result.isFeasible)
        assertEquals(275390.625, result.requiredCapital!!, 1000.0)

        // Engine-level double check: feasible at the solved capital, infeasible just below.
        val whatIf = GoalSolverLogic.buildGoalWhatIfInputs(
            baseInputs(0.3, 0.0, degradationFloor = 0.5), 0.3, 40, result.requiredCapital!!
        )
        val years = calculateSimulation(whatIf, emptyList(), zeroIncomeSurplus())
        assertTrue(years.all { it.debtAmount <= 1e-6 && it.funzioneUtilita >= 0.3 - 1e-6 })

        val below = GoalSolverLogic.buildGoalWhatIfInputs(
            baseInputs(0.3, 0.0, degradationFloor = 0.5), 0.3, 40, result.requiredCapital!! - 2000.0
        )
        val yearsBelow = calculateSimulation(below, emptyList(), zeroIncomeSurplus())
        assertTrue(yearsBelow.any { it.debtAmount > 1e-6 || it.funzioneUtilita < 0.3 - 1e-6 })
    }

    @Test
    fun higherThreshold_requiresMoreCapital() {
        val needed025 = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs(0.25, 0.0, degradationFloor = 0.5), emptyList(), zeroIncomeSurplus(), 40, 0.25
        )
        val needed030 = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs(0.30, 0.0, degradationFloor = 0.5), emptyList(), zeroIncomeSurplus(), 40, 0.30
        )

        assertTrue(needed025.isFeasible && needed030.isFeasible)
        assertTrue(
            "Threshold 0.30 must require more capital than 0.25",
            needed030.requiredCapital!! > needed025.requiredCapital!!
        )
    }

    @Test
    fun planIncome_reducesRequiredCapital() {
        val withDefaultIncome = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs(0.25, 0.0, degradationFloor = 0.5), emptyList(), SurplusInput(), 50, 0.25
        )
        val withZeroIncome = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs(0.25, 0.0, degradationFloor = 0.5), emptyList(), zeroIncomeSurplus(), 50, 0.25
        )

        assertTrue(withDefaultIncome.isFeasible && withZeroIncome.isFeasible)
        assertTrue(
            "Salary savings before stop age and pension after must reduce required capital",
            withDefaultIncome.requiredCapital!! < withZeroIncome.requiredCapital!!
        )
    }

    @Test
    fun bequestConstraint_increasesRequiredCapital() {
        val noBequest = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs(0.25, 0.0, degradationFloor = 0.5), emptyList(), zeroIncomeSurplus(), 40, 0.25
        )
        val withBequest = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs(0.25, 0.0, degradationFloor = 0.5).copy(soldiDaConservare = 20000.0),
            emptyList(),
            zeroIncomeSurplus(),
            40,
            0.25
        )

        assertTrue(noBequest.isFeasible && withBequest.isFeasible)
        assertTrue(
            "Preserving a legacy must require more capital",
            withBequest.requiredCapital!! > noBequest.requiredCapital!!
        )
    }

    @Test
    fun validateThreshold_rejectsUnreachableThresholdWithDefaultCurves() {
        val validationHigh = GoalSolverLogic.validateThreshold(
            baseInputs(0.299, 0.0), 0.299
        )
        assertFalse(
            "Default curves ceiling (~0.9347 utility x ~0.316 degradation) cannot reach 0.299",
            validationHigh.isAchievable
        )
        assertTrue(validationHigh.maxAchievableUtility < 0.299)

        val validationLow = GoalSolverLogic.validateThreshold(
            baseInputs(0.29, 0.0), 0.29
        )
        assertTrue(validationLow.isAchievable)
        assertTrue(validationLow.maxAchievableUtility >= 0.29)
        assertEquals(0.9347, validationLow.utilityCurveCeiling, 0.01)
        assertEquals(0.3157, validationLow.minDegradation, 0.01)
    }

    @Test
    fun solveMinimumCapital_returnsInfeasibleWithReason_whenThresholdUnreachable() {
        val result = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs(0.299, 0.0), emptyList(), zeroIncomeSurplus(), 40, 0.299
        )

        assertFalse(result.isFeasible)
        assertNull(result.requiredCapital)
        assertTrue(
            "Reason must explain the ceiling problem",
            result.reason?.contains("utility", ignoreCase = true) == true
        )
    }

    @Test
    fun invalidStopWorkAge_isRejected() {
        val tooLate = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs(0.2, 0.0, degradationFloor = 0.5), emptyList(), zeroIncomeSurplus(), 82, 0.2
        )
        val beforeCurrentAge = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs(0.2, 0.0, degradationFloor = 0.5), emptyList(), zeroIncomeSurplus(), 39, 0.2
        )

        assertFalse(tooLate.isFeasible)
        assertNull(tooLate.requiredCapital)
        assertFalse(beforeCurrentAge.isFeasible)
        assertNull(beforeCurrentAge.requiredCapital)
    }
}
