// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SimulationYear
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Sanity check against the user's REAL stored data (SharedPreferences extracted
 * from the device into an EXTERNAL folder — nothing personal is committed).
 *
 * Opt-in: runs only when the folder exists (set FA_PREFS_DIR to override the
 * default). Reads FinancialPrefs/SurplusPrefs/SpecificExpensesPrefs XML, parses
 * the Gson payloads and runs the official simulation + the Goal Solver locus,
 * proving that scheduled (formerly "one-time") expenses count in the solver.
 */
class UserRealDataCheckTest {

    private val prefsDir: File =
        File(System.getenv("FA_PREFS_DIR") ?: "C:\\WINDOWS\\TEMP\\fa_prefs")

    private val gson = Gson()

    private fun readPayload(prefsFile: String, key: String): String? {
        val file = File(prefsDir, prefsFile)
        if (!file.isFile) return null
        val xml = file.readText()
        val regex = Regex("<string name=\"$key\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        val raw = regex.find(xml)?.groupValues?.get(1) ?: return null
        return raw
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&#10;", "\n")
            .replace("&#39;", "'")
    }

    private fun loadUserScenario(): Triple<FinancialInput, SurplusInput, List<SpecificExpense>>? {
        val inputsJson = readPayload("FinancialPrefs.xml", "FinancialInputs") ?: return null
        val surplusJson = readPayload("SurplusPrefs.xml", "SurplusInputs") ?: return null
        val expensesJson = readPayload("SpecificExpensesPrefs.xml", "SpecificExpenses")
        val inputs = gson.fromJson(inputsJson, FinancialInput::class.java)
        val surplus = gson.fromJson(surplusJson, SurplusInput::class.java)
        val expenses: List<SpecificExpense> = expensesJson?.let {
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                List::class.java, SpecificExpense::class.java
            ).type
            gson.fromJson<List<SpecificExpense>>(it, type)
        } ?: emptyList()
        return Triple(inputs, surplus, expenses)
    }

    private fun summarize(tag: String, years: List<SimulationYear>, objective: Double, w: Double) {
        val obj = calculateObjectivesFromYears(years, bonusStdWeight = w, legacyTarget = null)
        println("$tag: fObjW=${"%.4f".format(objective)} avgUtil=${"%.4f".format(obj.avgUtilita)} " +
            "std=${"%.4f".format(obj.stdDev)} stability=${"%.4f".format(obj.stabilityIndex)} " +
            "finalCapital=${"%.0f".format(obj.finalCapital)} debtYears=${years.count { it.debtAmount > 1e-6 }} " +
            "legacyViolations=${years.count { it.violazioneLascito }}")
    }

    @Test
    fun user_real_data_current_plan_simulation() = runBlocking {
        assumeTrue("User prefs folder not available", prefsDir.isDirectory)
        val (inputs, surplus, expenses) = loadUserScenario() ?: return@runBlocking

        println("=== USER DATA: age ${inputs.etaAttuale}..${inputs.etaMorte}, pension ${inputs.etaPensione}, " +
            "capital ${"%.0f".format(inputs.capitaleIniziale)}, inheritance ${"%.0f".format(inputs.eredita)}@${inputs.etaRicevimentoEredita}, " +
            "TFR ${"%.0f".format(inputs.tfrNetto)}, P1=${"%.4f".format(inputs.p1SavingRatioSurplus)} P2=${inputs.p2EtaFineRisparmioNoCapitale} " +
            "P3=${"%.4f".format(inputs.p3PercentualeCapitaleDaSpendereAnnualmente)} P4=${inputs.p4EtaAnticipataInizioSpesaCapitale}, " +
            "w=${inputs.bonusStdWeight}, threshold=${inputs.sogliaMinimaFunzioneUtilita}, keep=${"%.0f".format(inputs.soldiDaConservare)}")
        val realExpenses = expenses.filter { expense -> expense.amount > 0 }
        val expenseList = realExpenses.joinToString(separator = ", ") { expense ->
            "age " + expense.age + ": " + "%.0f".format(expense.amount) + " EUR"
        }
        val expenseTotal = "%.0f".format(expenses.sumOf { expense -> expense.amount })
        println("=== SCHEDULED EXPENSES: " + expenseList + " | TOTAL " + expenseTotal + " EUR")

        val (objective, years) = calculateSimulationWithWeight(inputs, expenses, surplus)
        summarize("CURRENT PLAN", years, objective, inputs.bonusStdWeight)
        assertEquals(inputs.etaMorte - inputs.etaAttuale, years.size)
        assertTrue(years.all { it.monthlyUtilitySamples.isNotEmpty() })
    }

