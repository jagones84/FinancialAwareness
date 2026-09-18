// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.CurvePoint
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SimulationYear
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.Random
import kotlin.math.abs
import kotlin.math.pow

/**
 * RANDOM cross-validation (user request: "usa un input set casuale, lancia il goal
 * solver, poi con gli stessi input calcola la time-history della utility e verifica
 * che con il capitale iniziale del solver applicato alla simulazione vai a sbattere
 * proprio sulla soglia del solver").
 *
 * For each seeded-random scenario the full GUI pipeline is replayed:
 *  1. [GoalSolverLogic.solveCapitalVsSavingRatio] (the whole locus, as the dialog does);
 *  2. the row of a random P1 is APPLIED via [GoalSolverLogic.buildGoalApplyInputs]
 *     (same inputs, capital = C*);
 *  3. the OFFICIAL engine recomputes the utility time-history and, whenever C* > 0,
 *     the plan must hit a binding constraint (with C* = 0 the goal is covered by
 *     income alone and nothing has to graze):
 *       UTILITY graze - the floor funds exactly the threshold, so min(monthly
 *         samples) == T within 1e-4 (extra capital only inflates the bequest);
 *       LEGACY graze - violazioneLascito is death-only (SimulationLogic checks the
 *         plan's last month) while during the plan the legacy is guarded by the
 *         reserve-gated p3 draw (only the excess over the discounted legacy plus
 *         the PV of future expenses is spendable), so the FINAL net worth must
 *         graze the legacy;
 *       DEBT graze - the engine never lets utility fall below T (finalSpend =
 *         max(baseSpend, minimumSpend), the shortfall becomes DEBT), so with a
 *         zero/loose legacy the binding resource is solvency: the minimum YEAR-END
 *         net worth must graze the debt boundary within the bisection slack
 *         compounded at the plan's interest rate;
 *     - below C* (C* - 2x capital tolerance) the engine must VIOLATE the goal, proving
 *       C* is really the minimum and the graze is not an accident of slack capital.
 *
 * The random generator is seeded ([SEED]) so any failure is exactly reproducible;
 * the scenario dump is printed for every draw.
 */
class GoalSolverRandomCrossValidationTest {

    companion object {
        private const val SEED = 20260901L
        private const val SCENARIOS = 12
        private const val MIN_CHECKED_SCENARIOS = 5
        private const val GRAZE_EPSILON = 1e-4
    }

    data class RandomScenario(
        val label: String,
        val inputs: FinancialInput,
        val surplus: SurplusInput,
        val expenses: List<SpecificExpense>,
        val stopWorkAge: Int,
        val threshold: Double,
        val p1: Double,
        val legacy: Double
    )

    private fun degradationCurve(floor: Double): List<CurvePoint> = listOf(
        CurvePoint(30.0, 1.0),
        CurvePoint(60.0, 0.8),
        CurvePoint(95.0, floor)
    )

