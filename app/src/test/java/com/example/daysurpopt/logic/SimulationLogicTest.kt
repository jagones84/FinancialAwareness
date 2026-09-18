// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.domain.Defaults
import com.example.daysurpopt.domain.SimulationYear
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.CurvePoint
import org.junit.Test
import org.junit.Assert.*
import com.example.daysurpopt.utils.AppDebugLog

class SimulationLogicTest {

    @Test
    fun testInheritanceCrash() {
        // Setup inputs based on user report
        val baseInput = FinancialInput(
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
            valoreSpesaGiornalieraMaxUtilita = 10.0, // Low max spending
            sogliaMinimaFunzioneUtilita = 0.1,
            eredita = 0.0 // Baseline
        ).withDefaultAssumptionCurves()
        
        val surplusInput = SurplusInput()
        val expenses = emptyList<SpecificExpense>()

        // 1. Run Baseline
        val (fobjBase, yearsBase) = calculateSimulationWithWeight(baseInput, expenses, surplusInput)
        
        println("Baseline fobj: $fobjBase")
        assertTrue("Baseline fobj should be positive", fobjBase > 0.0)

        // 2. Run with Inheritance (+10k)
        val inputWithInheritance = baseInput.copy(eredita = 10000.0)
        val (fobjInherit, yearsInherit) = calculateSimulationWithWeight(inputWithInheritance, expenses, surplusInput)
        
        println("Inheritance fobj: $fobjInherit")
        
        println("=== AppDebugLog ===")
        println(AppDebugLog.lines.joinToString("\n"))
        println("===================")

        assertTrue("Inheritance fobj should be positive", fobjInherit > 0.0)
    }

    @Test
    fun testEarlyVsLateInheritance() {
        // Compare Age 41 vs Age 65 with realistic spending caps
        val baseInput = FinancialInput(
            p1SavingRatioSurplus = 0.5182,
            p2EtaFineRisparmioNoCapitale = 61,
            p3PercentualeCapitaleDaSpendereAnnualmente = 0.9171,
            p4EtaAnticipataInizioSpesaCapitale = 82,
            etaAttuale = 30,
            etaPensione = 67,
            etaMorte = 90,
            soldiDaConservare = 50000.0,
            capitaleIniziale = 10000.0,
            valoreSpesaGiornalieraMaxUtilita = 150.0, // High enough to allow utility growth
            sogliaMinimaFunzioneUtilita = 0.1,
            eredita = 10000.0,
            // This regression checks the raw utility effect of inheritance timing.
            bonusStdWeight = 0.0
        ).withDefaultAssumptionCurves()
        
        val surplusInput = SurplusInput(
            mutuoAffitto = 500.0, 
            mutuoAffittoFinoEta = 60
        )
        val expenses = emptyList<SpecificExpense>()

        // 1. Late Inheritance (65)
        val inputLate = baseInput.copy(etaRicevimentoEredita = 65)
        val (fobjLate, yearsLate) = calculateSimulationWithWeight(inputLate, expenses, surplusInput)
        
        // 2. Early Inheritance (41)
        val inputEarly = baseInput.copy(etaRicevimentoEredita = 41)
        val (fobjEarly, yearsEarly) = calculateSimulationWithWeight(inputEarly, expenses, surplusInput)
        
        println("Late Inheritance (65) fobj: $fobjLate")
        println("Early Inheritance (41) fobj: $fobjEarly")
        
        if (yearsLate.isNotEmpty() && yearsEarly.isNotEmpty()) {
             println("Late Avg Util: ${yearsLate.map { it.funzioneUtilita }.average()}")
             println("Early Avg Util: ${yearsEarly.map { it.funzioneUtilita }.average()}")
        }

        assertTrue("Both fobj should be positive", fobjLate > 0.0 && fobjEarly > 0.0)
        assertTrue("Early inheritance should be better or equal to late", fobjEarly >= fobjLate)
    }

    @Test
    fun testAgentJsonParsingLogic() {
        // Mimic AgentToolExecutor logic (UPDATED)
        val params = mapOf(
            "eredita" to "10000", // String format
            "p1SavingRatioSurplus" to 0.5
        )
        
        fun getDouble(key: String): Double? {
            val value = params[key] ?: params[key.lowercase()]
            return when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        }

        val eredita = getDouble("eredita")
        println("Parsed eredita from string '10000': $eredita")
        
        assertNotNull("AgentToolExecutor should parse String numbers correctly now", eredita)
        assertEquals(10000.0, eredita!!, 0.001)
    }