    @Test
    fun user_real_data_goal_solver_locus_and_expenses_count() = runBlocking {
        assumeTrue("User prefs folder not available", prefsDir.isDirectory)
        val (inputs, surplus, expenses) = loadUserScenario() ?: return@runBlocking

        val stopAge = 55
        val threshold = 0.22

        val sweep = GoalSolverLogic.solveCapitalVsSavingRatio(
            baseInputs = inputs,
            specificExpenses = expenses,
            surplusData = surplus,
            stopWorkAge = stopAge,
            threshold = threshold
        )
        println("=== GOAL SOLVER LOCUS (stop $stopAge, threshold $threshold, WITH scheduled expenses) ===")
        sweep.rows.forEach { row ->
            println("P1 ${"%.0f".format(row.p1 * 100)}%${if (row.isCurrentPlan) " (current)" else ""} -> " +
                (row.requiredCapital?.let { "%.0f EUR".format(it) } ?: "not reachable"))
        }
        val feasible = sweep.rows.filter { it.isFeasible }
        assertTrue(feasible.isNotEmpty())
        val slack = GoalSolverLogic.DEFAULT_CAPITAL_TOLERANCE + 1.0
        for (i in 1 until feasible.size) {
            assertTrue(
                feasible[i].requiredCapital!! <= feasible[i - 1].requiredCapital!! + slack
            )
        }

        val rowWithExpenses = feasible.last().requiredCapital!!
        val withoutExpenses = GoalSolverLogic.solveCapitalVsSavingRatio(
            baseInputs = inputs,
            specificExpenses = emptyList(),
            surplusData = surplus,
            stopWorkAge = stopAge,
            threshold = threshold
        )
        val rowWithoutExpenses = withoutExpenses.rows.last { it.isFeasible }.requiredCapital!!
        println("=== SCHEDULED EXPENSES COUNT IN THE SOLVER: best row with expenses = ${"%.0f".format(rowWithExpenses)} EUR, " +
            "without = ${"%.0f".format(rowWithoutExpenses)} EUR, delta = ${"%.0f".format(rowWithExpenses - rowWithoutExpenses)} EUR")
        assertTrue(
            "Removing the expense burden must not increase the required capital",
            rowWithoutExpenses <= rowWithExpenses + slack
        )
    }