    private fun randomScenario(rnd: Random, index: Int): RandomScenario {
        val currentAge = 28 + rnd.nextInt(25)
        val deathAge = (currentAge + 33 + rnd.nextInt(20)).coerceAtMost(95)
        val stopWorkAge = currentAge + rnd.nextInt(deathAge - 15 - currentAge + 1)
        val threshold = 0.15 + rnd.nextDouble() * 0.15
        val interest = rnd.nextDouble() * 0.05
        val legacy = if (rnd.nextDouble() < 0.5) 10_000.0 + rnd.nextInt(51) * 1_000.0 else 0.0
        val salary = 900.0 + rnd.nextInt(2_300)
        val pension = 500.0 + rnd.nextInt(1_300)
        val rent = if (rnd.nextDouble() < 0.7) 200.0 + rnd.nextInt(700) else 0.0
        val rentUntil = (stopWorkAge + rnd.nextInt(10)).coerceAtMost(deathAge - 1)
        val p1 = rnd.nextInt(11) / 10.0
        val floor = 0.5 + rnd.nextDouble() * 0.2

        val expenseCount = rnd.nextInt(4)
        val expenses = (0 until expenseCount).map {
            val age = currentAge + 2 + rnd.nextInt(deathAge - currentAge - 4)
            val amount = (2 + rnd.nextInt(38)) * 1_000.0
            val offset = if (rnd.nextDouble() < 0.3) 0.02 + rnd.nextDouble() * 0.06 else 0.0
            SpecificExpense(age, amount, offset)
        }.sortedBy { it.age }

        val inheritance = if (rnd.nextDouble() < 0.5) 30_000.0 + rnd.nextInt(120) * 1_000.0 else 0.0
        val inheritanceAge = currentAge + 2 + rnd.nextInt((deathAge - currentAge - 3).coerceAtLeast(1))
        val tfr = if (rnd.nextDouble() < 0.5) 20_000.0 + rnd.nextInt(60) * 1_000.0 else 0.0

        val inputs = FinancialInput(
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
            p2EtaFineRisparmioNoCapitale = stopWorkAge,
            p3PercentualeCapitaleDaSpendereAnnualmente = 0.35,
            p4EtaAnticipataInizioSpesaCapitale = stopWorkAge,
            bonusStdWeight = 0.0
        ).copy(degradationCurvePoints = degradationCurve(floor)).withDefaultAssumptionCurves()

        val surplus = SurplusInput(
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

        val label = "R$index(age $currentAge/$stopWorkAge/$deathAge T=" +
            "%.2f".format(Locale.US, threshold) + " i=" +
            "%.3f".format(Locale.US, interest) + " sal=" +
            "%.0f".format(Locale.US, salary) + " pen=" +
            "%.0f".format(Locale.US, pension) + " rent=" +
            "%.0f".format(Locale.US, rent) + " leg=" +
            "%.0f".format(Locale.US, legacy) + " er=" +
            "%.0f".format(Locale.US, inheritance) + "@$inheritanceAge tfr=" +
            "%.0f".format(Locale.US, tfr) + " exp=" + expenses.size + " floor=" +
            "%.2f".format(Locale.US, floor)

        return RandomScenario(label, inputs, surplus, expenses, stopWorkAge, threshold, p1, legacy)
    }

    private fun feasibleInEngine(
        years: List<SimulationYear>,
        threshold: Double,
        legacy: Double
    ): Boolean {
        if (years.isEmpty()) return false
        if (years.any { it.debtAmount > 1e-6 }) return false
        if (years.any { it.violazioneLascito }) return false
        return years.all { year ->
            year.monthlyUtilitySamples.isNotEmpty() &&
                year.monthlyUtilitySamples.all { it >= threshold - 1e-6 }
        }
    }

    @Test
    fun random_inputs_solver_capital_hits_the_threshold_exactly_in_the_engine() {
        val rnd = Random(SEED)
        var checked = 0

        repeat(SCENARIOS) { index ->
            val sc = randomScenario(rnd, index)
            val sweep = GoalSolverLogic.solveCapitalVsSavingRatio(
                baseInputs = sc.inputs,
                specificExpenses = sc.expenses,
                surplusData = sc.surplus,
                stopWorkAge = sc.stopWorkAge,
                threshold = sc.threshold
            )
            val row = sweep.rows.firstOrNull { abs(it.p1 - sc.p1) < 1e-9 }
            if (row == null || !row.isFeasible || row.requiredCapital == null) {
                println("${sc.label} P1=${sc.p1}: row infeasible, skipped")
                return@repeat
            }
            checked++
            val required = row.requiredCapital!!

            val applied = GoalSolverLogic.buildGoalApplyInputs(sc.inputs, sc.threshold, sc.stopWorkAge, row)
            val years = calculateSimulation(applied, sc.expenses, sc.surplus)

            assertTrue(
                "${sc.label} P1=${sc.p1}: at C*=${"%.0f".format(Locale.US, required)} " +
                    "the official engine must satisfy the goal",
                feasibleInEngine(years, sc.threshold, sc.legacy)
            )

            val minSample = years.minOf { it.monthlyUtilitySamples.minOrNull()!! }
            if (required > 0.0) {
                val finalNetWorth = years.last().capitaleFineAnno - years.last().debtAmount
                val minYearEndNetWorth = years.minOf { it.capitaleFineAnno - it.debtAmount }
                val planYears = sc.inputs.etaMorte - sc.inputs.etaAttuale
                val compoundSlack = GoalSolverLogic.DEFAULT_CAPITAL_TOLERANCE *
                    (1.0 + sc.inputs.tassoGuadagnoInteresse).pow(planYears) + 1.0
                val grazesUtility = minSample <= sc.threshold + GRAZE_EPSILON
                val grazesLegacy = sc.legacy > 0.0 && finalNetWorth <= sc.legacy + compoundSlack
                val grazesDebt = minYearEndNetWorth <= compoundSlack
                assertTrue(
                    "${sc.label} P1=${sc.p1} C*=${"%.0f".format(Locale.US, required)}: the applied " +
                        "plan must hit a binding constraint (utility graze: min=$minSample vs " +
                        "T=${sc.threshold}; legacy graze: final net worth=$finalNetWorth vs " +
                        "legacy=${sc.legacy}; debt graze: min year-end net worth=" +
                        "$minYearEndNetWorth; compound slack=" +
                        "${"%.0f".format(Locale.US, compoundSlack)})",
                    grazesUtility || grazesLegacy || grazesDebt
                )

                if (required > 2 * GoalSolverLogic.DEFAULT_CAPITAL_TOLERANCE) {
                    val below = applied.copy(
                        capitaleIniziale = required - 2 * GoalSolverLogic.DEFAULT_CAPITAL_TOLERANCE
                    )
                    val yearsBelow = calculateSimulation(below, sc.expenses, sc.surplus)
                    assertFalse(
                        "${sc.label} P1=${sc.p1}: below C* the official engine must violate the goal",
                        feasibleInEngine(yearsBelow, sc.threshold, sc.legacy)
                    )
                }

                val binding = when {
                    grazesUtility ->
                        "UTILITY graze, min-T=" + "%.2e".format(Locale.US, minSample - sc.threshold)
                    grazesLegacy ->
                        "LEGACY graze, final-legacy=" +
                            "%.0f".format(Locale.US, finalNetWorth - sc.legacy)
                    else ->
                        "DEBT graze, min year-end net worth=" +
                            "%.0f".format(Locale.US, minYearEndNetWorth)
                }
                println(
                    "${sc.label} P1=${"%.0f".format(Locale.US, sc.p1 * 100)}% " +
                        "C*=${"%.0f".format(Locale.US, required)} EUR -> $binding"
                )
            } else {
                println(
                    "${sc.label} P1=${"%.0f".format(Locale.US, sc.p1 * 100)}% " +
                        "C*=0 EUR -> trivially feasible, no binding constraint (income covers the goal)"
                )
            }
        }

        assertTrue(
            "At least $MIN_CHECKED_SCENARIOS of $SCENARIOS random scenarios must yield a " +
                "feasible checked row (got $checked)",
            checked >= MIN_CHECKED_SCENARIOS
        )
    }
}