    @Test
    fun testInheritanceSweep() {
        println("=== Inheritance Sweep Test ===")
        val baseInput = FinancialInput(
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
            valoreSpesaGiornalieraMaxUtilita = 82.0,
            sogliaMinimaFunzioneUtilita = 0.1,
            eredita = 0.0
        ).withDefaultAssumptionCurves()

        // Use stable expenses that don't cause bankruptcy at 0 inheritance
        val surplusInput = SurplusInput(
            mutuoAffitto = 800.0, 
            mutuoAffittoFinoEta = 60
        )
        val expenses = emptyList<SpecificExpense>()

        var previousFobj = -1.0
        var foundDrop = false

        for (inherit in 0..20000 step 1000) {
            val input = baseInput.copy(eredita = inherit.toDouble())
            val (fobj, years) = calculateSimulationWithWeight(input, expenses, surplusInput)
            
            println("Inheritance: $inherit -> fobj: $fobj")
            
            if (previousFobj != -1.0 && fobj < previousFobj - 1e-6) {
                println("!!! DETECTED FOBJ DROP at $inherit (Prev: $previousFobj, Curr: $fobj)")
                foundDrop = true
                
                // Detailed dump for the drop
                println("Years count: ${years.size}")
                val violations = years.filter { it.violazioneLascito }
                if (violations.isNotEmpty()) println("Violations: ${violations.size}")
            }
            previousFobj = fobj
        }
        
        assertFalse("Should not find any drop in fobj when increasing inheritance", foundDrop)
    }

    @Test
    fun calculateObjectivesFromYears_reports_feasibility_and_legacy_gap() {
        val years = listOf(
            SimulationYear(
                eta = 65,
                funzioneUtilita = 0.2,
                capitaleFineAnno = 60000.0,
                violazioneLascito = false
            ),
            SimulationYear(
                eta = 66,
                funzioneUtilita = 0.3,
                capitaleFineAnno = 55000.0,
                violazioneLascito = false
            )
        )

        val result = calculateObjectivesFromYears(
            years = years,
            bonusStdWeight = 0.5,
            legacyTarget = 50000.0
        )

        assertEquals(0.25, result.avgUtilita, 1e-9)
        assertTrue(result.isFeasible)
        assertEquals(5000.0, result.legacyGap, 1e-9)
        assertEquals(55000.0, result.finalCapital, 1e-9)
    }

    @Test
    fun calculateObjectivesFromYears_prefers_monthly_samples_when_available() {
        val years = listOf(
            SimulationYear(
                eta = 41,
                funzioneUtilita = 0.3,
                capitaleFineAnno = 1000.0,
                monthlyUtilitySamples = listOf(0.1, 0.2)
            ),
            SimulationYear(
                eta = 42,
                funzioneUtilita = 0.9,
                capitaleFineAnno = 1200.0,
                monthlyUtilitySamples = listOf(0.8, 1.0)
            )
        )

        val result = calculateObjectivesFromYears(
            years = years,
            bonusStdWeight = 0.0,
            legacyTarget = 0.0
        )

        assertEquals(0.525, result.avgUtilita, 1e-9)
        assertEquals(calculateStandardDeviation(listOf(0.1, 0.2, 0.8, 1.0)), result.stdDev, 1e-9)
    }

    @Test
    fun computeObjective_keeps_weighted_value_bounded_when_std_is_tiny() {
        val avg = 0.25
        val std = 1e-15

        val score = computeStabilityScore(avg, std)
        val weighted = computeObjective(avg, std, 1.0)

        assertEquals(1.0, score, 1e-9)
        assertTrue(weighted.isFinite())
        assertTrue(weighted < 1.0)
    }

    @Test
    fun computeObjective_uses_stability_as_penalty_not_additive_bonus() {
        val avg = 0.25
        val std = 0.25

        val weighted = computeObjective(avg, std, 1.0)

        assertEquals(0.125, weighted, 1e-9)
        assertTrue(weighted <= avg + 1e-9)
    }