    @Test
    fun user_real_data_fobj_landscape_diagnostic() = runBlocking {
        assumeTrue("User prefs folder not available", prefsDir.isDirectory)
        val (inputs0, surplus, expenses) = loadUserScenario() ?: return@runBlocking
        val inputs = inputs0.withDefaultAssumptionCurves()
        val w = inputs.bonusStdWeight
        val threshold = inputs.sogliaMinimaFunzioneUtilita
        val curveMax = inputs.utilityCurvePoints
            ?.filter { it.x.isFinite() && it.y.isFinite() }
            ?.maxOfOrNull { it.y } ?: 0.9347
        val cap = computeMaxUtilityMonthlySpend(inputs)

        println("=== FOBJ LANDSCAPE DIAGNOSTIC (P1 x P2, P3=${"%.4f".format(inputs.p3PercentualeCapitaleDaSpendereAnnualmente)}, " +
            "P4=${inputs.p4EtaAnticipataInizioSpesaCapitale}, w=$w, T=$threshold, capSpend=${"%.1f".format(cap)} EUR/month) ===")

        val p2Values = listOf(50, 56, 62, 68, 74, 80)
        val p1Values = listOf(0.0, 0.2, 0.4, 0.6, 0.8, 1.0)
        val fobjs = mutableListOf<Double>()
        for (p2 in p2Values) {
            for (p1 in p1Values) {
                val cell = inputs.copy(
                    p1SavingRatioSurplus = p1,
                    p2EtaFineRisparmioNoCapitale = p2
                )
                val years = calculateSimulation(cell, expenses, surplus)
                val obj = calculateObjectivesFromYears(years, bonusStdWeight = w, legacyTarget = cell.soldiDaConservare)
                val samples = years.flatMap { it.monthlyUtilitySamples }
                val floorFrac = samples.count { abs(it - threshold) <= 1e-4 }.toDouble() / samples.size
                var satCount = 0
                years.forEach { year ->
                    val ceiling = curveMax * funzioneDegradoPerEta(year.eta, inputs)
                    satCount += year.monthlyUtilitySamples.count { it >= ceiling - 1e-4 }
                }
                val satFrac = satCount.toDouble() / samples.size.coerceAtLeast(1)
                fobjs.add(obj.fObjW)
                println(
                    "P1=${"%.1f".format(p1)} P2=$p2 -> fobj=${"%.4f".format(obj.fObjW)} avg=${"%.4f".format(obj.avgUtilita)} " +
                        "std=${"%.4f".format(obj.stdDev)} floor%=${"%.2f".format(floorFrac)} sat%=${"%.2f".format(satFrac)} " +
                        "viol=${years.any { it.violazioneLascito }}"
                )
            }
        }
        val zMin = fobjs.min()
        val zMax = fobjs.max()
        val nearMax = fobjs.count { it >= zMax - 0.02 }
        println("=== SUMMARY: fobj range [$zMin .. $zMax], spread=${"%.4f".format(zMax - zMin)}, " +
            "cells within 0.02 of max: $nearMax/${fobjs.size} ===")

        println("=== ARCANUM CHECK: threshold T sets the floor-bite boundary P1* = 1 - minSpend(T)/surplus ===")
        val surplusMonthly = surplus.calculateSurplusGiornalieroLavorativa(true) * 365.25 / 12.0
        for (tProbe in listOf(0.2, 0.16, 0.12, 0.1)) {
            val probeInputs = inputs.copy(sogliaMinimaFunzioneUtilita = tProbe)
            val feasibleAvg = mutableListOf<Double>()
            var violCells = 0
            for (p2 in p2Values) {
                for (p1 in p1Values) {
                    val cell = probeInputs.copy(
                        p1SavingRatioSurplus = p1,
                        p2EtaFineRisparmioNoCapitale = p2
                    )
                    val years = calculateSimulation(cell, expenses, surplus)
                    val obj = calculateObjectivesFromYears(years, bonusStdWeight = w, legacyTarget = cell.soldiDaConservare)
                    if (years.any { it.violazioneLascito }) {
                        violCells++
                    } else {
                        feasibleAvg.add(obj.fObj0)
                    }
                }
            }
            val fdeg42 = funzioneDegradoPerEta(42.0, inputs).coerceAtLeast(1e-9)
            val requiredRaw = (tProbe / fdeg42).coerceIn(0.0, 1.0)
            val curve = inputs.utilityCurvePoints
                ?.filter { it.x.isFinite() && it.y.isFinite() }?.sortedBy { it.x }
            var minSpend42 = 0.0
            if (curve != null && curve.size >= 2) {
                for (i in 0 until curve.lastIndex) {
                    val a = curve[i]
                    val b = curve[i + 1]
                    if (requiredRaw >= minOf(a.y, b.y) && requiredRaw <= maxOf(a.y, b.y)) {
                        val daily = if (b.y == a.y) a.x else a.x + (requiredRaw - a.y) / (b.y - a.y) * (b.x - a.x)
                        minSpend42 = daily * 365.25 / 12.0
                        break
                    }
                }
            }
            println(
                "T=$tProbe -> minSpend42=${"%.0f".format(minSpend42)} EUR/mo, P1*=${"%.2f".format(1.0 - minSpend42 / surplusMonthly)}, " +
                    "feasible cells ${feasibleAvg.size}/${p2Values.size * p1Values.size}, viol=$violCells, " +
                    "feasible-avg range [${"%.4f".format(feasibleAvg.min())} .. ${"%.4f".format(feasibleAvg.max())}], " +
                    "spread=${"%.4f".format(feasibleAvg.max() - feasibleAvg.min())}"
            )
        }
    }

