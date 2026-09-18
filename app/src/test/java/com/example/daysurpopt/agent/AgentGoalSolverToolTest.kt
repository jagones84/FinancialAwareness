// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.agent

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.logic.GoalSolverLogic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the RUN_RETIREMENT_SOLVER agent tool: it must wrap the same
 * GoalSolverLogic used by the GUI, honoring the same JSON input overrides
 * as RUN_SIMULATION (so the LLM can zero pension income for a pure
 * capital-based plan, exactly like a user could do in the GUI).
 */
class AgentGoalSolverToolTest {

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

    private fun parseRequiredCapital(output: String): Double {
        val regex = Regex("- Required Initial Capital: ([-0-9.,EN]+)")
        val match = regex.find(output) ?: error("No required capital in output:\n$output")
        val token = match.groupValues[1].replace(",", ".")
        return if (token.equals("N/A", ignoreCase = true)) Double.NaN else token.toDouble()
    }

    @Test
    fun run_retirement_solver_matches_goalsolver_logic() {
        val expected = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs = inputs,
            specificExpenses = expenses,
            surplusData = surplus,
            stopWorkAge = 45,
            threshold = 0.25
        )

        val output = runTool("""RUN_RETIREMENT_SOLVER {"stopWorkAge": 45, "happinessThreshold": 0.25}""")

        assertTrue(expected.isFeasible)
        assertTrue(output.contains("- Stop Work Age: 45"))
        assertTrue(output.contains("- Status: Feasible"))
        val reported = parseRequiredCapital(output)
        assertTrue(
            "Tool capital $reported must match GoalSolverLogic ${expected.requiredCapital}",
            kotlin.math.abs(reported - expected.requiredCapital!!) < 0.01
        )
    }

    @Test
    fun run_retirement_solver_honors_surplus_overrides() {
        val overriddenSurplus = surplus.copy(pensioneMensileNetta = 0.0)
        val expected = GoalSolverLogic.solveMinimumInitialCapital(
            baseInputs = inputs,
            specificExpenses = expenses,
            surplusData = overriddenSurplus,
            stopWorkAge = 45,
            threshold = 0.25
        )

        val output = runTool(
            """RUN_RETIREMENT_SOLVER {"stopWorkAge": 45, "happinessThreshold": 0.25, "pensioneMensileNetta": 0.0}"""
        )

        val reported = parseRequiredCapital(output)
        assertTrue(
            "Tool capital $reported must match the overridden plan ${expected.requiredCapital}",
            kotlin.math.abs(reported - expected.requiredCapital!!) < 0.01
        )
    }

    @Test
    fun run_retirement_solver_reports_ceiling_reason_when_unreachable() {
        val output = runTool("""RUN_RETIREMENT_SOLVER {"stopWorkAge": 45, "happinessThreshold": 0.5}""")

        assertTrue(output.contains("- Status: Infeasible"))
        assertTrue(
            "Infeasible output must explain the utility ceiling",
            output.contains("maximum achievable utility", ignoreCase = true)
        )
        assertTrue(
            "Infeasible output must not report a capital number",
            output.contains("- Required Initial Capital: N/A")
        )
    }
}