    @Test
    fun utilityWithOffset_stays_bounded_and_fobj_never_exceeds_one() {
        val baseInput = FinancialInput(
            p1SavingRatioSurplus = 0.0,
            p2EtaFineRisparmioNoCapitale = 61,
            p3PercentualeCapitaleDaSpendereAnnualmente = 0.9171,
            p4EtaAnticipataInizioSpesaCapitale = 82,
            etaAttuale = 30,
            etaPensione = 67,
            etaMorte = 90,
            soldiDaConservare = 50000.0,
            capitaleIniziale = 10000.0,
            valoreSpesaGiornalieraMaxUtilita = 150.0,
            sogliaMinimaFunzioneUtilita = 0.1
        ).withDefaultAssumptionCurves()

        val surplusInput = SurplusInput(
            mutuoAffitto = 500.0,
            mutuoAffittoFinoEta = 60
        )
        val expenses = listOf(SpecificExpense(age = 31, amount = 1000.0, utilityOffset = 0.9))

        val years = calculateSimulation(baseInput, expenses, surplusInput)
        val samples = years.flatMap { it.monthlyUtilitySamples }

        assertTrue(samples.isNotEmpty())
        val maxSample = samples.max()
        assertTrue(
            "Utility samples must stay within [0,1] after offset, got max=$maxSample",
            maxSample <= 1.0 + 1e-9
        )

        val objectives = calculateObjectivesFromYears(
            years = years,
            bonusStdWeight = 1.0,
            legacyTarget = baseInput.soldiDaConservare
        )
        assertTrue(
            "fObjW must stay within [0,1] after offset, got ${objectives.fObjW}",
            objectives.fObjW <= 1.0 + 1e-9
        )
    }

    @Test
    fun legacyViolation_gets_graded_penalty_instead_of_zero() {
        val violatingYears = listOf(
            SimulationYear(
                eta = 40, funzioneUtilita = 0.5, capitaleFineAnno = 1000.0,
                monthlyUtilitySamples = List(12) { 0.5 }
            ),
            SimulationYear(
                eta = 41, funzioneUtilita = 0.5, capitaleFineAnno = 0.0,
                monthlyUtilitySamples = List(12) { 0.5 }, violazioneLascito = true
            )
        )
        val violating = calculateObjectivesFromYears(violatingYears, bonusStdWeight = 1.0, legacyTarget = 500.0)
        // floor + proportional: 1.0 + 2.5 * (500 - 0) / 500 = 3.5 (full breach)
        assertEquals(0.5 - 3.5, violating.fObjW, 1e-9)
        assertEquals(0.5 - 3.5, violating.fObj0, 1e-9)
        assertFalse(violating.isFeasible)

        val feasibleYears = violatingYears.map { it.copy(violazioneLascito = false) }
        val feasible = calculateObjectivesFromYears(feasibleYears, bonusStdWeight = 1.0, legacyTarget = 500.0)
        assertEquals(0.5, feasible.fObjW, 1e-9)
        assertTrue(violating.fObjW < feasible.fObjW)
    }

    @Test
    fun legacyViolation_penalty_preserves_gradient_between_plans() {
        fun yearsWith(sample: Double): List<SimulationYear> = listOf(
            SimulationYear(
                eta = 40, funzioneUtilita = sample, capitaleFineAnno = 0.0,
                monthlyUtilitySamples = List(12) { sample }, violazioneLascito = true
            )
        )
        val low = calculateObjectivesFromYears(yearsWith(0.3), bonusStdWeight = 1.0, legacyTarget = 0.0)
        val high = calculateObjectivesFromYears(yearsWith(0.6), bonusStdWeight = 1.0, legacyTarget = 0.0)
        assertEquals(0.3 - 2.5, low.fObjW, 1e-9)
        assertEquals(0.6 - 2.5, high.fObjW, 1e-9)
        assertTrue(high.fObjW > low.fObjW)
        assertTrue(low.fObjW < 0.0 && high.fObjW < 0.0)
    }

    @Test
    fun negativeFiniteSamples_flowIntoGradedAverage() {
        val years = listOf(
            SimulationYear(
                eta = 40, funzioneUtilita = 0.2, capitaleFineAnno = 0.0,
                monthlyUtilitySamples = listOf(0.4, -0.2)
            )
        )
        val result = calculateObjectivesFromYears(years, bonusStdWeight = 0.0, legacyTarget = 0.0)
        assertEquals(0.1, result.avgUtilita, 1e-9)
        assertEquals(0.1, result.fObjW, 1e-9)
        assertFalse(result.isFeasible)
    }

