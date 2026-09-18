// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.agent

import com.example.daysurpopt.R
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.logic.OptimizationLogic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the RUN_SENSITIVITY agent tool: it must wrap the same
 * OptimizationLogic.runSensitivityAnalysis used by the GUI, honor the same
 * JSON input overrides as RUN_SIMULATION (so the LLM can request e.g.
 * "sensitivity at 4% interest"), and report human-readable ranked impacts.
 */
class AgentSensitivityToolTest {

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

    private fun runTool(response: String): String = runBlocking {
        AgentToolExecutor.checkForToolUse(
            response = response,
            baseInputs = inputs,
            specificExpenses = expenses,
            surplusData = surplus,
            llmRequest = { prompt -> "stub llm answer" }
        ) ?: error("Tool command not detected in: $response")
    }

    private data class ParsedImpact(val name: String, val impact: Double, val unit: String)

    private fun parseImpacts(output: String): List<ParsedImpact> {
        val regex = Regex("- ([^:]+): ([-0-9.,E]+) pt / ([^\n]+)")
        return regex.findAll(output).map {
            ParsedImpact(
                it.groupValues[1].trim(),
                it.groupValues[2].replace(",", ".").toDouble(),
                it.groupValues[3].trim()
            )
        }.toList()
    }

    @Test
    fun run_sensitivity_tool_reports_ranked_readable_impacts() {
        val output = runTool("RUN_SENSITIVITY {}")

        assertTrue(
            "Output must have the sensitivity header:\n$output",
            output.contains("**Sensitivity Analysis (impact on average utility):**")
        )
        val impacts = parseImpacts(output)
        assertTrue("Output must list several parameters:\n$output", impacts.size >= 10)
        assertTrue(
            "Names must be human readable (GUI string resources), got: ${impacts.map { it.name }}",
            impacts.any { it.name == "Interest Rate" } && impacts.any { it.name == "P1 Saving Ratio" }
        )
        val absImpacts = impacts.map { kotlin.math.abs(it.impact) }
        assertEquals(
            "Impacts must be ranked by absolute effect like the GUI",
            absImpacts,
            absImpacts.sortedDescending()
        )
    }

    @Test
    fun run_sensitivity_tool_matches_gui_logic_with_overrides() = runBlocking {
        val expected = OptimizationLogic.runSensitivityAnalysis(
            baseInputs = inputs.copy(tassoGuadagnoInteresse = 0.06),
            specificExpenses = expenses,
            surplusData = surplus
        ).first { it.nameResId == R.string.sens_int_rate }

        val output = runTool("""RUN_SENSITIVITY {"tassoGuadagnoInteresse": 0.06}""")
        val reported = parseImpacts(output).first { it.name == "Interest Rate" }

        assertEquals(expected.unitResId, R.string.unit_pt_1pp)
        assertEquals("1pp", reported.unit)
        assertEquals(
            "Tool impact must match OptimizationLogic at 6% interest",
            expected.scaledImpact,
            reported.impact,
            1e-4
        )
    }

    @Test
    fun run_sensitivity_tool_documented_in_system_prompt() {
        val prompt = PromptConstructor.constructSystemPrompt(inputs, expenses, surplus)
        assertTrue(
            "RUN_SENSITIVITY must be documented in the system prompt",
            prompt.contains("RUN_SENSITIVITY")
        )
    }
}
