// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.agent

import com.example.daysurpopt.domain.FinancialInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptsTest {

    @Test
    fun riskPrompt_usesCurrentBoundedStabilityScoreDefinition() {
        val prompt = AgentPrompts.getRiskPrompt(
            baseInputs = FinancialInput(),
            commonFinancialContext = "context"
        )

        assertTrue(
            "Risk prompt must define StabilityScore as Avg / (Avg + StdDev)",
            prompt.contains("Avg / (Avg + StdDev)")
        )
    }

    @Test
    fun riskPrompt_usesCurrentPenalizedHappinessObjective() {
        val prompt = AgentPrompts.getRiskPrompt(
            baseInputs = FinancialInput(),
            commonFinancialContext = "context"
        )

        assertTrue(
            "Risk prompt must describe the penalized happiness objective fScalar = Avg * ((1 - w) + w * StabilityScore)",
            prompt.contains("Avg * ((1 - w) + w * StabilityScore)")
        )
    }

    @Test
    fun riskPrompt_neverMentionsLegacyStaleFormulas() {
        val prompt = AgentPrompts.getRiskPrompt(
            baseInputs = FinancialInput(),
            commonFinancialContext = "context"
        )

        assertFalse(
            "Risk prompt must not teach the removed 'StdDev / (Weight/100)' definition",
            prompt.contains("StdDev / (Weight/100)")
        )
        assertFalse(
            "Risk prompt must not teach the removed unbounded 'AvgUtility / StdDev' reward term",
            prompt.contains("AvgUtility / StdDev")
        )
    }

    @Test
    fun sustainabilityPrompt_definesP1AsSurplusShare() {
        val prompt = AgentPrompts.getSustainabilityPrompt(
            baseInputs = FinancialInput(),
            commonFinancialContext = "context"
        )

        assertTrue(
            "Sustainability prompt must define P1 as the saved fraction of the monthly SURPLUS (income minus fixed expenses)",
            prompt.contains("SURPLUS (income minus fixed expenses)")
        )
        assertTrue(
            "Sustainability prompt must forbid income-based savings-rate comparisons",
            prompt.contains("not a percentage of total income")
        )
    }

    @Test
    fun riskPrompt_explainsDebtIsConditional() {
        val prompt = AgentPrompts.getRiskPrompt(
            baseInputs = FinancialInput(),
            commonFinancialContext = "context"
        )

        assertTrue(
            "Risk prompt must state the debt rate applies only if the simulation reports actual debt",
            prompt.contains("only if the simulation reports actual debt")
        )
        assertTrue(
            "Risk prompt must forbid debt-elimination advice when no debt occurs",
            prompt.contains("do not recommend debt elimination")
        )
    }

    @Test
    fun masterPrompt_forbidsInventedNumbers() {
        val prompt = AgentPrompts.getMasterPrompt(
            marketContext = "market",
            sustainabilityReport = "s",
            riskReport = "r",
            analystReport = "a",
            isComparing = false,
            locale = java.util.Locale.US
        )

        assertTrue(
            "Master prompt must restrict the report to engine-provided numbers",
            prompt.contains("Use only the numbers provided")
        )
    }
}