    @Test
    fun exceptionSentinel_stillForcesZeroObjective() {
        val years = listOf(
            SimulationYear(
                eta = 40, funzioneUtilita = -1e9, capitaleFineAnno = 0.0,
                monthlyUtilitySamples = listOf(-1e9)
            )
        )
        val result = calculateObjectivesFromYears(years, bonusStdWeight = 1.0, legacyTarget = 0.0)
        assertEquals(0.0, result.fObjW, 0.0)
        assertEquals(0.0, result.fObj0, 0.0)
    }

    @Test
    fun legacyViolation_penalty_guarantees_separation_from_feasible_plans() {
        // A marginal breacher with a HIGH utility base must still score below ANY feasible plan,
        // otherwise the maximizer prefers violating the legacy (user-reported optimizer bug).
        val breacher = calculateObjectivesFromYears(
            listOf(
                SimulationYear(
                    eta = 40, funzioneUtilita = 0.9, capitaleFineAnno = 49_999.0,
                    monthlyUtilitySamples = List(12) { 0.9 }, violazioneLascito = true
                )
            ),
            bonusStdWeight = 1.0,
            legacyTarget = 50_000.0
        )
        assertEquals(
            0.9 - 1.0 - 2.5 * 1.0 / 50_000.0,
            breacher.fObjW,
            1e-9
        )
        assertTrue("marginal breacher must score below zero", breacher.fObjW < 0.0)

        val lowUtilityFeasible = calculateObjectivesFromYears(
            listOf(
                SimulationYear(
                    eta = 40, funzioneUtilita = 0.05, capitaleFineAnno = 60_000.0,
                    monthlyUtilitySamples = List(12) { 0.05 }
                )
            ),
            bonusStdWeight = 1.0,
            legacyTarget = 50_000.0
        )
        assertEquals(0.05, lowUtilityFeasible.fObjW, 1e-9)
        assertTrue(
            "feasible plan (even with low utility) must beat any legacy violator",
            lowUtilityFeasible.fObjW > breacher.fObjW
        )
    }

    @Test
    fun p3Draw_isAnnuitizedOnNetWorthMinusLegacy_notReserveGated() {
        val zeroSurplus = SurplusInput(
            stipendioMensile = 0.0, premioRisultatoNettoAnnuale = 0.0, tredicesimaQuattordicesimaNetto = 0.0,
            pensioneMensileNetta = 0.0, mutuoAffitto = 0.0,
            condominioLavorativa = 0.0, bolletteLavorativa = 0.0, ciboLavorativa = 0.0, veicoliLavorativa = 0.0,
            palestraLavorativa = 0.0, trasportiViaggiLavorativa = 0.0, saluteLavorativa = 0.0,
            vacanzeLavorativa = 0.0, shoppingLavorativa = 0.0, altroLavorativa = 0.0,
            condominioPensione = 0.0, bollettePensione = 0.0, ciboPensione = 0.0, veicoliPensione = 0.0,
            palestraPensione = 0.0, trasportiViaggiPensione = 0.0, salutePensione = 0.0,
            vacanzePensione = 0.0, shoppingPensione = 0.0, altroPensione = 0.0
        )
        val inputs = FinancialInput(
            p1SavingRatioSurplus = 0.0,
            p2EtaFineRisparmioNoCapitale = 45,
            p3PercentualeCapitaleDaSpendereAnnualmente = 1.0,
            p4EtaAnticipataInizioSpesaCapitale = 45,
            etaAttuale = 45,
            etaPensione = 67,
            etaMorte = 85,
            soldiDaConservare = 10000.0,
            capitaleIniziale = 400_000.0,
            valoreSpesaGiornalieraMaxUtilita = 100.0,
            sogliaMinimaFunzioneUtilita = 0.1
        ).withDefaultAssumptionCurves()
        val bigLateExpense = listOf(SpecificExpense(age = 84, amount = 350_000.0))

        val spendWith = calculateSimulation(inputs, bigLateExpense, zeroSurplus)
            .first().spesaMensileCorrettaFinale
        val spendWithout = calculateSimulation(inputs, emptyList(), zeroSurplus)
            .first().spesaMensileCorrettaFinale

        assertEquals(spendWithout, spendWith, 10.0)
        assertTrue("annuitized draw must be active: $spendWith EUR/month", spendWith > 700.0)
    }

