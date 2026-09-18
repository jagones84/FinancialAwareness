// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.agent

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SurplusInput
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documentation contract of the agent system prompt.
 * The LLM can only use tools it is told about: the prompt must list every
 * tool the GUI-side state exposes to the chat workflow, and must describe
 * the current weight semantics (direct w in [0,1], penalized happiness formula).
 */
class PromptDocumentationTest {

    private val prompt = PromptConstructor.constructSystemPrompt(
        inputs = FinancialInput().withDefaultAssumptionCurves(),
        specificExpenses = emptyList(),
        surplusData = SurplusInput()
    )

    @Test
    fun system_prompt_documents_core_simulation_tools() {
        listOf(
            "GET_FINANCIAL_CONTEXT",
            "RUN_SIMULATION",
            "RUN_OPTIMIZATION",
            "RUN_SENSITIVITY",
            "RUN_MULTI_AGENT_ANALYSIS",
            "WEB_SEARCH",
            "GET_TIME"
        ).forEach { tool ->
            assertTrue("System prompt must document $tool", prompt.contains(tool))
        }
    }

    @Test
    fun system_prompt_documents_direct_weight_semantics() {
        assertTrue(prompt.contains("bonusStdWeight"))
        assertTrue(prompt.contains("Do not use P3 as a proxy"))
    }

    @Test
    fun system_prompt_exposes_all_financial_input_override_fields() {
        listOf(
            "tassoGuadagnoInteresse",
            "eredita",
            "soldiDaConservare",
            "tfrNetto",
            "capitaleIniziale",
            "valoreSpesaGiornalieraMaxUtilita",
            "sogliaMinimaFunzioneUtilita",
            "p1SavingRatioSurplus",
            "p2EtaFineRisparmioNoCapitale",
            "p3PercentualeCapitaleDaSpendereAnnualmente",
            "p4EtaAnticipataInizioSpesaCapitale",
            "bonusStdWeight",
            "utilityCurvePoints",
            "degradationCurvePoints"
        ).forEach { field ->
            assertTrue("Tool docs must expose $field", prompt.contains(field))
        }
    }

    @Test
    fun system_prompt_documents_fetch_page_tool() {
        assertTrue(
            "FETCH_PAGE is executable in AgentToolExecutor and must be documented in the system prompt",
            prompt.contains("FETCH_PAGE")
        )
    }

    @Test
    fun system_prompt_documents_utility_threshold_override() {
        assertTrue(
            "sogliaMinimaFunzioneUtilita is overridable in AgentToolExecutor and must be listed among RUN_SIMULATION params",
            prompt.contains("sogliaMinimaFunzioneUtilita")
        )
    }

    @Test
    fun system_prompt_documents_goal_solver_tool() {
        assertTrue(
            "RUN_RETIREMENT_SOLVER must be documented with its parameters",
            prompt.contains("RUN_RETIREMENT_SOLVER") && prompt.contains("happinessThreshold")
        )
    }

    @Test
    fun system_prompt_limits_multi_agent_analysis_to_one_run_per_request() {
        assertTrue(
            "System prompt must instruct the LLM to call RUN_MULTI_AGENT_ANALYSIS at most once per user request",
            prompt.contains("at most once per user request")
        )
    }

    @Test
    fun system_prompt_documents_assumption_curve_workflow() {
        assertTrue(
            "System prompt must tell the agent that GET_FINANCIAL_CONTEXT returns the effective curves",
            prompt.contains("effective curves")
        )
        assertTrue(
            "System prompt must clarify curve overrides are what-if only (no persistence to the Setup tab)",
            prompt.contains("what-if only")
        )
    }

    @Test
    fun system_prompt_requires_literal_tool_command_emission() {
        assertTrue(
            "System prompt must forbid announcing a tool without emitting the literal command",
            prompt.contains("Never merely announce")
        )
        assertTrue(
            "System prompt must require the command token on a standalone line",
            prompt.contains("standalone line")
        )
    }

    @Test
    fun system_prompt_documents_optimization_modes_and_ga_overrides() {
        assertTrue(
            "RUN_OPTIMIZATION docs must expose the GUI optimization modes",
            prompt.contains("PARETO_KNEE") && prompt.contains("PARETO_FRONT")
        )
        assertTrue(
            "RUN_OPTIMIZATION docs must expose the GA config overrides",
            prompt.contains("popSize") && prompt.contains("generations")
        )
    }

    @Test
    fun risk_prompt_mentions_stability_concepts() {
        val riskPrompt = AgentPrompts.getRiskPrompt(
            baseInputs = FinancialInput(),
            commonFinancialContext = "ctx"
        )
        assertTrue(riskPrompt.contains("Stability Index"))
        assertTrue(riskPrompt.contains("Weight (w)"))
    }
}
