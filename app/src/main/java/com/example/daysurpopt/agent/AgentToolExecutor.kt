// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.agent

import com.example.daysurpopt.R
import com.example.daysurpopt.data.SearchRepository
import com.example.daysurpopt.domain.CurvePoint
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.GAConfigUI
import com.example.daysurpopt.domain.GAConfig
import com.example.daysurpopt.domain.ParamsCandidate
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.logic.GoalSolverLogic
import com.example.daysurpopt.logic.OptimizationLogic
import com.example.daysurpopt.logic.ParetoKneeSelectionLogic
import com.example.daysurpopt.logic.ParetoOptimizationLogic
import com.example.daysurpopt.logic.calculateSimulationWithWeight
import com.example.daysurpopt.logic.calculateStandardDeviation
import com.example.daysurpopt.utils.AppDebugLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Executes tools requested by the AI agent (Simulations, Optimization, Web Search, etc.).
 */
object AgentToolExecutor {

    private val commandRegex = Regex("(WEB_SEARCH|RUN_SIMULATION|RUN_OPTIMIZATION|RUN_SENSITIVITY|RUN_MULTI_AGENT_ANALYSIS|RUN_RETIREMENT_SOLVER|GET_TIME|FETCH_PAGE|GET_FINANCIAL_CONTEXT)")

    /**
     * Extracts the tool command name from an LLM response, or null when the response
     * contains no tool command. Used by the chat loop to track which tools already
     * ran in the current turn.
     */
    fun extractCommandName(response: String): String? {
        return commandRegex.find(response)?.value
    }

    /**
     * Extracts ALL tool command names from an LLM response (a single reply may carry
     * several commands, all of which get executed).
     */
    fun extractAllCommandNames(response: String): List<String> {
        return commandRegex.findAll(response).map { it.value }.toList()
    }

    suspend fun checkForToolUse(
        response: String,
        baseInputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        userGaConfig: GAConfigUI? = null,
        comparisonContext: String? = null,
        alreadyExecutedCommands: Set<String> = emptySet(),
        llmRequest: suspend (String) -> String // Callback for Multi-Agent workflow
    ): String? {
        // 1. Identify ALL commands: a single LLM response may legitimately carry several
        // tool commands (e.g. read context, then simulate) and every one must execute.
        val matches = commandRegex.findAll(response).toList()
        if (matches.isEmpty()) return null

        val localExecuted = alreadyExecutedCommands.toMutableSet()
        val outputs = mutableListOf<String>()
        for (match in matches) {
            val command = match.value

            // Re-execution guard: running the same heavy tool twice in one turn wastes
            // several LLM calls and produces conflicting reports; the first output is authoritative.
            if (command in localExecuted) {
                outputs += "**Tool $command was already executed in this turn.** Its full output is above in the conversation. " +
                    "Do not call it again — use that existing output to answer the user now."
                continue
            }

            val jsonParams = extractJsonParams(response, match.range.last + 1)
            if (jsonParams.isNotEmpty()) {
                AppDebugLog.add("Agent", "Extracted Params for $command: $jsonParams")
            }

            val output = try {
                executeCommand(command, jsonParams, baseInputs, specificExpenses, surplusData, userGaConfig, comparisonContext, llmRequest)
            } catch (e: Exception) {
                "Tool execution failed: ${e.message}"
            }
            if (output != null) outputs += output
            localExecuted.add(command)
        }
        return if (outputs.isEmpty()) null else outputs.joinToString("\n\n---\n\n")
    }

    private fun extractJsonParams(response: String, startIndex: Int): String {
        val jsonStartIndex = response.indexOf('{', startIndex)
        if (jsonStartIndex == -1) return ""

        var braceCount = 0
        for (i in jsonStartIndex until response.length) {
            if (response[i] == '{') braceCount++
            else if (response[i] == '}') braceCount--
            if (braceCount == 0) {
                return response.substring(jsonStartIndex, i + 1)
            }
        }
        return ""
    }

