// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.agent

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.GAConfigUI
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RUN_OPTIMIZATION must reach GUI parity:
 *  - the user's GAConfigUI (popSize/generations/pc/pm/ranges) is the default GA config;
 *  - optional JSON overrides (popSize, generations, pc, pm) are accepted;
 *  - 'mode' selects TRUE_SCALAR (default), PARETO_KNEE or PARETO_FRONT, exactly like the GUI;
 *  - RUN_MULTI_AGENT_ANALYSIS receives the comparison context when compare mode is active.
 */
class AgentOptimizationToolTest {

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

    private val surplus = SurplusInput(mutuoAffitto = 600.0, mutuoAffittoFinoEta = 60)
    private val expenses = emptyList<SpecificExpense>()
    private val tinyGaConfig = GAConfigUI(popSize = "10", generations = "3")

    private fun runTool(
        response: String,
        gaConfig: GAConfigUI? = tinyGaConfig,
        comparisonContext: String? = null
    ): Pair<String, MutableList<String>> {
        val capturedPrompts = mutableListOf<String>()
        val output = runBlocking {
            AgentToolExecutor.checkForToolUse(
                response = response,
                baseInputs = inputs,
                specificExpenses = expenses,
                surplusData = surplus,
                userGaConfig = gaConfig,
                comparisonContext = comparisonContext,
                llmRequest = { prompt ->
                    capturedPrompts.add(prompt)
                    "stub report"
                }
            ) ?: error("Tool command not detected in: $response")
        }
        return output to capturedPrompts
    }

    @Test
    fun run_optimization_pareto_knee_mode_reports_front_and_knee() {
        val (output, _) = runTool(
            """RUN_OPTIMIZATION {"mode": "PARETO_KNEE", "popSize": 20, "generations": 5}"""
        )

        assertTrue(output.contains("Pareto Knee"))
        assertTrue(output.contains("Front Size"))
        assertTrue(output.contains("P1 (Savings Rate)"))
        assertTrue(output.contains("P4 (Spending Start Age)"))
    }

    @Test
    fun run_optimization_pareto_front_mode_reports_front() {
        val (output, _) = runTool(
            """RUN_OPTIMIZATION {"mode": "PARETO_FRONT", "popSize": 20, "generations": 5}"""
        )

        assertTrue(output.contains("Pareto Front"))
        assertTrue(output.contains("Front Size"))
        assertTrue(output.contains("P1 (Savings Rate)"))
    }

    @Test
    fun run_optimization_respects_ga_config_overrides_in_scalar_mode() {
        val (output, _) = runTool("""RUN_OPTIMIZATION {"popSize": 10, "generations": 3}""")

        val regex = Regex("\\*\\*Objective Function\\*\\*: ([-0-9.,E]+) -> \\*\\*([-0-9.,E]+)\\*\\*")
        val match = regex.find(output) ?: error("Scalar output not parseable:\n$output")
        val current = match.groupValues[1].replace(',', '.').toDouble()
        val optimized = match.groupValues[2].replace(',', '.').toDouble()
        assertTrue("Optimized ($optimized) must be >= current ($current)", optimized >= current - 1e-9)
    }

    @Test
    fun run_multi_agent_analysis_receives_comparison_context() {
        val (output, prompts) = runTool(
            "RUN_MULTI_AGENT_ANALYSIS {}",
            comparisonContext = "**COMPARISON MODE ACTIVE** Profile 2 (Pippo): Avg Utility 0.42"
        )

        assertTrue(output.contains("Multi-Agent Analysis Report"))
        assertTrue(
            "The specialized agents must receive the comparison context",
            prompts.any { it.contains("COMPARISON MODE ACTIVE") && it.contains("Profile 2 (Pippo)") }
        )
    }

    @Test
    fun run_multi_agent_analysis_without_comparison_has_no_comparison_marker() {
        val (_, prompts) = runTool("RUN_MULTI_AGENT_ANALYSIS {}")

        assertTrue(
            "No comparison marker must appear when compare mode is off",
            prompts.none { it.contains("COMPARISON MODE ACTIVE") }
        )
    }
}