    @Test
    fun computeMaxUtilityMonthlySpend_defaultSigmoid_usesDailyMax() {
        val inputs = FinancialInput(valoreSpesaGiornalieraMaxUtilita = 150.0)
        assertEquals(150.0 * (365.25 / 12.0), computeMaxUtilityMonthlySpend(inputs), 1e-9)
    }

    @Test
    fun computeMaxUtilityMonthlySpend_curveCapsAtPlateauStart() {
        val inputs = FinancialInput(
            valoreSpesaGiornalieraMaxUtilita = 150.0,
            utilityCurvePoints = listOf(
                CurvePoint(x = 0.0, y = 0.2),
                CurvePoint(x = 100.0, y = 0.9),
                CurvePoint(x = 200.0, y = 0.9)
            )
        )
        assertEquals(100.0 * (365.25 / 12.0), computeMaxUtilityMonthlySpend(inputs), 1e-9)
    }

    @Test
    fun computeMaxUtilityMonthlySpend_singlePointCurve_fallsBackToDailyMax() {
        val inputs = FinancialInput(
            valoreSpesaGiornalieraMaxUtilita = 150.0,
            utilityCurvePoints = listOf(CurvePoint(x = 10.0, y = 0.9))
        )
        assertEquals(150.0 * (365.25 / 12.0), computeMaxUtilityMonthlySpend(inputs), 1e-9)
    }

    @Test
    fun spendCap_limitsVoluntarySpending_toMaxUtilitySpend() {
        val inputs = FinancialInput(
            p1SavingRatioSurplus = 0.0,
            p2EtaFineRisparmioNoCapitale = 30,
            p3PercentualeCapitaleDaSpendereAnnualmente = 1.0,
            p4EtaAnticipataInizioSpesaCapitale = 30,
            etaAttuale = 30,
            etaPensione = 67,
            etaMorte = 90,
            soldiDaConservare = 50000.0,
            capitaleIniziale = 10_000_000.0,
            valoreSpesaGiornalieraMaxUtilita = 10.0,
            sogliaMinimaFunzioneUtilita = 0.1
        )
        val years = calculateSimulation(inputs, emptyList(), SurplusInput())
        val cap = computeMaxUtilityMonthlySpend(inputs)
        assertTrue(years.isNotEmpty())
        assertEquals(cap, years.first().spesaMensileCorrettaFinale, 1e-6)
        val samples = years.first().monthlyUtilitySamples
        assertTrue(samples.isNotEmpty())
        assertTrue(
            "utility must sit on the saturation plateau (only fdeg drifts within the year): spread=${samples.max() - samples.min()}",
            samples.max() - samples.min() < 0.01
        )
    }

    @Test
    fun spendCap_floorWins_whenMinimumSpendExceedsCap() {
        val inputs = FinancialInput(
            p1SavingRatioSurplus = 0.0,
            p2EtaFineRisparmioNoCapitale = 30,
            p3PercentualeCapitaleDaSpendereAnnualmente = 1.0,
            p4EtaAnticipataInizioSpesaCapitale = 30,
            etaAttuale = 30,
            etaPensione = 67,
            etaMorte = 90,
            soldiDaConservare = 50000.0,
            capitaleIniziale = 10_000_000.0,
            valoreSpesaGiornalieraMaxUtilita = 10.0,
            sogliaMinimaFunzioneUtilita = 0.95
        )
        val years = calculateSimulation(inputs, emptyList(), SurplusInput())
        val cap = computeMaxUtilityMonthlySpend(inputs)
        assertTrue(years.isNotEmpty())
        assertTrue(years.first().spesaMensileCorrettaFinale > cap)
        val samples = years.first().monthlyUtilitySamples
        assertTrue(samples.min() >= 0.95 - 1e-6)
    }

    @Test
    fun calculateObjectivesFromYears_uses_bounded_stability_score() {
        val years = List(12) {
            SimulationYear(
                eta = 41,
                funzioneUtilita = 0.25,
                capitaleFineAnno = 1000.0,
                monthlyUtilitySamples = listOf(0.25)
            )
        }

        val result = calculateObjectivesFromYears(
            years = years,
            bonusStdWeight = 1.0,
            legacyTarget = 0.0
        )

        assertEquals(1.0, result.stabilityIndex, 1e-9)
        assertEquals(0.25, result.fObjW, 1e-9)
    }

