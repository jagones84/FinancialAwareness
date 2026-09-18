// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.CurvePoint
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SimulationYear
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Independent cross-validation of the Goal Solver against the OFFICIAL
 * simulation on arbitrary parameter sets (user request: "change the parameters
 * by yourself and cross-validate them"). For every scenario and every checked
 * (P1, capital) couple:
 *  1. at the solver's required capital the official engine MUST satisfy the
 *     goal (every monthly utility sample >= T, no debt, no legacy violation);
 *  2. below the required capital (C* - 2x tolerance, guaranteed to be under
 *     the bisection's infeasible bracket) the engine MUST violate the goal;
 *  3. with EXTRA capital the utility time-history MUST be byte-identical to
 *     the minimal plan (P3 = 0: the floor never spends the surplus capital -
 *     it only raises the final bequest). This is why the simulated time
 *     history flattens at T no matter how much capital you actually own.
 */
class GoalSolverCrossValidationTest {

    data class Scenario(
        val name: String,
        val inputs: FinancialInput,
        val surplus: SurplusInput,
        val expenses: List<SpecificExpense>,
        val stopWorkAge: Int,
        val threshold: Double,
        val checkP1: List<Double>
    )

    private fun degradation(floor: Double): List<CurvePoint> = listOf(
        CurvePoint(30.0, 1.0),
        CurvePoint(60.0, 0.8),
        CurvePoint(95.0, floor)
    )

    private fun scenario(
        name: String,
        currentAge: Int,
        stopAge: Int,
        deathAge: Int,
        threshold: Double,
        legacy: Double,
        interest: Double,
        surplus: SurplusInput,
        expenses: List<SpecificExpense> = emptyList(),
        inheritance: Double = 0.0,
        inheritanceAge: Int = currentAge,
        tfr: Double = 0.0,
        utilityOffsetExpense: Boolean = false
    ): Scenario {
        val base = FinancialInput(
            eredita = inheritance,
            etaRicevimentoEredita = inheritanceAge,
            soldiDaConservare = legacy,
            tfrNetto = tfr,
            tassoGuadagnoInteresse = interest,
            tassoInteresseDebito = 0.10,
            sogliaMinimaFunzioneUtilita = threshold,
            capitaleIniziale = 0.0,
            valoreSpesaGiornalieraMaxUtilita = 82.0,
            etaAttuale = currentAge,
            etaPensione = deathAge - 5,
            etaMorte = deathAge,
            p1SavingRatioSurplus = 0.30,
            p2EtaFineRisparmioNoCapitale = stopAge,
            p3PercentualeCapitaleDaSpendereAnnualmente = 0.35,
            p4EtaAnticipataInizioSpesaCapitale = stopAge,
            bonusStdWeight = 0.0
        )
        val withCurves = base.copy(degradationCurvePoints = degradation(0.5))
            .withDefaultAssumptionCurves()
        val expenses2 = if (utilityOffsetExpense && expenses.isNotEmpty()) {
            listOf(expenses.first().copy(utilityOffset = 0.05)) + expenses.drop(1)
        } else expenses
        return Scenario(name, withCurves, surplus, expenses2, stopAge, threshold, listOf(0.0, 0.3, 0.7, 1.0))
    }

    private fun workingSurplus(salary: Double, rent: Double, rentUntil: Int, pension: Double): SurplusInput =
        SurplusInput(
            stipendioMensile = salary,
            premioRisultatoNettoAnnuale = 0.0,
            tredicesimaQuattordicesimaNetto = 0.0,
            pensioneMensileNetta = pension,
            tredicesimaQuattordicesimaNettoPensione = 0.0,
            mutuoAffitto = rent,
            mutuoAffittoFinoEta = rentUntil,
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

    private fun scenarios(): List<Scenario> = listOf(
        scenario(
            "S1 early-stop-no-income", currentAge = 40, stopAge = 40, deathAge = 82,
            threshold = 0.30, legacy = 0.0, interest = 0.02, surplus = workingSurplus(0.0, 0.0, 0, 0.0)
        ),
        scenario(
            "S2 mid-career-rent-pension", currentAge = 42, stopAge = 58, deathAge = 82,
            threshold = 0.25, legacy = 20000.0, interest = 0.03,
            surplus = workingSurplus(2500.0, 600.0, 60, 1400.0),
            expenses = listOf(
                SpecificExpense(46, 8000.0, 0.0),
                SpecificExpense(66, 30000.0, 0.0),
                SpecificExpense(75, 15000.0, 0.0)
            ),
            inheritance = 80000.0, inheritanceAge = 50, tfr = 60000.0, utilityOffsetExpense = true
        ),
        scenario(
            "S3 high-rates-late-death", currentAge = 35, stopAge = 50, deathAge = 90,
            threshold = 0.28, legacy = 30000.0, interest = 0.05,
            surplus = workingSurplus(3000.0, 0.0, 0, 900.0),
            expenses = listOf(SpecificExpense(55, 25000.0, 0.0))
        ),
        scenario(
            "S4 late-start-low-threshold", currentAge = 50, stopAge = 60, deathAge = 85,
            threshold = 0.15, legacy = 0.0, interest = 0.015,
            surplus = workingSurplus(1800.0, 400.0, 65, 0.0)
        )
    )

    private fun feasibleInEngine(
        years: List<SimulationYear>,
        threshold: Double,
        legacy: Double
    ): Boolean {
        if (years.isEmpty()) return false
        if (years.any { it.debtAmount > 1e-6 }) return false
        if (years.any { it.violazioneLascito }) return false
        return years.all { year ->
            year.monthlyUtilitySamples.all { it >= threshold - 1e-6 }
        }
    }

    private fun utilityHistory(years: List<SimulationYear>): List<Double> =
        years.flatMap { it.monthlyUtilitySamples }

    @Test
    fun cross_validation_required_capital_feasible_below_infeasible_extra_identical() {
        scenarios().forEach { sc ->
            val sweep = GoalSolverLogic.solveCapitalVsSavingRatio(
                baseInputs = sc.inputs,
                specificExpenses = sc.expenses,
                surplusData = sc.surplus,
                stopWorkAge = sc.stopWorkAge,
                threshold = sc.threshold
            )
            val feasibleRows = sweep.rows.filter { it.isFeasible }
            assertTrue("${sc.name}: scenario must be feasible for at least one P1", feasibleRows.isNotEmpty())

            val p1sToCheck = feasibleRows.filter { row -> sc.checkP1.any { kotlin.math.abs(it - row.p1) < 1e-9 } }
            assertTrue("${sc.name}: expected check rows present", p1sToCheck.isNotEmpty())

            p1sToCheck.forEach { row ->
                val required = row.requiredCapital!!
                val applied = GoalSolverLogic.buildGoalApplyInputs(sc.inputs, sc.threshold, sc.stopWorkAge, row)
                val years = calculateSimulation(applied, sc.expenses, sc.surplus)
                assertTrue(
                    "${sc.name} P1=${row.p1}: at C*=${"%.0f".format(required)} the official engine must satisfy the goal",
                    feasibleInEngine(years, sc.threshold, sc.inputs.soldiDaConservare)
                )

                if (required > 5000.0) {
                    val below = applied.copy(capitaleIniziale = required - 2 * GoalSolverLogic.DEFAULT_CAPITAL_TOLERANCE)
                    val yearsBelow = calculateSimulation(below, sc.expenses, sc.surplus)
                    assertFalse(
                        "${sc.name} P1=${row.p1}: below C* the official engine must violate the goal",
                        feasibleInEngine(yearsBelow, sc.threshold, sc.inputs.soldiDaConservare)
                    )
                }

                val extra = applied.copy(capitaleIniziale = required + 50000.0)
                val yearsExtra = calculateSimulation(extra, sc.expenses, sc.surplus)
                val h1 = utilityHistory(years)
                val h2 = utilityHistory(yearsExtra)
                assertEquals("${sc.name} P1=${row.p1}: history length", h1.size, h2.size)
                val maxDiff = h1.indices.maxOf { kotlin.math.abs(h1[it] - h2[it]) }
                assertEquals(
                    "${sc.name} P1=${row.p1}: extra capital must NOT change the utility time-history " +
                        "(P3 = 0 floor never spends the surplus; maxDiff=$maxDiff)",
                    0.0, maxDiff, 1e-9
                )
                assertTrue(
                    "${sc.name} P1=${row.p1}: extra capital must raise the final bequest",
                    yearsExtra.last().capitaleFineAnno > years.last().capitaleFineAnno
                )
            }
            println(
                "${sc.name}: " + p1sToCheck.joinToString(" | ") {
                    "P1=${"%.0f".format(it.p1 * 100)}% -> C*=${"%.0f".format(it.requiredCapital!!)} EUR"
                }
            )
        }
    }

    @Test
    fun cross_validation_entire_locus_satisfies_goal_in_engine() {
        val sc = scenarios().first { it.name == "S2 mid-career-rent-pension" }
        val sweep = GoalSolverLogic.solveCapitalVsSavingRatio(
            baseInputs = sc.inputs,
            specificExpenses = sc.expenses,
            surplusData = sc.surplus,
            stopWorkAge = sc.stopWorkAge,
            threshold = sc.threshold
        )
        val feasibleRows = sweep.rows.filter { it.isFeasible }
        assertTrue(feasibleRows.isNotEmpty())
        feasibleRows.forEach { row ->
            val applied = GoalSolverLogic.buildGoalApplyInputs(sc.inputs, sc.threshold, sc.stopWorkAge, row)
            val years = calculateSimulation(applied, sc.expenses, sc.surplus)
            assertTrue(
                "S2 P1=${row.p1} C*=${"%.0f".format(row.requiredCapital)}: entire locus must satisfy the goal",
                feasibleInEngine(years, sc.threshold, sc.inputs.soldiDaConservare)
            )
        }
        println("S2 entire locus cross-validated: ${feasibleRows.size} feasible rows all satisfy the goal in the engine")
    }
}
