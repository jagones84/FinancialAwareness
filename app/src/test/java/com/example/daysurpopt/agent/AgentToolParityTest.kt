// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.agent

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.logic.calculateSimulationWithWeight
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the parity contract between the AI Agent tool layer and the GUI engine.
 *
 * The agent MUST evaluate scenarios through the exact same functions used by the GUI
 * (calculateSimulationWithWeight / OptimizationLogic). These tests prove it:
 * for identical inputs, the tool output text must report the GUI engine numbers.
 */
class AgentToolParityTest {

    private val inputs = FinancialInput(
        eredita = 20000.0,
        soldiDaConservare = 30000.0,
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

    private val surplus = SurplusInput(mutuoAffitto = 600.0, mutuoAffittoFinoEta = 60)
    private val expenses = listOf(SpecificExpense(age = 45, amount = 8000.0, utilityOffset = 0.05))

    private fun runTool(response: String): String = runBlocking {
        AgentToolExecutor.checkForToolUse(
            response = response,
            baseInputs = inputs,
            specificExpenses = expenses,
            surplusData = surplus,
            llmRequest = { prompt -> "stub llm answer for: ${prompt.take(30)}" }
        ) ?: error("Tool command not detected in: $response")
    }

    @Test
    fun run_simulation_without_overrides_reports_gui_engine_values() {
        val (expectedObj, expectedYears) =
            calculateSimulationWithWeight(inputs, expenses, surplus)
        val expectedAvg = expectedYears.map { it.funzioneUtilita }.average()

        val output = runTool("RUN_SIMULATION {}")

        assertTrue(output.contains("- Objective Function: " + "%.4f".format(expectedObj)))
        assertTrue(output.contains("- Final Capital: " + "%.2f".format(expectedYears.last().capitaleFineAnno)))
        assertTrue(output.contains("- Avg Utility: " + "%.4f".format(expectedAvg)))
    }

    @Test
    fun run_simulation_with_overrides_matches_direct_input_copy() {
        val json = """{"tassoGuadagnoInteresse": 0.05, "capitaleIniziale": 30000.0, "stipendioMensile": 2500.0}"""
        val overriddenInputs = inputs.copy(
            tassoGuadagnoInteresse = 0.05,
            capitaleIniziale = 30000.0
        )
        val overriddenSurplus = surplus.copy(stipendioMensile = 2500.0)
        val (expectedObj, expectedYears) =
            calculateSimulationWithWeight(overriddenInputs, expenses, overriddenSurplus)

        val output = runTool("RUN_SIMULATION $json")

        assertTrue(output.contains("- Objective Function: " + "%.4f".format(expectedObj)))
        assertTrue(output.contains("- Final Capital: " + "%.2f".format(expectedYears.last().capitaleFineAnno)))
    }

    @Test
    fun run_simulation_honors_utility_threshold_override() {
        val json = """{"sogliaMinimaFunzioneUtilita": 0.3}"""
        val overriddenInputs = inputs.copy(sogliaMinimaFunzioneUtilita = 0.3)
        val (expectedObj, _) = calculateSimulationWithWeight(overriddenInputs, expenses, surplus)

        val output = runTool("RUN_SIMULATION $json")

        assertTrue(output.contains("- Objective Function: " + "%.4f".format(expectedObj)))
    }

    @Test
    fun get_financial_context_returns_full_gui_state() {
        val output = runTool("GET_FINANCIAL_CONTEXT {}")

        assertTrue(output.contains("\"financialInput\""))
        assertTrue(output.contains("\"surplusInput\""))
        assertTrue(output.contains("\"specificExpenses\""))
        assertTrue(output.contains("\"capitaleIniziale\":15000.0"))
        assertTrue(output.contains("\"stipendioMensile\":2000.0"))
    }

    @Test
    fun run_optimization_never_reports_less_than_current_objective() {
        val output = runTool("RUN_OPTIMIZATION {}")

        val regex = Regex("\\*\\*Objective Function\\*\\*: ([-0-9.,E]+) -> \\*\\*([-0-9.,E]+)\\*\\*")
        val match = regex.find(output) ?: error("Optimization output not parseable:\n$output")
        val current = match.groupValues[1].replace(',', '.').toDouble()
        val optimized = match.groupValues[2].replace(',', '.').toDouble()

        assertTrue(
            "Optimized objective ($optimized) must be >= current ($current)",
            optimized >= current - 1e-9
        )
        assertTrue(output.contains("P1 (Savings Rate)"))
        assertTrue(output.contains("P4 (Spending Start Age)"))
    }
}