    @Test
    fun user_real_data_sensibleness_audit() = runBlocking {
        assumeTrue("User prefs folder not available", prefsDir.isDirectory)
        val (inputs0, surplus, expenses) = loadUserScenario() ?: return@runBlocking
        val inputs = inputs0.withDefaultAssumptionCurves()
        val t = inputs.sogliaMinimaFunzioneUtilita
        val daysPerMonth = 365.25 / 12.0

        println("=== AUDIT INPUTS: eta ${inputs.etaAttuale}..${inputs.etaMorte}, pension ${inputs.etaPensione}, " +
            "capital ${"%.0f".format(inputs.capitaleIniziale)}, legacy ${"%.0f".format(inputs.soldiDaConservare)}, " +
            "inheritance ${"%.0f".format(inputs.eredita)}@${inputs.etaRicevimentoEredita}, tfr ${"%.0f".format(inputs.tfrNetto)}@pension, " +
            "T=$t, w=${inputs.bonusStdWeight}, P1=${"%.3f".format(inputs.p1SavingRatioSurplus)} " +
            "P2=${inputs.p2EtaFineRisparmioNoCapitale} P3=${"%.3f".format(inputs.p3PercentualeCapitaleDaSpendereAnnualmente)} " +
            "P4=${inputs.p4EtaAnticipataInizioSpesaCapitale}")
        println("=== AUDIT INPUTS: gain=${inputs.tassoGuadagnoInteresse}, debtRate=${inputs.tassoInteresseDebito}, " +
            "valoreSpesaGiornalieraMaxUtilita=${inputs.valoreSpesaGiornalieraMaxUtilita}")
        expenses.filter { it.amount > 0 }.sortedBy { it.age }.forEach {
            println("   expense age ${it.age}: ${"%.0f".format(it.amount)} EUR (utilityOffset=${"%.2f".format(it.utilityOffset)})")
        }

        println("=== AUDIT SURPLUS (EUR/month, daily x 30.4375) ===")
        println("lavorativa: conMutuo=${"%.0f".format(surplus.calculateSurplusGiornalieroLavorativa(true) * daysPerMonth)} " +
            "senzaMutuo=${"%.0f".format(surplus.calculateSurplusGiornalieroLavorativa(false) * daysPerMonth)}")
        println("pensione:   conMutuo=${"%.0f".format(surplus.calculateSurplusGiornalieroPensione(true) * daysPerMonth)} " +
            "senzaMutuo=${"%.0f".format(surplus.calculateSurplusGiornalieroPensione(false) * daysPerMonth)}")

        println("=== AUDIT UTILITY CURVE (u vs monthly spend, age 42 vs 70) ===")
        for (monthly in listOf(0.0, 300.0, 500.0, 700.0, 900.0, 1100.0, 1300.0, 1600.0, 2000.0,
                computeMaxUtilityMonthlySpend(inputs), 3000.0, 5000.0)) {
            println("spend ${"%.0f".format(monthly)}/mo -> u42=${"%.3f".format(utilitaDaSpesa(42.0, monthly, inputs))} " +
                "u70=${"%.3f".format(utilitaDaSpesa(70.0, monthly, inputs))}")
        }
        println("curveMax=${inputs.utilityCurvePoints?.filter { it.x.isFinite() && it.y.isFinite() }?.maxOfOrNull { it.y }}, " +
            "capSpend=${"%.1f".format(computeMaxUtilityMonthlySpend(inputs))} EUR/mo")

        fun minimumSpendAt(age: Double): Double {
            val fdeg = funzioneDegradoPerEta(age, inputs).coerceAtLeast(1e-9)
            val requiredRaw = (t / fdeg).coerceIn(0.0, 1.0)
            val curve = inputs.utilityCurvePoints
                ?.filter { it.x.isFinite() && it.y.isFinite() }?.sortedBy { it.x } ?: return 0.0
            if (curve.isEmpty() || requiredRaw <= curve.first().y) return 0.0
            for (i in 0 until curve.lastIndex) {
                val a = curve[i]
                val b = curve[i + 1]
                if (requiredRaw >= minOf(a.y, b.y) && requiredRaw <= maxOf(a.y, b.y)) {
                    val daily = if (b.y == a.y) a.x else a.x + (requiredRaw - a.y) / (b.y - a.y) * (b.x - a.x)
                    return daily * daysPerMonth
                }
            }
            return curve.last().x * daysPerMonth
        }
        println("=== AUDIT MINIMUM SPEND for T=$t (offset=0) ===")
        for (age in listOf(42.0, 50.0, 60.0, 65.0, 70.0, 80.0)) {
            println("age ${age.toInt()}: minimum ${"%.0f".format(minimumSpendAt(age))} EUR/mo (fdeg=${"%.3f".format(funzioneDegradoPerEta(age, inputs))})")
        }

        val (objective, years) = calculateSimulationWithWeight(inputs, expenses, surplus)
        println("=== AUDIT CURRENT PLAN (objective=${"%.4f".format(objective)}) ===")
        years.forEach { y ->
            println("eta ${y.eta}: spend=${"%.0f".format(y.spesaMensileCorrettaFinale)} u=${"%.3f".format(y.funzioneUtilita)} " +
                "atFloor=${y.utilityAtThreshold} capEnd=${"%.0f".format(y.capitaleFineAnno)} debt=${"%.0f".format(y.debtAmount)}")
        }

        println("=== AUDIT FINE P1 SWEEP (P2=${inputs.p2EtaFineRisparmioNoCapitale}, P3/P4 current, w=0) ===")
        var p1 = 0.0
        while (p1 <= 0.401) {
            val cell = inputs.copy(p1SavingRatioSurplus = p1)
            val ys = calculateSimulation(cell, expenses, surplus)
            val o = calculateObjectivesFromYears(ys, bonusStdWeight = 0.0, legacyTarget = cell.soldiDaConservare)
            println("P1=${"%.3f".format(p1)} fobj0=${"%.4f".format(o.fObj0)} avg=${"%.4f".format(o.avgUtilita)} " +
                "finalNW=${"%.0f".format(o.finalCapital)} viol=${ys.any { it.violazioneLascito }}")
            p1 += 0.025
        }
    }
}
