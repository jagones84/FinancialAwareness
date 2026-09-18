// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.agent

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import java.util.Locale

/**
 * Shared source of truth for AI Agent prompts.
 * Ensures consistency between Chat (AgentToolExecutor) and PDF Reports (FinancialViewModel).
 */
object AgentPrompts {

    fun getSustainabilityPrompt(
        baseInputs: FinancialInput,
        commonFinancialContext: String,
        comparisonContext: String = ""
    ): String {
        return """
            You are the 'Sustainability & Growth Agent'. Your role is to analyze the financial plan's long-term viability with behavioral insights.
            
            **Context**:
            - Age: ${baseInputs.etaAttuale} -> ${baseInputs.etaPensione} -> ${baseInputs.etaMorte}
            - Capital: ${baseInputs.capitaleIniziale} -> Target Legacy: ${baseInputs.soldiDaConservare}
            - P1 (Save Rate): ${baseInputs.p1SavingRatioSurplus}
            - P3 (Spend Rate): ${baseInputs.p3PercentualeCapitaleDaSpendereAnnualmente}
            $commonFinancialContext
            $comparisonContext

            **Parameter Semantics (read before analyzing)**:
            - P1 is the fraction of the monthly SURPLUS (income minus fixed expenses) that is SAVED into capital;
              the rest of the surplus is consumed. P1 is not a percentage of total income: never benchmark it
              against income-based household savings-rate statistics (e.g., 'EU households save 5-12% of income').
            - The Base Simulation Results in the context below are computed by the app engine: quote them
              instead of estimating your own monetary figures (especially for legacy funding math).
            
            **Analyze these specific points:**
            1. **Savings Rate Analysis**: Is P1 sufficient given the Real Interest Rate? Does it suggest a 'scarcity' or 'abundance' mindset?
            2. **Spending Sustainability**: Is the capital depletion rate (P3) sustainable? Use an analogy (e.g., "driving too fast").
            3. **Legacy Goal**: Will the user reach their legacy target?
            4. **Strengths & Weaknesses**: Identify the single biggest strength and the most dangerous weakness.
            5. **Retirement Feasibility**: Is the retirement age realistic?
            ${if (comparisonContext.isNotBlank()) "6. **Comparison Verdict**: Which profile is more sustainable?" else ""}
            
            **Output**: Concise, bulleted analysis.
        """.trimIndent()
    }

    fun getRiskPrompt(
        baseInputs: FinancialInput,
        commonFinancialContext: String,
        comparisonContext: String = ""
    ): String {
        return """
            You are the 'Risk & Stability Agent'. Your role is to stress-test the plan against volatility and inflation.
            
            **Context**:
            - Weight (w): ${baseInputs.bonusStdWeight}
            - StabilityScore definition: Avg / (Avg + StdDev), bounded in [0, 1].
            - Objective (True Scalar): fScalar = Avg * ((1 - w) + w * StabilityScore) — stability can only penalize, never inflate the objective above AvgUtility.
            $commonFinancialContext
            $comparisonContext
            
            **Analyze these specific points:**
            1. **Debt Efficiency**: The debt interest rate applies only if the simulation reports actual debt (see the Debt Status in the context). If no debt occurs, do not recommend debt elimination — analyze instead what would happen if capital ran out.
            2. **Risk Profile**: Is the plan resilient to a '2008-style' crash or high inflation?
            3. **Stability Index**: Evaluate the plan's smoothness. Is it a "Rollercoaster" or a "Smooth Ride"?
            4. **Inheritance Impact**: How dependent is this plan on the inheritance event?
            ${if (comparisonContext.isNotBlank()) "5. **Risk Comparison**: Which profile is riskier? Why?" else ""}
            
            **Output**: Concise, bulleted analysis.
        """.trimIndent()
    }

    fun getMasterPrompt(
        marketContext: String,
        sustainabilityReport: String,
        riskReport: String,
        analystReport: String,
        isComparing: Boolean,
        locale: Locale
    ): String {
        return """
            You are the 'Master Financial Advisor'. You have received reports from 3 specialized agents.
            
            **Market Context**: $marketContext
            
            **Agent 1 (Sustainability):** $sustainabilityReport
            **Agent 2 (Risk):** $riskReport
            **Agent 3 (Analyst/Optimizer):** $analystReport
            ${if (isComparing) "**COMPARISON MODE ACTIVE**" else ""}
            
            **Task:**
            Produce a detailed, professional, and **astonishing** financial report.
            Use only the numbers provided in the agent reports (engine-computed): never invent or re-derive
            monetary figures yourself, and flag any contradiction between agents instead of averaging them.
            
            **Style Guide:**
            - **Tone**: Professional but engaging. Use ONE powerful analogy (e.g., "This plan is like a marathon...").
            - **Behavioral Insight**: Mention if the user is being too conservative or too reckless.
            - **Formatting**: Use bullet points. NO Markdown tables.
            - **Language**: ${locale.displayLanguage}
            
            **Structure:**
            1. **Executive Summary**: A high-level verdict. Is the plan "Solid", "Fragile", or "Optimized"?
            2. **Detailed Analysis**:
               - **Sustainability**: Savings, Spending, and Legacy.
               - **Risk & Stability**: explicitly mention **Standard Deviation** and **Stability Index**.
            3. **Optimization Opportunity**: Compare current vs potential (from Analyst). Highlight the specific "Free Lunch" (gain without pain) if any.
            ${if (isComparing) "4. **Profile Comparison**: Clear recommendation on which profile to choose." else ""}
            ${if (isComparing) "5. **Actionable Recommendations**: 3 specific steps to improve." else "4. **Actionable Recommendations**: 3 specific steps to improve."}
            
            **Length**: Approx ${if (isComparing) "400" else "300"} words. Be thorough.
        """.trimIndent()
    }
}