    private suspend fun executeCommand(
        command: String,
        jsonParams: String,
        baseInputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        userGaConfig: GAConfigUI?,
        comparisonContext: String?,
        llmRequest: suspend (String) -> String
    ): String? {
        return when (command) {
                "WEB_SEARCH" -> {
                    val query = extractStringParam(jsonParams, "query") ?: return null
                    "**Search Output:**\n" + SearchRepository.performWebSearch(query)
                }
                "FETCH_PAGE" -> {
                    val url = extractStringParam(jsonParams, "url") ?: return null
                    "**Page Content:**\n" + SearchRepository.fetchPageContent(url)
                }
                "GET_TIME" -> {
                    "**System Time:**\n" + SearchRepository.getCurrentTime()
                }
                "GET_FINANCIAL_CONTEXT" -> {
                    val gson = Gson()
                    val effective = baseInputs.withDefaultAssumptionCurves()
                    val contextData = mapOf(
                        "financialInput" to baseInputs,
                        "surplusInput" to surplusData,
                        "specificExpenses" to specificExpenses,
                        "effectiveCurves" to mapOf(
                            "utilityCurve" to effective.utilityCurvePoints,
                            "degradationCurve" to effective.degradationCurvePoints
                        )
                    )
                    "**Current Context:**\n```json\n" + gson.toJson(contextData) + "\n```"
                }
                "RUN_SIMULATION" -> {
                    executeSimulation(jsonParams, baseInputs, specificExpenses, surplusData)
                }
                "RUN_OPTIMIZATION" -> {
                    executeOptimization(jsonParams, baseInputs, specificExpenses, surplusData, userGaConfig)
                }
                "RUN_RETIREMENT_SOLVER" -> {
                    executeGoalSolver(jsonParams, baseInputs, specificExpenses, surplusData)
                }
                "RUN_SENSITIVITY" -> {
                    executeSensitivity(jsonParams, baseInputs, specificExpenses, surplusData)
                }
                "RUN_MULTI_AGENT_ANALYSIS" -> {
                    executeMultiAgentWorkflow(baseInputs, specificExpenses, surplusData, llmRequest, comparisonContext)
                }
                else -> null
            }
    }
    
