// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.agent

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.logic.calculateSimulationWithWeight
import com.example.daysurpopt.logic.calculateStandardDeviation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The multi-agent workflow must feed the Sustainability and Risk agents with the REAL
 * engine results (same calculateSimulationWithWeight the GUI runs), the actual monthly
 * surplus, and the actual debt occurrence — otherwise those agents hallucinate monetary
 * figures and generic advice ("eliminate your debt" when no debt exists).
 */
class AgentMultiAgentContextTest {

    private val inputs = FinancialInput(
        eredita = 20000.0,
        soldiDaConservare = 50000.0,
        tfrNetto = 25000.0,
        tassoGuadagnoInteresse = 0.02,
        tassoInteresseDebito = 0.07,
        sogliaMinimaFunzioneUtilita = 0.1,
        capitaleIniziale = 105000.0,
        valoreSpesaGiornalieraMaxUtilita = 82.0,
        etaAttuale = 30,
        etaPensione = 65,
        etaRicevimentoEredita = 55,
        etaMorte = 82,
        p1SavingRatioSurplus = 0.158,
        p2EtaFineRisparmioNoCapitale = 53,
        p3PercentualeCapitaleDaSpendereAnnualmente = 0.9913,
        p4EtaAnticipataInizioSpesaCapitale = 57,
        bonusStdWeight = 0.15
    ).withDefaultAssumptionCurves()

    private val surplus = SurplusInput(
        stipendioMensile = 2800.0,
        tredicesimaQuattordicesimaNetto = 2800.0,
        pensioneMensileNetta = 1650.0,
        mutuoAffitto = 600.0,
        mutuoAffittoFinoEta = 60,
        ciboLavorativa = 400.0,
        bolletteLavorativa = 150.0,
        veicoliLavorativa = 200.0
    )
    private val expenses = emptyList<SpecificExpense>()

    private fun buildContext(market: String = "Eurozone inflation ~2%"): String = runBlocking {
        AgentToolExecutor.buildMultiAgentFinancialContext(inputs, expenses, surplus, market)
    }

    private fun parseNumber(text: String, label: String): Double {
        val regex = Regex(Regex.escape(label) + "\\s*[:\\-]?\\s*\\*{0,2}([-0-9.,E]+)")
        val match = regex.find(text) ?: error("Label '$label' not found in:\n$text")
        return match.groupValues[1].replace(",", ".").toDouble()
    }

    @Test
    fun multi_agent_context_contains_real_base_simulation_metrics() = runBlocking {
        val (obj, years) = calculateSimulationWithWeight(inputs, expenses, surplus)
        val utilities = years.flatMap { y ->
            if (y.monthlyUtilitySamples.isNotEmpty()) y.monthlyUtilitySamples else listOf(y.funzioneUtilita)
        }
        val avg = utilities.average()
        val std = calculateStandardDeviation(utilities)
        val stability = AgentReportFormatter.computeStabilityIndex(avg, std)

        val context = buildContext()

        assertEquals(obj, parseNumber(context, "Objective Function"), 1e-4)
        assertEquals(avg, parseNumber(context, "Avg Utility"), 1e-4)
        assertEquals(std, parseNumber(context, "Standard Deviation"), 1e-4)
        assertEquals(stability, parseNumber(context, "Stability Score"), 1e-4)
        assertEquals(
            years.lastOrNull()?.capitaleFineAnno ?: 0.0,
            parseNumber(context, "Final Capital"),
            0.02
        )
    }

    @Test
    fun multi_agent_context_states_monthly_surplus_and_p1_semantics() {
        val context = buildContext()

        val expectedSurplus =
            surplus.getEntrateMensiliLavorativa() - surplus.getUsciteMensiliLavorativa(true)
        assertEquals(expectedSurplus, parseNumber(context, "Monthly Surplus"), 0.01)
        assertEquals(expectedSurplus * inputs.p1SavingRatioSurplus, parseNumber(context, "Monthly Saving"), 0.01)

        assertTrue(
            "Context must define P1 as a share of the monthly SURPLUS:\n$context",
            context.contains("SURPLUS (income minus fixed expenses)")
        )
        assertTrue(
            "Context must forbid income-based savings-rate comparisons:\n$context",
            context.contains("not a percentage of total income")
        )
    }

    @Test
    fun multi_agent_context_reports_no_debt_for_solvent_plan() {
        val context = buildContext()

        assertTrue(
            "Solvent plan must report an explicit no-debt status:\n$context",
            context.contains("No debt occurs in this plan")
        )
        assertFalse(context.contains("Debt occurs in years"))
    }

    @Test
    fun multi_agent_context_reports_debt_years_when_capital_exhausted() = runBlocking {
        val insolventInputs = inputs.copy(
            capitaleIniziale = 5000.0
        )
        val insolventSurplus = surplus.copy(
            stipendioMensile = 0.0,
            tredicesimaQuattordicesimaNetto = 0.0,
            pensioneMensileNetta = 0.0
        )
        val (_, years) = calculateSimulationWithWeight(insolventInputs, expenses, insolventSurplus)
        val debtAges = years.filter { it.debtAmount > 0.0 }.map { it.eta }
        assertTrue("Test scenario must actually produce debt", debtAges.isNotEmpty())

        val context = AgentToolExecutor.buildMultiAgentFinancialContext(
            insolventInputs, expenses, insolventSurplus, "benchmarks"
        )

        assertTrue(
            "Insolvent plan must report debt years:\n$context",
            context.contains("Debt occurs in years")
        )
        assertTrue(
            "Debt years list must contain the engine-computed ages ${debtAges}:\n$context",
            debtAges.all { age -> context.contains(age.toString()) }
        )
    }

    @Test
    fun extract_command_name_parses_tool_commands() {
        assertEquals("RUN_MULTI_AGENT_ANALYSIS", AgentToolExecutor.extractCommandName("RUN_MULTI_AGENT_ANALYSIS {}"))
        assertEquals("RUN_SIMULATION", AgentToolExecutor.extractCommandName("I will run it. RUN_SIMULATION {\"eredita\": 1}"))
        assertNull(AgentToolExecutor.extractCommandName("Just a plain answer with no tool."))
    }

    @Test
    fun repeated_tool_call_in_same_turn_is_not_executed_twice() = runBlocking {
        var llmCalls = 0
        val result = AgentToolExecutor.checkForToolUse(
            response = "RUN_MULTI_AGENT_ANALYSIS {}",
            baseInputs = inputs,
            specificExpenses = expenses,
            surplusData = surplus,
            alreadyExecutedCommands = setOf("RUN_MULTI_AGENT_ANALYSIS"),
            llmRequest = { llmCalls++; "should not be called" }
        )

        assertTrue(
            "Guard must return an explicit already-executed notice:\n$result",
            result!!.contains("already executed") && result.contains("RUN_MULTI_AGENT_ANALYSIS")
        )
        assertEquals("No LLM call must happen for a repeated tool", 0, llmCalls)
    }
}
