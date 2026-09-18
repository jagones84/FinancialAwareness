// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.agent

import com.example.daysurpopt.domain.CurvePoint
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.logic.calculateSimulationWithWeight
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The agent must be able to READ the assumption curves (utility vs extra daily spending,
 * and age degradation) and to EDIT them as what-if overrides — exactly like the user can
 * do in the Setup/Assumptions screen. These tests lock that access.
 */
class AgentCurveAccessTest {

    private val surplus = SurplusInput(mutuoAffitto = 600.0, mutuoAffittoFinoEta = 60)
    private val expenses = emptyList<SpecificExpense>()

    private val customInputs = FinancialInput(
        capitaleIniziale = 20000.0,
        etaAttuale = 30,
        etaPensione = 65,
        etaMorte = 82,
        utilityCurvePoints = listOf(
            CurvePoint(0.0, 0.0),
            CurvePoint(40.0, 0.5),
            CurvePoint(80.0, 1.0)
        ),
        degradationCurvePoints = listOf(
            CurvePoint(30.0, 1.0),
            CurvePoint(60.0, 0.9),
            CurvePoint(82.0, 0.4)
        )
    )

    private fun runTool(inputs: FinancialInput, response: String): String = runBlocking {
        AgentToolExecutor.checkForToolUse(
            response = response,
            baseInputs = inputs,
            specificExpenses = expenses,
            surplusData = surplus,
            llmRequest = { "stub" }
        ) ?: error("Tool command not detected in: $response")
    }

    private fun contextJson(output: String): Map<String, Any> {
        val start = output.indexOf('{')
        val json = output.substring(start, output.lastIndexOf('}') + 1)
        val type = object : TypeToken<Map<String, Any>>() {}.type
        return Gson().fromJson(json, type)
    }

    @Test
    fun get_financial_context_exposes_custom_curves() {
        val output = runTool(customInputs, "GET_FINANCIAL_CONTEXT {}")
        val json = contextJson(output)

        val financial = json["financialInput"] as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val utility = financial["utilityCurvePoints"] as List<Map<String, Double>>
        @Suppress("UNCHECKED_CAST")
        val degradation = financial["degradationCurvePoints"] as List<Map<String, Double>>

        assertEquals(3, utility.size)
        assertEquals(0.5, utility[1]["y"]!!, 1e-9)
        assertEquals(0.4, degradation[2]["y"]!!, 1e-9)
    }

    @Test
    fun get_financial_context_exposes_effective_default_curves_when_not_customized() {
        val output = runTool(FinancialInput(), "GET_FINANCIAL_CONTEXT {}")
        val json = contextJson(output)

        val effective = json["effectiveCurves"] as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val utility = effective["utilityCurve"] as List<Map<String, Double>>
        @Suppress("UNCHECKED_CAST")
        val degradation = effective["degradationCurve"] as List<Map<String, Double>>

        assertTrue(
            "Effective utility curve must be materialized (utility vs extra daily spending), got: $utility",
            utility.size >= 5 && utility.first()["y"] == 0.0 && utility.last()["y"]!! > 0.9
        )
        assertTrue(
            "Effective degradation curve (decay with age) must be materialized, got: $degradation",
            degradation.size >= 5 && degradation.first()["x"] == 30.0
        )
    }

    @Test
    fun run_simulation_applies_degradation_curve_override() = runBlocking {
        val flatDegradation = listOf(
            CurvePoint(30.0, 1.0),
            CurvePoint(65.0, 1.0),
            CurvePoint(82.0, 1.0)
        )

        val (expectedObj, expectedYears) = calculateSimulationWithWeight(
            customInputs.copy(degradationCurvePoints = flatDegradation), expenses, surplus
        )

        val output = runTool(
            customInputs,
            """RUN_SIMULATION {"degradationCurvePoints": [{"x":30,"y":1.0},{"x":65,"y":1.0},{"x":82,"y":1.0}]}"""
        )

        val reportedFinal = Regex("- Final Capital: ([-0-9.,E]+)").find(output)
            ?.groupValues?.get(1)?.replace(",", ".")?.toDouble()
        assertNotNull("Output must contain Final Capital:\n$output", reportedFinal)
        assertEquals(expectedYears.last().capitaleFineAnno, reportedFinal!!, 0.02)
    }

    @Test
    fun multiple_tool_commands_in_one_response_all_execute() = runBlocking {
        val flatDegradation = listOf(
            CurvePoint(30.0, 1.0),
            CurvePoint(65.0, 1.0),
            CurvePoint(82.0, 1.0)
        )
        val (_, expectedYears) = calculateSimulationWithWeight(
            customInputs.copy(degradationCurvePoints = flatDegradation), expenses, surplus
        )

        val output = runTool(
            customInputs,
            "I will read the context and then simulate.\n" +
                "GET_FINANCIAL_CONTEXT\n" +
                """RUN_SIMULATION {"degradationCurvePoints": [{"x":30,"y":1.0},{"x":65,"y":1.0},{"x":82,"y":1.0}]}"""
        )

        assertTrue(
            "First command output (context) must be present:\n$output",
            output.contains("**Current Context:**")
        )
        assertTrue(
            "Second command output (simulation) must be present — a single response may carry multiple tool commands:\n$output",
            output.contains("**Simulation Result:**")
        )
        val reportedFinal = Regex("- Final Capital: ([-0-9.,E]+)").find(output)
            ?.groupValues?.get(1)?.replace(",", ".")?.toDouble()
        assertNotNull(reportedFinal)
        assertEquals(expectedYears.last().capitaleFineAnno, reportedFinal!!, 0.02)
    }
}