    private fun extractStringParam(json: String, key: String): String? {
        if (json.isBlank()) return null
        return try {
             val type = object : TypeToken<Map<String, Any>>() {}.type
             val params: Map<String, Any> = Gson().fromJson(json, type)
             params[key] as? String
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun executeSimulation(json: String, baseInputs: FinancialInput, specificExpenses: List<SpecificExpense>, surplusData: SurplusInput): String {
        AppDebugLog.add("Agent", "executeSimulation: $json")
        if (json.isBlank()) return "Missing simulation parameters."
        return try {
             val type = object : TypeToken<Map<String, Any>>() {}.type
             val params: Map<String, Any> = Gson().fromJson(json, type)
             
             val modifiedInputs = applyFinancialOverrides(baseInputs, params)
             val modifiedSurplus = applySurplusOverrides(surplusData, params)
             val modifiedSpecificExpenses = applySpecificExpenseOverrides(specificExpenses, params)
             
             AppDebugLog.add("Agent", "Simulating with: Rate=${modifiedInputs.tassoGuadagnoInteresse}, P1=${modifiedInputs.p1SavingRatioSurplus}, Wage=${modifiedSurplus.stipendioMensile}")
             AppDebugLog.add("Agent", "Base Rate=${baseInputs.tassoGuadagnoInteresse}, Modified Rate=${modifiedInputs.tassoGuadagnoInteresse}")

             val (obj, years) = withContext(Dispatchers.Default) {
                  calculateSimulationWithWeight(modifiedInputs, modifiedSpecificExpenses, modifiedSurplus)
             }
             
             "\n\n**Simulation Result:**\n" +
                     "- Objective Function: %.4f\n".format(obj) +
                     "- Final Capital: %.2f\n".format(years.lastOrNull()?.capitaleFineAnno ?: 0.0) +
                     "- Avg Utility: %.4f".format(years.map { it.funzioneUtilita }.average())
        } catch (e: Exception) {
            AppDebugLog.add("Agent", "Simulation error: ${e.message}")
            "Simulation failed: ${e.message}"
        }
    }

    private suspend fun executeSensitivity(json: String, baseInputs: FinancialInput, specificExpenses: List<SpecificExpense>, surplusData: SurplusInput): String {
        AppDebugLog.add("Agent", "executeSensitivity: $json")
        return try {
            val params: Map<String, Any> = if (json.isBlank()) {
                emptyMap()
            } else {
                val type = object : TypeToken<Map<String, Any>>() {}.type
                Gson().fromJson(json, type)
            }

            val modifiedInputs = applyFinancialOverrides(baseInputs, params)
            val modifiedSurplus = applySurplusOverrides(surplusData, params)
            val modifiedSpecificExpenses = applySpecificExpenseOverrides(specificExpenses, params)

            val results = withContext(Dispatchers.Default) {
                OptimizationLogic.runSensitivityAnalysis(modifiedInputs, modifiedSpecificExpenses, modifiedSurplus)
            }

            if (results.isEmpty()) {
                "\n\n**Sensitivity Analysis (impact on average utility):**\n- No sensitivity data: the base average utility is non-positive."
            } else {
                "\n\n**Sensitivity Analysis (impact on average utility):**\n" + results.joinToString("\n") { res ->
                    val impact = String.format(Locale.US, "%.4f", res.scaledImpact)
                    "- ${sensitivityName(res.nameResId)}: $impact pt / ${sensitivityUnit(res.unitResId)}"
                }
            }
        } catch (e: Exception) {
            AppDebugLog.add("Agent", "Sensitivity error: ${e.message}")
            "Sensitivity analysis failed: ${e.message}"
        }
    }

    private fun sensitivityName(resId: Int): String = when (resId) {
        R.string.sens_p1 -> "P1 Saving Ratio"
        R.string.sens_p2 -> "P2 End Savings Age"
        R.string.sens_p3 -> "P3 Capital Spending Share"
        R.string.sens_p4 -> "P4 Capital Spending Start"
        R.string.sens_inheritance -> "Inheritance"
        R.string.sens_keep -> "Capital to Keep"
        R.string.sens_tfr -> "Net TFR"
        R.string.sens_initial_cap -> "Initial Capital"
        R.string.sens_int_rate -> "Interest Rate"
        R.string.sens_debt_rate -> "Debt Interest Rate"
        R.string.sens_utility_threshold -> "Utility Threshold"
        R.string.sens_max_spending -> "Max Utility Spending"
        R.string.sens_surplus -> "Daily Surplus"
        else -> "Parameter"
    }

    private fun sensitivityUnit(resId: Int): String = when (resId) {
        R.string.unit_pt_10 -> "10%"
        R.string.unit_pt_year -> "year"
        R.string.unit_pt_10k -> "10k€"
        R.string.unit_pt_1pp -> "1pp"
        R.string.unit_pt_001 -> "0.01"
        R.string.unit_pt_100eur -> "100€ month"
        else -> "unit"
    }

    private suspend fun executeOptimization(
        json: String,
        baseInputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        userGaConfig: GAConfigUI? = null
    ): String {
        AppDebugLog.add("Agent", "executeOptimization: $json")
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val params: Map<String, Any> = if (json.isNotBlank()) Gson().fromJson(json, type) else emptyMap()

            val modifiedInputs = applyFinancialOverrides(baseInputs, params)
            val modifiedSurplus = applySurplusOverrides(surplusData, params)
            val modifiedSpecificExpenses = applySpecificExpenseOverrides(specificExpenses, params)

            val gaConfig = buildAgentGaConfig(params, modifiedInputs, userGaConfig)
            val mode = (params["mode"] as? String)?.trim()?.uppercase(Locale.US) ?: "TRUE_SCALAR"

            // 1. Calculate Current Metrics
            val (currentObj, currentYears) = withContext(Dispatchers.Default) {
                calculateSimulationWithWeight(modifiedInputs, modifiedSpecificExpenses, modifiedSurplus)
            }
            val currentUtilities = currentYears.flatMap { year ->
                if (year.monthlyUtilitySamples.isNotEmpty()) year.monthlyUtilitySamples else listOf(year.funzioneUtilita)
            }
            val currentAvg = currentUtilities.average()
            val currentStdDev = com.example.daysurpopt.logic.calculateStandardDeviation(currentUtilities)
            val currentStability = AgentReportFormatter.computeStabilityIndex(currentAvg, currentStdDev)

            // Create initial guess from current parameters to seed the optimization
            // This ensures the optimization never performs worse than the current strategy
            val initialGuess = ParamsCandidate(
                modifiedInputs.p1SavingRatioSurplus,
                modifiedInputs.p2EtaFineRisparmioNoCapitale,
                modifiedInputs.p3PercentualeCapitaleDaSpendereAnnualmente,
                modifiedInputs.p4EtaAnticipataInizioSpesaCapitale
            )

            when (mode) {
                "PARETO_KNEE" -> {
                    val front = withContext(Dispatchers.Default) {
                        ParetoOptimizationLogic.optimizeParetoParameters(
                            modifiedInputs, gaConfig, modifiedSpecificExpenses, modifiedSurplus
                        )
                    }
                    val knee = withContext(Dispatchers.Default) {
                        front.points.takeIf { it.isNotEmpty() }?.let { ParetoKneeSelectionLogic.selectKneePoint(it) }
                    } ?: return "**Optimization Analysis (Pareto Knee Mode):**\n- Front Size: 0 (no feasible non-dominated points found)."

                    formatParetoOutput("Pareto Knee Mode", front.points.size, knee, modifiedInputs)
                }
                "PARETO_FRONT" -> {
                    val front = withContext(Dispatchers.Default) {
                        ParetoOptimizationLogic.optimizeParetoParameters(
                            modifiedInputs, gaConfig, modifiedSpecificExpenses, modifiedSurplus
                        )
                    }
                    val best = front.points.maxByOrNull { it.avgUtility }
                        ?: return "**Optimization Analysis (Pareto Front Mode):**\n- Front Size: 0 (no feasible non-dominated points found)."

                    formatParetoOutput("Pareto Front Mode", front.points.size, best, modifiedInputs)
                }
                else -> executeScalarOptimization(
                    modifiedInputs, gaConfig, modifiedSpecificExpenses, modifiedSurplus,
                    initialGuess, currentObj, currentAvg, currentStdDev, currentStability
                )
            }
        } catch (e: Exception) {
            "Optimization failed: ${e.message}"
        }
    }

    private fun formatParetoOutput(
        modeLabel: String,
        frontSize: Int,
        point: com.example.daysurpopt.domain.ParetoPoint,
        baseInputs: FinancialInput
    ): String {
        val stability = AgentReportFormatter.computeStabilityIndex(point.avgUtility, point.stdDevUtility)
        return """
            **Optimization Analysis ($modeLabel):**

            **1. Key Metrics:**
            - **Front Size**: $frontSize
            - **Selected Point**: Avg Utility ${"%.4f".format(Locale.US, point.avgUtility)}, Std Dev ${"%.4f".format(Locale.US, point.stdDevUtility)}
            - **Stability Score**: ${"%.4f".format(Locale.US, stability)}

            **2. Selected Parameters:**
            - **P1 (Savings Rate)**: ${"%.2f%%".format(point.params.p1 * 100)}
            - **P2 (Savings End Age)**: ${point.params.p2}
            - **P3 (Spending Rate)**: ${"%.2f%%".format(point.params.p3 * 100)}
            - **P4 (Spending Start Age)**: ${point.params.p4}

            **3. Reference Plan (unoptimized):**
            - **P1**: ${"%.2f%%".format(baseInputs.p1SavingRatioSurplus * 100)}, **P2**: ${baseInputs.p2EtaFineRisparmioNoCapitale}, **P3**: ${"%.2f%%".format(baseInputs.p3PercentualeCapitaleDaSpendereAnnualmente * 100)}, **P4**: ${baseInputs.p4EtaAnticipataInizioSpesaCapitale}
        """.trimIndent()
    }

    private suspend fun executeScalarOptimization(
        modifiedInputs: FinancialInput,
        gaConfig: com.example.daysurpopt.domain.GAConfig,
        modifiedSpecificExpenses: List<SpecificExpense>,
        modifiedSurplus: SurplusInput,
        initialGuess: ParamsCandidate,
        currentObj: Double,
        currentAvg: Double,
        currentStdDev: Double,
        currentStability: Double
    ): String {
        val result = withContext(Dispatchers.Default) {
            // 2. GA
            val gaRes = OptimizationLogic.optimizeParameters(modifiedInputs, gaConfig, modifiedSpecificExpenses, modifiedSurplus, initialGuess)
            // 3. Coordinate Search
            OptimizationLogic.coordinateSearch(modifiedInputs, gaRes.bestParams, gaConfig, specificExpenses = modifiedSpecificExpenses, surplusData = modifiedSurplus)
        }

        // 4. Calculate Optimized Metrics (for Stability Index)
        val optInputs = modifiedInputs.copy(
            p1SavingRatioSurplus = result.bestParams.p1,
            p2EtaFineRisparmioNoCapitale = result.bestParams.p2,
            p3PercentualeCapitaleDaSpendereAnnualmente = result.bestParams.p3,
            p4EtaAnticipataInizioSpesaCapitale = result.bestParams.p4
        )
        val (optObj, optYears) = withContext(Dispatchers.Default) {
            calculateSimulationWithWeight(optInputs, modifiedSpecificExpenses, modifiedSurplus)
        }
        val optUtilities = optYears.flatMap { year ->
            if (year.monthlyUtilitySamples.isNotEmpty()) year.monthlyUtilitySamples else listOf(year.funzioneUtilita)
        }
        val optAvg = optUtilities.average()
        val optStdDev = com.example.daysurpopt.logic.calculateStandardDeviation(optUtilities)
        val optStability = AgentReportFormatter.computeStabilityIndex(optAvg, optStdDev)

        val gain = optObj - currentObj

        return """
        **Optimization Analysis (Current vs Optimized):**

        **1. Key Metrics:**
        - **Objective Function**: ${"%.4f".format(Locale.US, currentObj)} -> **${"%.4f".format(Locale.US, optObj)}** (Gain: ${"%+.4f".format(Locale.US, gain)})
        - **Stability Score**: ${"%.4f".format(Locale.US, currentStability)} -> **${"%.4f".format(Locale.US, optStability)}** (Higher is better)
        - **Standard Deviation**: ${"%.4f".format(Locale.US, currentStdDev)} -> **${"%.4f".format(Locale.US, optStdDev)}**

        **2. Optimized Parameters:**
        - **P1 (Savings Rate)**: ${"%.2f%%".format(modifiedInputs.p1SavingRatioSurplus * 100)} -> **${"%.2f%%".format(result.bestParams.p1 * 100)}**
        - **P2 (Savings End Age)**: ${modifiedInputs.p2EtaFineRisparmioNoCapitale} -> **${result.bestParams.p2}**
        - **P3 (Spending Rate)**: ${"%.2f%%".format(modifiedInputs.p3PercentualeCapitaleDaSpendereAnnualmente * 100)} -> **${"%.2f%%".format(result.bestParams.p3 * 100)}**
        - **P4 (Spending Start Age)**: ${modifiedInputs.p4EtaAnticipataInizioSpesaCapitale} -> **${result.bestParams.p4}**

        **Analyst Verdict**: The optimized plan improves the objective score by ${"%.2f%%".format(Locale.US, (gain / currentObj) * 100)}.
        """.trimIndent()
    }

    /**
     * Builds the GA config for agent optimization: defaults to the user's GUI GA config
     * (same popSize/generations/pc/pm/ranges the GUI optimizer would use), with optional
     * per-call JSON overrides for popSize/generations/pc/pm.
     */
    private fun buildAgentGaConfig(
        params: Map<String, Any>,
        inputs: FinancialInput,
        userGaConfig: GAConfigUI?
    ): com.example.daysurpopt.domain.GAConfig {
        val base = userGaConfig?.let { OptimizationLogic.parseGaConfig(it, inputs) }
            ?: com.example.daysurpopt.domain.GAConfig(
                popSize = 100,
                generations = 50,
                pc = 0.7,
                pm = 0.08,
                min = ParamsCandidate(0.0, inputs.etaAttuale, 0.0, inputs.etaAttuale),
                max = ParamsCandidate(1.0, inputs.etaPensione, 1.0, inputs.etaMorte),
                maximize = true
            )

        fun num(key: String): Double? = when (val value = params[key]) {
            is Number -> value.toDouble()
            is String -> value.replace(',', '.').toDoubleOrNull()
            else -> null
        }

        return base.copy(
            popSize = num("popSize")?.toInt()?.coerceAtLeast(2) ?: base.popSize,
            generations = num("generations")?.toInt()?.coerceAtLeast(1) ?: base.generations,
            pc = num("pc")?.coerceIn(0.0, 1.0) ?: base.pc,
            pm = num("pm")?.coerceIn(0.0, 1.0) ?: base.pm
        )
    }
    
    private suspend fun executeGoalSolver(
        json: String,
        baseInputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput
    ): String {
        AppDebugLog.add("Agent", "executeGoalSolver: $json")
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val params: Map<String, Any> = Gson().fromJson(json, type)

            val stopWorkAge = when (val value = params["stopWorkAge"]) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            } ?: return "Retirement study failed: missing integer parameter 'stopWorkAge'."

            val threshold = when (val value = params["happinessThreshold"]) {
                is Number -> value.toDouble()
                is String -> value.replace(',', '.').toDoubleOrNull()
                else -> null
            } ?: return "Retirement study failed: missing number parameter 'happinessThreshold'."

            // Same override semantics as RUN_SIMULATION: the LLM can adjust any input
            // (e.g. zero the pension income for a pure capital-based plan) before solving.
            val modifiedInputs = applyFinancialOverrides(baseInputs, params)
            val modifiedSurplus = applySurplusOverrides(surplusData, params)
            val modifiedExpenses = applySpecificExpenseOverrides(specificExpenses, params)

            val result = withContext(Dispatchers.Default) {
                GoalSolverLogic.solveMinimumInitialCapital(
                    baseInputs = modifiedInputs,
                    specificExpenses = modifiedExpenses,
                    surplusData = modifiedSurplus,
                    stopWorkAge = stopWorkAge,
                    threshold = threshold
                )
            }

            val status = if (result.isFeasible) "Feasible" else "Infeasible"
            val capital = result.requiredCapital?.let { "%.2f".format(Locale.US, it) } ?: "N/A"
            val reason = result.reason?.let { "\n- Reason: $it" } ?: ""

            "\n\n**Anticipated Retirement Study Result:**\n" +
                    "- Required Initial Capital: $capital\n" +
                    "- Stop Work Age: $stopWorkAge\n" +
                    "- Happiness Threshold: ${"%.4f".format(Locale.US, threshold)}\n" +
                    "- Max Achievable Utility: ${"%.4f".format(Locale.US, result.maxAchievableUtility)}\n" +
                    "- Status: $status" + reason
        } catch (e: Exception) {
            AppDebugLog.add("Agent", "Retirement study error: ${e.message}")
            "Retirement study failed: ${e.message}"
        }
    }

    // --- Helper Functions for Parameter Mapping ---
    private fun applyFinancialOverrides(base: FinancialInput, params: Map<String, Any>): FinancialInput {
        var inputs = base.copy()
        
        fun getDouble(key: String): Double? {
            val value = params[key] ?: params[key.lowercase()]
            return when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        }

        fun getInt(key: String): Int? {
            val value = params[key] ?: params[key.lowercase()]
            return when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        }

        getDouble("eredita")?.let { inputs = inputs.copy(eredita = it) }
        getDouble("soldiDaConservare")?.let { inputs = inputs.copy(soldiDaConservare = it) }
        getDouble("tfrNetto")?.let { inputs = inputs.copy(tfrNetto = it) }
        getDouble("tassoGuadagnoInteresse")?.let { inputs = inputs.copy(tassoGuadagnoInteresse = it) }
        getDouble("tassoInteresseDebito")?.let { inputs = inputs.copy(tassoInteresseDebito = it) }
        getDouble("sogliaMinimaFunzioneUtilita")?.let { inputs = inputs.copy(sogliaMinimaFunzioneUtilita = it) }
        getDouble("capitaleIniziale")?.let { inputs = inputs.copy(capitaleIniziale = it) }
        getDouble("valoreSpesaGiornalieraMaxUtilita")?.let { inputs = inputs.copy(valoreSpesaGiornalieraMaxUtilita = it) }
        
        getInt("etaAttuale")?.let { inputs = inputs.copy(etaAttuale = it) }
        getInt("etaPensione")?.let { inputs = inputs.copy(etaPensione = it) }
        getInt("etaRicevimentoEredita")?.let { inputs = inputs.copy(etaRicevimentoEredita = it) }
        getInt("etaMorte")?.let { inputs = inputs.copy(etaMorte = it) }
        
        getDouble("p1SavingRatioSurplus")?.let { inputs = inputs.copy(p1SavingRatioSurplus = it) }
        getInt("p2EtaFineRisparmioNoCapitale")?.let { inputs = inputs.copy(p2EtaFineRisparmioNoCapitale = it) }
        getDouble("p3PercentualeCapitaleDaSpendereAnnualmente")?.let { inputs = inputs.copy(p3PercentualeCapitaleDaSpendereAnnualmente = it) }
        getInt("p4EtaAnticipataInizioSpesaCapitale")?.let { inputs = inputs.copy(p4EtaAnticipataInizioSpesaCapitale = it) }
        getDouble("bonusStdWeight")?.let { inputs = inputs.copy(bonusStdWeight = it) }
        
        // Curves
        val utilityPoints = getCurvePoints(params["utilityCurvePoints"])
        if (utilityPoints != null) inputs = inputs.copy(utilityCurvePoints = utilityPoints)
        
        val degradationPoints = getCurvePoints(params["degradationCurvePoints"])
        if (degradationPoints != null) inputs = inputs.copy(degradationCurvePoints = degradationPoints)
        
        return inputs
    }

    private fun applySurplusOverrides(base: SurplusInput, params: Map<String, Any>): SurplusInput {
        var surplus = base.copy()
        
        fun getDouble(key: String): Double? {
            val value = params[key] ?: params[key.lowercase()]
            return when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        }
        
        fun getInt(key: String): Int? {
            val value = params[key] ?: params[key.lowercase()]
            return when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        }

        // Income
        getDouble("stipendioMensile")?.let { surplus = surplus.copy(stipendioMensile = it) }
        getDouble("premioRisultatoNettoAnnuale")?.let { surplus = surplus.copy(premioRisultatoNettoAnnuale = it) }
        getDouble("tredicesimaQuattordicesimaNetto")?.let { surplus = surplus.copy(tredicesimaQuattordicesimaNetto = it) }
        getDouble("bonusEventualiPersonaliMensile")?.let { surplus = surplus.copy(bonusEventualiPersonaliMensile = it) }
        getInt("bonusEventualiPersonaliMensileFinoEta")?.let { surplus = surplus.copy(bonusEventualiPersonaliMensileFinoEta = it) }
        
        // Pension
        getDouble("pensioneMensileNetta")?.let { surplus = surplus.copy(pensioneMensileNetta = it) }
        getDouble("tredicesimaQuattordicesimaNettoPensione")?.let { surplus = surplus.copy(tredicesimaQuattordicesimaNettoPensione = it) }
        getDouble("bonusEventualiPersonaliPensioneMensile")?.let { surplus = surplus.copy(bonusEventualiPersonaliPensioneMensile = it) }
        getInt("bonusEventualiPersonaliPensioneMensileFinoEta")?.let { surplus = surplus.copy(bonusEventualiPersonaliPensioneMensileFinoEta = it) }
        getDouble("altreEntrateMensiliPensione")?.let { surplus = surplus.copy(altreEntrateMensiliPensione = it) }
        
        // Shared Expenses
        getDouble("mutuoAffitto")?.let { surplus = surplus.copy(mutuoAffitto = it) }
        getInt("mutuoAffittoFinoEta")?.let { surplus = surplus.copy(mutuoAffittoFinoEta = it) }
        
        // Work Expenses
        getDouble("condominioLavorativa")?.let { surplus = surplus.copy(condominioLavorativa = it) }
        getDouble("bolletteLavorativa")?.let { surplus = surplus.copy(bolletteLavorativa = it) }
        getDouble("ciboLavorativa")?.let { surplus = surplus.copy(ciboLavorativa = it) }
        getDouble("veicoliLavorativa")?.let { surplus = surplus.copy(veicoliLavorativa = it) }
        getDouble("palestraLavorativa")?.let { surplus = surplus.copy(palestraLavorativa = it) }
        getDouble("trasportiViaggiLavorativa")?.let { surplus = surplus.copy(trasportiViaggiLavorativa = it) }
        getDouble("saluteLavorativa")?.let { surplus = surplus.copy(saluteLavorativa = it) }
        getDouble("vacanzeLavorativa")?.let { surplus = surplus.copy(vacanzeLavorativa = it) }
        getDouble("shoppingLavorativa")?.let { surplus = surplus.copy(shoppingLavorativa = it) }
        getDouble("altroLavorativa")?.let { surplus = surplus.copy(altroLavorativa = it) }
        
        // Pension Expenses
        getDouble("condominioPensione")?.let { surplus = surplus.copy(condominioPensione = it) }
        getDouble("bollettePensione")?.let { surplus = surplus.copy(bollettePensione = it) }
        getDouble("ciboPensione")?.let { surplus = surplus.copy(ciboPensione = it) }
        getDouble("veicoliPensione")?.let { surplus = surplus.copy(veicoliPensione = it) }
        getDouble("palestraPensione")?.let { surplus = surplus.copy(palestraPensione = it) }
        getDouble("trasportiViaggiPensione")?.let { surplus = surplus.copy(trasportiViaggiPensione = it) }
        getDouble("salutePensione")?.let { surplus = surplus.copy(salutePensione = it) }
        getDouble("vacanzePensione")?.let { surplus = surplus.copy(vacanzePensione = it) }
        getDouble("shoppingPensione")?.let { surplus = surplus.copy(shoppingPensione = it) }
        getDouble("altroPensione")?.let { surplus = surplus.copy(altroPensione = it) }

        return surplus
    }

    private fun applySpecificExpenseOverrides(base: List<SpecificExpense>, params: Map<String, Any>): List<SpecificExpense> {
        val jsonList = params["specificExpenses"] as? List<*> ?: return base
        
        return try {
            jsonList.mapNotNull { item ->
                if (item is Map<*, *>) {
                    val ageVal = item["age"]
                    val age = when (ageVal) {
                        is Number -> ageVal.toInt()
                        is String -> ageVal.toIntOrNull() ?: 0
                        else -> 0
                    }
                    
                    val amountVal = item["amount"]
                    val amount = when (amountVal) {
                        is Number -> amountVal.toDouble()
                        is String -> amountVal.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    
                    val utilVal = item["utilityOffset"]
                    val utilityOffset = when (utilVal) {
                        is Number -> utilVal.toDouble()
                        is String -> utilVal.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    
                    SpecificExpense(age, amount, utilityOffset)
                } else null
            }
        } catch (e: Exception) {
            AppDebugLog.add("Agent", "Error parsing specificExpenses: ${e.message}")
            base
        }
    }
    
    private fun getCurvePoints(param: Any?): List<CurvePoint>? {
        if (param !is List<*>) return null
        return try {
            param.mapNotNull { item ->
                if (item is Map<*, *>) {
                    val xVal = item["x"]
                    val x = when (xVal) {
                        is Number -> xVal.toDouble()
                        is String -> xVal.toDoubleOrNull()
                        else -> null
                    } ?: return@mapNotNull null

                    val yVal = item["y"]
                    val y = when (yVal) {
                        is Number -> yVal.toDouble()
                        is String -> yVal.toDoubleOrNull()
                        else -> null
                    } ?: return@mapNotNull null
                    
                    CurvePoint(x, y)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Builds the shared financial context for the multi-agent workflow. Unlike the raw
     * inputs, this includes REAL engine results (base simulation metrics, monthly surplus,
     * implied saving, actual debt years) plus the parameter semantics, so the specialized
     * agents reason on data instead of hallucinating monetary figures.
     */
    internal suspend fun buildMultiAgentFinancialContext(
        baseInputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        marketContext: String
    ): String {
        val (baseObjective, baseYears) = withContext(Dispatchers.Default) {
            calculateSimulationWithWeight(baseInputs, specificExpenses, surplusData)
        }
        val utilities = baseYears.flatMap { year ->
            if (year.monthlyUtilitySamples.isNotEmpty()) year.monthlyUtilitySamples else listOf(year.funzioneUtilita)
        }
        val avgUtility = utilities.average()
        val stdDev = calculateStandardDeviation(utilities)
        val stability = AgentReportFormatter.computeStabilityIndex(avgUtility, stdDev)
        val finalCapital = baseYears.lastOrNull()?.capitaleFineAnno ?: 0.0

        val monthlyIncome = surplusData.getEntrateMensiliLavorativa()
        val monthlyExpenses = surplusData.getUsciteMensiliLavorativa(true)
        val monthlySurplus = monthlyIncome - monthlyExpenses
        val monthlySaving = monthlySurplus * baseInputs.p1SavingRatioSurplus
        val monthlyConsumption = monthlySurplus - monthlySaving

        val debtAges = baseYears.filter { it.debtAmount > 0.0 }.map { it.eta }
        val debtStatus = if (debtAges.isEmpty()) {
            "No debt occurs in this plan: the debt interest rate parameter is currently inert. " +
                "Do not recommend debt elimination."
        } else {
            "Debt occurs in years: $debtAges — the debt interest rate applies there."
        }

        return """
            **Financial Context**:
            - Capital Interest Rate: ${baseInputs.tassoGuadagnoInteresse} (Note: This is the REAL interest rate, net of inflation).
            - Debt Interest Rate: ${baseInputs.tassoInteresseDebito} (applies only if the simulation reports actual debt — see Debt Status).
            - External Benchmarks: $marketContext

            **P1 Semantics (IMPORTANT)**:
            - P1 is the fraction of the monthly SURPLUS (income minus fixed expenses) that is SAVED into capital.
            - The remaining part of the surplus is consumed (lifestyle spending generating utility).
            - P1 is not a percentage of total income: do not compare it with income-based household savings-rate statistics.

            **Base Simulation Results (computed by the engine — use these, do not estimate)**:
            - Monthly Surplus: ${"%.2f".format(Locale.US, monthlySurplus)} (income ${"%.2f".format(Locale.US, monthlyIncome)} - fixed expenses ${"%.2f".format(Locale.US, monthlyExpenses)})
            - Monthly Saving: ${"%.2f".format(Locale.US, monthlySaving)}; Monthly Consumption: ${"%.2f".format(Locale.US, monthlyConsumption)}
            - Objective Function: ${"%.4f".format(Locale.US, baseObjective)}
            - Avg Utility: ${"%.4f".format(Locale.US, avgUtility)} | Standard Deviation: ${"%.4f".format(Locale.US, stdDev)} | Stability Score: ${"%.4f".format(Locale.US, stability)}
            - Final Capital: ${"%.2f".format(Locale.US, finalCapital)}

            **Debt Status**: $debtStatus
        """.trimIndent()
    }

    private suspend fun executeMultiAgentWorkflow(
        baseInputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        llmRequest: suspend (String) -> String,
        comparisonContext: String? = null
    ): String {
        return try {
            coroutineScope {
                // 1. Fetch Market Context (Simulated or Knowledge-based)
                val marketContextPrompt = """
                    You are a Financial Market Researcher. 
                    Provide a brief summary of current inflation rates (Eurozone), typical S&P500 annual returns (last 20y), and 'safe' withdrawal rates. 
                    Format as a concise list.
                """.trimIndent()
                val marketContextDeferred = async { llmRequest(marketContextPrompt) }
                val marketContext = marketContextDeferred.await()

                val commonFinancialContext = buildMultiAgentFinancialContext(
                    baseInputs, specificExpenses, surplusData, marketContext
                )

                // Agent 1: Sustainability & Growth
                val sustainabilityPrompt = AgentPrompts.getSustainabilityPrompt(baseInputs, commonFinancialContext, comparisonContext ?: "")

                // Agent 2: Risk & Stability
                val riskPrompt = AgentPrompts.getRiskPrompt(baseInputs, commonFinancialContext, comparisonContext ?: "")

                // Agent 3: Analyst & Optimizer (Runs hard calculations)
                val analystDeferred = async {
                    // Run Optimization to find the theoretical "Best Plan"
                    val optimizationResult = executeOptimization("", baseInputs, specificExpenses, surplusData)
                    
                    // Run a stress test simulation (Interest Rate -1%)
                    val stressTestResult = executeSimulation("{\"tassoGuadagnoInteresse\": ${baseInputs.tassoGuadagnoInteresse - 0.01}}", baseInputs, specificExpenses, surplusData)
                    
                    """
                    **Analyst Report**:
                    
                    **A. Optimization Analysis**:
                    $optimizationResult
                    
                    **B. Stress Test (Interest Rate -1%)**:
                    $stressTestResult
                    """
                }

                // 2. Run LLM Agents in Parallel
                val sustainabilityDeferred = async { llmRequest(sustainabilityPrompt) }
                val riskDeferred = async { llmRequest(riskPrompt) }
                
                val results = awaitAll(sustainabilityDeferred, riskDeferred, analystDeferred)

                // 3. Master Agent Integration
                val masterPrompt = AgentPrompts.getMasterPrompt(
                    marketContext = marketContext,
                    sustainabilityReport = results[0],
                    riskReport = results[1],
                    analystReport = results[2],
                    isComparing = comparisonContext != null,
                    locale = java.util.Locale.getDefault()
                )

                val finalReport = llmRequest(masterPrompt)
                
                "**Multi-Agent Analysis Report:**\n\n$finalReport"
            }
        } catch (e: Exception) {
            "Multi-Agent Workflow failed: ${e.message}"
        }
    }
}