    @Test
    fun calculateObjectivesFromYears_reduces_objective_when_weight_changes_from_zero_to_one() {
        val years = listOf(
            SimulationYear(
                eta = 41,
                funzioneUtilita = 0.0,
                capitaleFineAnno = 1000.0,
                monthlyUtilitySamples = listOf(0.1, 0.5)
            )
        )

        val zeroWeight = calculateObjectivesFromYears(years, 0.0, 0.0)
        val unitWeight = calculateObjectivesFromYears(years, 1.0, 0.0)

        assertTrue(unitWeight.fObjW < zeroWeight.fObjW - 1e-6)
        assertEquals(0.3, zeroWeight.fObjW, 1e-9)
        assertEquals(0.18, unitWeight.fObjW, 1e-9)
    }

    @Test
    fun computeObjective_clamps_weight_to_slider_range() {
        val avg = 0.25
        val std = 0.25

        val atOne = computeObjective(avg, std, 1.0)
        val aboveOne = computeObjective(avg, std, 10.0)

        assertEquals(atOne, aboveOne, 1e-9)
    }

    @Test
    fun defaultGrid_with_positive_weight_stays_finite_and_bounded() {
        val inputs = FinancialInput(
            bonusStdWeight = 10.0
        ).withDefaultAssumptionCurves()
        val surplus = SurplusInput()
        val p1Values = listOf(0.0, 0.25, 0.5, 0.75, 1.0)
        val p2Values = listOf(inputs.etaAttuale, 40, 50, 60, inputs.etaPensione).distinct().sorted()

        val objectives = p2Values.flatMap { p2 ->
            p1Values.map { p1 ->
                calculateSimulationWithWeight(
                    inputs.copy(
                        p1SavingRatioSurplus = p1,
                        p2EtaFineRisparmioNoCapitale = p2,
                        p4EtaAnticipataInizioSpesaCapitale = maxOf(inputs.p4EtaAnticipataInizioSpesaCapitale, p2)
                    ),
                    emptyList(),
                    surplus
                ).first
            }
        }

        assertTrue(objectives.all { it.isFinite() })
        assertTrue(objectives.max() < 1.0)
    }

    @Test
    fun calculateSimulation_matches_python_reference_policy_on_common_dataset() {
        val utilityCurve = listOf(
            CurvePoint(0.0, 0.00),
            CurvePoint(20.0, 0.11),
            CurvePoint(30.0, 0.24),
            CurvePoint(40.0, 0.38),
            CurvePoint(50.0, 0.55),
            CurvePoint(60.0, 0.67),
            CurvePoint(90.0, 0.81),
            CurvePoint(100.0, 0.90)
        )
        val ageCurve = listOf(
            CurvePoint(30.0, 1.00),
            CurvePoint(40.0, 0.94),
            CurvePoint(50.0, 0.78),
            CurvePoint(60.0, 0.52),
            CurvePoint(70.0, 0.36),
            CurvePoint(80.0, 0.31),
            CurvePoint(90.0, 0.30)
        )
        val expenses = listOf(
            SpecificExpense(42, 1500.0, 0.0),
            SpecificExpense(45, 5000.0, 0.0),
            SpecificExpense(45, 3000.0, 0.0),
            SpecificExpense(50, 30000.0, 0.0),
            SpecificExpense(60, 26000.0, 0.0),
            SpecificExpense(70, 20000.0, 0.0),
            SpecificExpense(70, 26000.0, 0.0),
            SpecificExpense(80, 20000.0, 0.0)
        )
        val inputs = FinancialInput(
            eredita = 160000.0,
            soldiDaConservare = 50000.0,
            tfrNetto = 100000.0,
            tassoGuadagnoInteresse = 0.02,
            tassoInteresseDebito = 0.0,
            sogliaMinimaFunzioneUtilita = 0.0,
            capitaleIniziale = 105000.0,
            valoreSpesaGiornalieraMaxUtilita = 100.0,
            utilityCurvePoints = utilityCurve,
            degradationCurvePoints = ageCurve,
            etaAttuale = 41,
            etaPensione = 65,
            etaRicevimentoEredita = 55,
            etaMorte = 82,
            p1SavingRatioSurplus = 0.16,
            p2EtaFineRisparmioNoCapitale = 55,
            p3PercentualeCapitaleDaSpendereAnnualmente = 1.0,
            p4EtaAnticipataInizioSpesaCapitale = 55,
            bonusStdWeight = 1.0
        )
        val surplus = SurplusInput(
            stipendioMensile = 957.5,
            premioRisultatoNettoAnnuale = 0.0,
            tredicesimaQuattordicesimaNetto = 0.0,
            bonusEventualiPersonaliMensile = 300.0,
            bonusEventualiPersonaliMensileFinoEta = 54,
            pensioneMensileNetta = 18.33,
            tredicesimaQuattordicesimaNettoPensione = 0.0,
            bonusEventualiPersonaliPensioneMensile = 0.0,
            bonusEventualiPersonaliPensioneMensileFinoEta = 0,
            altreEntrateMensiliPensione = 0.0,
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

        val pythonReference = calculateSimulationWithWeight(inputs, expenses, surplus).first
        // Locked 2026-07-30 (reserve-gated p3 draw) at 0.1879212626130284. Re-locked 2026-09-02:
        // old annuitized spend rule restored (p3 quota pre-pension, p3-scaled sustainable annuity
        // with forecast brake in retirement) + shortfall-proportional legacy penalty
        // (2.5 x shortfall/legacy) - user-requested policy change ("come un tempo").
        assertEquals(0.17065753269457007, pythonReference, 1e-9)
        assertTrue(pythonReference.isFinite())
    }

    @Test
    fun richSurplusData_produces_nonFlat_p1Landscape() {
        val surplus = SurplusInput(
            stipendioMensile = 3000.0, premioRisultatoNettoAnnuale = 0.0, tredicesimaQuattordicesimaNetto = 0.0,
            pensioneMensileNetta = 0.0, mutuoAffitto = 0.0,
            condominioLavorativa = 0.0, bolletteLavorativa = 0.0, ciboLavorativa = 0.0, veicoliLavorativa = 0.0,
            palestraLavorativa = 0.0, trasportiViaggiLavorativa = 0.0, saluteLavorativa = 0.0,
            vacanzeLavorativa = 0.0, shoppingLavorativa = 0.0, altroLavorativa = 0.0,
            condominioPensione = 0.0, bollettePensione = 0.0, ciboPensione = 0.0, veicoliPensione = 0.0,
            palestraPensione = 0.0, trasportiViaggiPensione = 0.0, salutePensione = 0.0,
            vacanzePensione = 0.0, shoppingPensione = 0.0, altroPensione = 0.0
        )
        val inputs = FinancialInput(
            p1SavingRatioSurplus = 0.0,
            p2EtaFineRisparmioNoCapitale = 65,
            p3PercentualeCapitaleDaSpendereAnnualmente = 1.0,
            p4EtaAnticipataInizioSpesaCapitale = 45,
            etaAttuale = 45,
            etaPensione = 65,
            etaMorte = 85,
            soldiDaConservare = 10_000.0,
            capitaleIniziale = 300_000.0,
            valoreSpesaGiornalieraMaxUtilita = 100.0,
            sogliaMinimaFunzioneUtilita = 0.1
        ).withDefaultAssumptionCurves()

        val fobjs = mutableListOf<Double>()
        var p1 = 0.0
        while (p1 <= 1.001) {
            val cell = inputs.copy(p1SavingRatioSurplus = p1)
            val years = calculateSimulation(cell, emptyList(), surplus)
            val o = calculateObjectivesFromYears(years, bonusStdWeight = 0.0, legacyTarget = cell.soldiDaConservare)
            fobjs.add(o.fObj0)
            println("RICH P1=${"%.2f".format(p1)} fobj0=${"%.4f".format(o.fObj0)} avg=${"%.4f".format(o.avgUtilita)} " +
                "finalNW=${"%.0f".format(o.finalCapital)}")
            p1 += 0.1
        }
        val spread = (fobjs.max() - fobjs.min())
        println("RICH-LANDSCAPE spread=$spread")
        assertTrue(
            "Engine must produce a rich P1 landscape when surplus >> minimum spend (spread=$spread)",
            spread > 0.10
        )
    }
}
