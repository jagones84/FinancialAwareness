// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.daysurpopt.R
import com.example.daysurpopt.agent.AgentPrompts
import com.example.daysurpopt.data.*
import com.example.daysurpopt.domain.*
import com.example.daysurpopt.logic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

import android.widget.Toast
import com.example.daysurpopt.logic.PdfExporter
import androidx.core.content.FileProvider
import android.content.Intent

data class OptimizationResult(
    val mode: OptimizationMode,
    val gaFitness: Double,
    val bonusWeight: Double,
    val finalFitness: Double,
    val p1: Double,
    val p2: Int,
    val p3: Double,
    val p4: Int,
    val paretoPointCount: Int = 0,
    val kneeScore: Double? = null,
    val selectedAvgUtility: Double? = null,
    val selectedStdDev: Double? = null
)

internal data class AnalysisUiState(
    val objectiveFunctionValue: Double?,
    val optimizationResult: OptimizationResult?,
    val paretoFrontResult: ParetoFrontResult?,
    val selectedParetoPoint: ParetoPoint?,
    val appliedParetoSnapshot: OptimizationMarkerSnapshot?,
    val lastTrueScalarSnapshot: OptimizationMarkerSnapshot?,
    val lastParetoCompromiseSnapshot: OptimizationMarkerSnapshot?,
    val lastParetoReferenceSnapshot: OptimizationMarkerSnapshot?,
    val simulationResultsCount: Int,
    val sensitivityResultsCount: Int,
    val currentWeight: Double = 0.0,
    val goalSweepResult: GoalSweepResult? = null
)

internal fun applyOptimizationParamsForTest(
    baseInputs: FinancialInput,
    point: ParetoPoint
): FinancialInput {
    val p2 = point.params.p2
    val p4 = maxOf(point.params.p4, p2)
    return baseInputs.copy(
        p1SavingRatioSurplus = point.params.p1,
        p2EtaFineRisparmioNoCapitale = p2,
        p3PercentualeCapitaleDaSpendereAnnualmente = point.params.p3,
        p4EtaAnticipataInizioSpesaCapitale = p4
    )
}

internal fun shouldSyncSelectedParetoPointToReference(
    previousSelection: ParetoPoint?,
    previousReference: ParetoPoint?
): Boolean {
    return previousSelection == null || previousSelection.params == previousReference?.params
}

internal fun shouldSyncAppliedParetoPointToReference(
    previousApplied: OptimizationMarkerSnapshot?,
    previousReference: ParetoPoint?
): Boolean {
    return previousApplied == null || previousApplied.params == previousReference?.params
}

internal fun chartWeightReleaseActionForMode(mode: OptimizationMode): String {
    return when (mode) {
        OptimizationMode.TRUE_SCALAR -> "rerun_scalar"
        OptimizationMode.PARETO_KNEE -> "rerun_knee"
        OptimizationMode.PARETO_FRONT -> "rerun_front"
    }
}

internal fun applyChartWeightUpdateForTest(
    currentInputs: FinancialInput,
    newWeight: Double
): Pair<FinancialInput, FinancialInputUI> {
    val updatedInputs = currentInputs.copy(bonusStdWeight = newWeight)
    return updatedInputs to FinancialInputUI.from(updatedInputs)
}

internal fun clearAnalysisStateForTest(state: AnalysisUiState): AnalysisUiState {
    return state.copy(
        objectiveFunctionValue = null,
        optimizationResult = null,
        paretoFrontResult = null,
        selectedParetoPoint = null,
        appliedParetoSnapshot = null,
        lastTrueScalarSnapshot = null,
        lastParetoCompromiseSnapshot = null,
        lastParetoReferenceSnapshot = null,
        simulationResultsCount = 0,
        sensitivityResultsCount = 0,
        goalSweepResult = null
    )
}

internal fun isGoalSolverInputValid(
    etaAttuale: Int,
    etaMorte: Int,
    stopWorkAge: Int,
    threshold: Double
): Boolean {
    val ageValid = stopWorkAge >= etaAttuale && stopWorkAge < etaMorte
    val thresholdValid = threshold > 0.0 && threshold < 1.0
    return ageValid && thresholdValid
}

/**
 * Builds the comparison context injected into the AI Agent prompts when compare mode
 * is active. Returns null when profile 2 data is unavailable (no comparison marker
 * is then shown to the specialized agents).
 */
internal fun buildComparisonContextForAgent(
    profile1Name: String?,
    profile2Name: String?,
    p1Inputs: FinancialInput,
    p2Inputs: FinancialInput?,
    p2Surplus: SurplusInput?,
    p1AvgUtility: Double?,
    p2AvgUtility: Double?
): String? {
    if (p2Inputs == null) return null

    val p1Label = profile1Name ?: "Profile 1"
    val p2Label = profile2Name ?: "Profile 2"

    return buildString {
        appendLine("**COMPARISON MODE ACTIVE** — the user is comparing two profiles:")
        appendLine("- Profile 1 ($p1Label): age ${p1Inputs.etaAttuale}, retirement ${p1Inputs.etaPensione}, " +
            "death ${p1Inputs.etaMorte}, initial capital ${p1Inputs.capitaleIniziale}, " +
            "legacy ${p1Inputs.soldiDaConservare}, P1 ${p1Inputs.p1SavingRatioSurplus}, " +
            "P2 ${p1Inputs.p2EtaFineRisparmioNoCapitale}, P3 ${p1Inputs.p3PercentualeCapitaleDaSpendereAnnualmente}, " +
            "P4 ${p1Inputs.p4EtaAnticipataInizioSpesaCapitale}" +
            (p1AvgUtility?.let { ", Avg Utility ${"%.4f".format(Locale.US, it)}" } ?: ""))
        appendLine("- Profile 2 ($p2Label): age ${p2Inputs.etaAttuale}, retirement ${p2Inputs.etaPensione}, " +
            "death ${p2Inputs.etaMorte}, initial capital ${p2Inputs.capitaleIniziale}, " +
            "legacy ${p2Inputs.soldiDaConservare}, P1 ${p2Inputs.p1SavingRatioSurplus}, " +
            "P2 ${p2Inputs.p2EtaFineRisparmioNoCapitale}, P3 ${p2Inputs.p3PercentualeCapitaleDaSpendereAnnualmente}, " +
            "P4 ${p2Inputs.p4EtaAnticipataInizioSpesaCapitale}" +
            (p2AvgUtility?.let { ", Avg Utility ${"%.4f".format(Locale.US, it)}" } ?: ""))
        if (p2Surplus != null) {
            appendLine("- Profile 2 monthly net salary: ${p2Surplus.stipendioMensile}, " +
                "monthly net pension: ${p2Surplus.pensioneMensileNetta}, " +
                "rent/mortgage: ${p2Surplus.mutuoAffitto} until age ${p2Surplus.mutuoAffittoFinoEta}")
        }
        appendLine("Compare both profiles and state which one is better and why.")
    }.trimEnd()
}

internal fun defaultOptimizationModeForTest(): OptimizationMode {
    return OptimizationMode.TRUE_SCALAR
}

internal fun optimizationExecutionPathForMode(mode: OptimizationMode): String {
    return when (mode) {
        OptimizationMode.TRUE_SCALAR -> "scalar_optimizer"
        OptimizationMode.PARETO_KNEE -> "pareto_knee"
        OptimizationMode.PARETO_FRONT -> "pareto_front"
    }
}

internal fun optimizationModeDisplayNameForTest(mode: OptimizationMode): String {
    return when (mode) {
        OptimizationMode.TRUE_SCALAR -> "True Scalar"
        OptimizationMode.PARETO_KNEE -> "Pareto Knee"
        OptimizationMode.PARETO_FRONT -> "Pareto Front"
    }
}

/**
 * Scheduled-expenses inputs are live: returns true when the new list actually differs
 * from the current one, so a real change invalidates stale analysis and re-simulates,
 * while a no-op edit (re-typed value, non-numeric text) leaves the analysis untouched.
 */
internal fun expensesListsDiffer(current: List<SpecificExpense>, new: List<SpecificExpense>): Boolean {
    return current != new
}

class FinancialViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    
    // ... (existing code)

    fun exportPdf(activityContext: android.content.Context) {
        viewModelScope.launch {
            // 1. Ensure latest simulation data is ready
            try {
                withContext(Dispatchers.Main) {
                   Toast.makeText(activityContext, activityContext.getString(R.string.pdf_updating), Toast.LENGTH_SHORT).show()
                }
                
                // Trigger calculation and wait
                val (objective, results) = withContext(Dispatchers.Default) {
                    calculateSimulationWithWeight(inputs.copy(), specificExpenses, surplusData)
                }
                objectiveFunctionValue = objective
                simulationResults = results
                objectiveResults = calculateObjectivesFromYears(
                    years = results,
                    bonusStdWeight = inputs.bonusStdWeight,
                    legacyTarget = inputs.soldiDaConservare
                )
                
                // Update comparison if needed
                if (compareState.isComparing && compareState.profile2 != null) {
                    withContext(Dispatchers.Default) {
                        computeComparisonResults(compareState.profile1 ?: FullProfile(inputs, surplusData, specificExpenses, gaUI), compareState.profile2!!)
                    }
                }
            } catch (e: Exception) {
                 e.printStackTrace()
            }
            
            if (simulationResults.isEmpty()) {
                 Toast.makeText(activityContext, activityContext.getString(R.string.pdf_simulation_failed), Toast.LENGTH_SHORT).show()
                 return@launch
            }

            val agentSettings = AgentSettingsRepository.loadSettings(context)
            var aiComment: String? = null

            if (PrivacyConsentRepository.isGranted(context) && agentSettings.apiKey.isNotBlank()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(activityContext, activityContext.getString(R.string.pdf_generating_ai), Toast.LENGTH_LONG).show()
                }
                aiComment = fetchFullAiReport(
                    agentSettings, 
                    inputs, 
                    specificExpenses, 
                    surplusData,
                    isComparing = compareState.isComparing,
                    profile2Inputs = profile2Inputs,
                    deltaObjectives = deltaObjectiveResults
                )
            }

            val file = withContext(Dispatchers.IO) {
                PdfExporter.generateReport(
                    activityContext,
                    inputs,
                    simulationResults,
                    objectiveFunctionValue ?: 0.0,
                    sensitivityResults,
                    aiComment,
                    agentSettings.model,
                    optimizationMode = optimizationMode,
                    paretoFrontResult = paretoFrontResult,
                    compareState = if (compareState.isComparing) compareState else null,
                    profile2Results = if (compareState.isComparing) Triple(
                        profile2ObjectiveResults,
                        profile2SimulationResults,
                        profile2SensitivityResults
                    ) else null,
                    deltaResults = if (compareState.isComparing) Triple(
                        deltaObjectiveResults,
                        deltaSimulationResults,
                        deltaSensitivityResults
                    ) else null
                )
            }
            
            withContext(Dispatchers.Main) {
                if (file != null) {
                    Toast.makeText(activityContext, activityContext.getString(R.string.pdf_exported_success, file.name), Toast.LENGTH_LONG).show()
                    
                    // Auto-open the PDF
                    try {
                        val uri = FileProvider.getUriForFile(
                            activityContext,
                            "${activityContext.packageName}.provider",
                            file
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        activityContext.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(activityContext, activityContext.getString(R.string.pdf_no_viewer), Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(activityContext, activityContext.getString(R.string.pdf_exported_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Stub for benchmark fetching (simulated web search)
    private suspend fun fetchBenchmarks(api: OpenRouterApi, settings: AgentSettings): String {
        // ideally this calls a Search Tool/API. For now, we ask the LLM to provide its internal knowledge 
        // or act as a search agent if it has browsing capabilities.
        val request = OpenRouterRequest(
             model = settings.model,
             messages = listOf(
                 OpenRouterMessage("system", Defaults.OPENROUTER_SAFETY_SYSTEM_PROMPT.trim() + "\n\n" + "You are a Financial Market Researcher. Your goal is to provide CURRENT economic benchmarks."),
                 OpenRouterMessage("user", "Provide a brief summary of current inflation rates (Eurozone), typical S&P500 annual returns (last 20y), and 'safe' withdrawal rates. Format as a concise list.")
             )
        )
        // We use a separate try/catch to not block the flow
        return try {
             api.chatCompletionText(
                 authorization = "Bearer ${settings.apiKey}",
                 request = request
             )
        } catch (e: Exception) {
             context.getString(R.string.market_data_unavailable)
        }
    }

    private suspend fun fetchFullAiReport(
        settings: AgentSettings,
        baseInputs: FinancialInput,
        specificExpenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        isComparing: Boolean = false,
        profile2Inputs: FinancialInput? = null,
        deltaObjectives: DeltaObjectiveResults? = null
    ): String? {
        return try {
            val api = OpenRouterClient.create()
            
            suspend fun callAgent(systemRole: String, prompt: String): String {
                val request = OpenRouterRequest(
                    model = settings.model,
                    messages = listOf(
                        OpenRouterMessage("system", Defaults.OPENROUTER_SAFETY_SYSTEM_PROMPT.trim() + "\n\n" + systemRole),
                        OpenRouterMessage("user", prompt)
                    )
                )
                return api.chatCompletionText(
                    authorization = "Bearer ${settings.apiKey}",
                    request = request
                )
            }
            
            // Step 0: Market Context
            val marketContext = fetchBenchmarks(api, settings)

            coroutineScope {
                 // Agent 1: Sustainability
                val comparisonContext = if (isComparing && profile2Inputs != null && deltaObjectives != null) """
                    
                    **COMPARISON MODE**:
                    You are comparing TWO profiles:
                    - Profile 1 (Baseline): Age ${baseInputs.etaAttuale}, Capital ${baseInputs.capitaleIniziale}, P1 ${baseInputs.p1SavingRatioSurplus}
                    - Profile 2: Age ${profile2Inputs.etaAttuale}, Capital ${profile2Inputs.capitaleIniziale}, P1 ${profile2Inputs.p1SavingRatioSurplus}
                    - Delta Objective: ${String.format(Locale.US, "%.4f", deltaObjectives.deltaFObjW)}
                """ else ""
                
                val commonFinancialContext = """
                    **Financial Context**:
                    - Capital Interest Rate: ${baseInputs.tassoGuadagnoInteresse} (Note: This is the REAL interest rate, net of inflation).
                    - Debt Interest Rate: ${baseInputs.tassoInteresseDebito}.
                    - External Benchmarks: $marketContext
                """.trimIndent()

                val sustainabilityPrompt = AgentPrompts.getSustainabilityPrompt(
                    baseInputs, 
                    commonFinancialContext, 
                    comparisonContext
                )

                // Agent 2: Risk
                val riskPrompt = AgentPrompts.getRiskPrompt(baseInputs, commonFinancialContext, comparisonContext)

                // Agent 3: Analyst (Optimization + Stress Test)
                val analystDeferred = async(Dispatchers.Default) {
                    val gaConfig = GAConfig(
                        popSize = 100, generations = 20, pc = 0.7, pm = 0.08,
                        min = ParamsCandidate(0.0, baseInputs.etaAttuale, 0.0, baseInputs.etaAttuale),
                        max = ParamsCandidate(1.0, baseInputs.etaPensione, 1.0, baseInputs.etaMorte),
                        maximize = true
                    )
                    val paretoResult = ParetoOptimizationLogic.optimizeParetoParameters(
                        baseInputs = baseInputs,
                        config = gaConfig,
                        specificExpenses = specificExpenses,
                        surplusData = surplusData
                    )
                    val referencePoint = paretoResult.points.takeIf { it.isNotEmpty() }?.let {
                        ParetoKneeSelectionLogic.selectKneePoint(it)
                    }
                    val optimizationNarrative = when {
                        paretoResult.points.isEmpty() -> """
                            - Optimization mode: ${optimizationMode.name}
                            - Pareto result: no feasible non-dominated plans found within current bounds.
                        """.trimIndent()

                        optimizationMode == OptimizationMode.PARETO_FRONT -> """
                            - Optimization mode: Pareto Front
                            - Pareto points found: ${paretoResult.points.size}
                            - Ideal Avg Utility: ${String.format(Locale.US, "%.4f", paretoResult.idealAvgUtility)}
                            - Ideal Std Dev: ${String.format(Locale.US, "%.4f", paretoResult.idealStdDevUtility)}
                            - Knee reference: score=${String.format(Locale.US, "%.4f", referencePoint?.kneeScore ?: 0.0)}, P1=${String.format(Locale.US, "%.2f", referencePoint?.params?.p1 ?: baseInputs.p1SavingRatioSurplus)}, P2=${referencePoint?.params?.p2 ?: baseInputs.p2EtaFineRisparmioNoCapitale}, P3=${String.format(Locale.US, "%.2f", referencePoint?.params?.p3 ?: baseInputs.p3PercentualeCapitaleDaSpendereAnnualmente)}, P4=${referencePoint?.params?.p4 ?: baseInputs.p4EtaAnticipataInizioSpesaCapitale}
                        """.trimIndent()

                        else -> """
                            - Optimization mode: Best Compromise
                            - Pareto points found: ${paretoResult.points.size}
                            - Current scalar score: ${String.format(Locale.US, "%.4f", objectiveFunctionValue ?: 0.0)}
                            - Knee score: ${String.format(Locale.US, "%.4f", referencePoint?.kneeScore ?: 0.0)}
                            - Optimized params:
                              P1=${String.format(Locale.US, "%.2f", referencePoint?.params?.p1 ?: baseInputs.p1SavingRatioSurplus)}
                              P2=${referencePoint?.params?.p2 ?: baseInputs.p2EtaFineRisparmioNoCapitale}
                              P3=${String.format(Locale.US, "%.2f", referencePoint?.params?.p3 ?: baseInputs.p3PercentualeCapitaleDaSpendereAnnualmente)}
                              P4=${referencePoint?.params?.p4 ?: baseInputs.p4EtaAnticipataInizioSpesaCapitale}
                        """.trimIndent()
                    }
                    
                    // Run Stress Test (Base: -1% Interest)
                    val stressTestInputs = baseInputs.copy(tassoGuadagnoInteresse = baseInputs.tassoGuadagnoInteresse - 0.01)
                    val (stressObj, stressYears) = calculateSimulationWithWeight(stressTestInputs, specificExpenses, surplusData)
                    val stressTestResult = """
                        - Objective Function: %.4f
                        - Final Capital: %.2f
                        - Avg Utility: %.4f
                    """.format(Locale.US, stressObj, stressYears.lastOrNull()?.capitaleFineAnno ?: 0.0, stressYears.map { it.funzioneUtilita }.average())

                    // Profile 2 Analysis (if comparing)
                    var profile2Analysis = ""
                    if (isComparing && profile2Inputs != null) {
                         // Quick Stress Test for P2
                         val p2StressInputs = profile2Inputs.copy(tassoGuadagnoInteresse = profile2Inputs.tassoGuadagnoInteresse - 0.01)
                         val (p2StressObj, p2StressYears) = calculateSimulationWithWeight(p2StressInputs, specificExpenses, surplusData)
                         
                         profile2Analysis = """
                         
                         **C. Profile 2 Analysis**:
                         - Current P1: ${profile2Inputs.p1SavingRatioSurplus}
                         - Stress Test (-1% Int): Obj=%.4f, EndCap=%.2f
                         """.format(Locale.US, p2StressObj, p2StressYears.lastOrNull()?.capitaleFineAnno ?: 0.0)
                    }

                    """
                    **Analyst Report**:
                    
                    **A. Optimization Analysis (Base Profile)**:
                    $optimizationNarrative
                    
                    **B. Stress Test (Base: Interest Rate -1%)**:
                    $stressTestResult
                    $profile2Analysis
                    """
                }

                val sustainabilityDeferred = async { callAgent("You are the Sustainability Agent.", sustainabilityPrompt) }
                val riskDeferred = async { callAgent("You are the Risk Agent.", riskPrompt) }
                
                val results = awaitAll(sustainabilityDeferred, riskDeferred, analystDeferred)

                // Master Agent
                val masterPrompt = AgentPrompts.getMasterPrompt(
                    marketContext = marketContext,
                    sustainabilityReport = results[0],
                    riskReport = results[1],
                    analystReport = results[2],
                    isComparing = isComparing,
                    locale = Locale(LanguageRepository.loadLanguage(context))
                )

                callAgent("You are the Master Financial Advisor.", masterPrompt)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "AI Analysis Failed: ${e.message}"
        }
    }


    // State
    var inputs by mutableStateOf(FinancialDataRepository.loadInputs(context))
        private set
    var uiInputs by mutableStateOf(FinancialInputUI.from(inputs))
        private set
    var gaUI by mutableStateOf(GaConfigRepository.loadConfig(context))
        private set
    var specificExpenses by mutableStateOf(SpecificExpensesRepository.loadExpenses(context))
        private set
    var surplusData by mutableStateOf(SurplusDataRepository.loadInputs(context))
        private set

    var optimizationMode by mutableStateOf(defaultOptimizationModeForTest())
        private set
    var paretoFrontResult by mutableStateOf<ParetoFrontResult?>(null)
        private set
    var lastTrueScalarSnapshot by mutableStateOf<OptimizationMarkerSnapshot?>(null)
        private set
    var lastParetoCompromiseSnapshot by mutableStateOf<OptimizationMarkerSnapshot?>(null)
        private set
    var lastParetoReferenceSnapshot by mutableStateOf<OptimizationMarkerSnapshot?>(null)
        private set
    var selectedParetoPoint by mutableStateOf<ParetoPoint?>(null)
        private set
    var appliedParetoSnapshot by mutableStateOf<OptimizationMarkerSnapshot?>(null)
        private set

    var objectiveFunctionValue by mutableStateOf<Double?>(null)
    var objectiveResults by mutableStateOf<ObjectiveResults?>(null)
    var simulationResults by mutableStateOf<List<SimulationYear>>(emptyList())
    var sensitivityResults by mutableStateOf<List<SensitivityResult>?>(null)
    var sensitivityMessageResId by mutableStateOf<Int?>(null)

    var optimizing by mutableStateOf(false)
    var optimizationResult by mutableStateOf<OptimizationResult?>(null)

    // Goal Solver State
    var goalSweepResult by mutableStateOf<GoalSweepResult?>(null)
        private set
    var goalSolverRunning by mutableStateOf(false)
        private set

    // Compare Mode State
    var compareState by mutableStateOf(CompareState())
        private set
    
    // Profile 2 computed results (when comparing)
    var profile2ObjectiveResults by mutableStateOf<ObjectiveResults?>(null)
        private set
    var profile2SimulationResults by mutableStateOf<List<SimulationYear>>(emptyList())
        private set
    var profile2SensitivityResults by mutableStateOf<List<SensitivityResult>?>(null)
        private set
    
    // Delta computed values
    var deltaObjectiveResults by mutableStateOf<DeltaObjectiveResults?>(null)
        private set
    var deltaSimulationResults by mutableStateOf<List<DeltaSimulationYear>>(emptyList())
        private set
    var deltaSensitivityResults by mutableStateOf<List<DeltaSensitivityResult>?>(null)
        private set
    
    // Profile 2 raw data (for chart delta computation)
    var profile2Inputs by mutableStateOf<FinancialInput?>(null)
        private set
    var profile2Expenses by mutableStateOf<List<SpecificExpense>>(emptyList())
        private set
    var profile2SurplusData by mutableStateOf<SurplusInput?>(null)
        private set

    // Surplus Summary State
    var surplusLavorativaMedia by mutableStateOf<Double?>(null)
    var surplusPensioneMedia by mutableStateOf<Double?>(null)
    var mutuoFinoEta by mutableStateOf<Int?>(null)

    init {
        val normalizedInputs = inputs.withDefaultAssumptionCurves()
        if (normalizedInputs != inputs) {
            inputs = normalizedInputs
            uiInputs = FinancialInputUI.from(inputs)
            FinancialDataRepository.saveInputs(context, inputs)
        }

        // Load surplus summary
        surplusLavorativaMedia = surplusData.calculateSurplusGiornalieroMedioLavorativa()
        surplusPensioneMedia = surplusData.calculateSurplusGiornalieroMedioPensione()
        mutuoFinoEta = surplusData.mutuoAffittoFinoEta

        // Restore Compare State if exists
        viewModelScope.launch {
            val savedState = CompareStateRepository.loadState(context)
            if (savedState != null) {
                val pName1 = savedState.profile1Name
                val pName2 = savedState.profile2Name
                
                if (pName1 != null && pName2 != null) {
                    val p1 = ProfileRepository.loadProfile(context, pName1)
                    val p2 = ProfileRepository.loadProfile(context, pName2)
                    
                    if (p1 != null && p2 != null) {
                        loadProfile(p1) // Load P1 as active
                        compareState = CompareState(
                            isComparing = true,
                            profile1Name = pName1,
                            profile2Name = pName2,
                            profile1 = p1,
                            profile2 = p2
                        )
                        computeComparisonResults(p1, p2)
                    } else {
                        // One of the profiles was deleted, clear state
                        CompareStateRepository.clearState(context)
                    }
                }
            }
        }
    }

    fun updateInputs(newInputs: FinancialInput) {
        inputs = newInputs
        uiInputs = FinancialInputUI.from(newInputs)
        clearOptimizationArtifacts()
        goalSweepResult = null
        saveInputs()
    }

    fun updateUiInputs(newUiInputs: FinancialInputUI) {
        uiInputs = newUiInputs
    }

    fun updateParsedInput(updater: (FinancialInput) -> FinancialInput) {
        inputs = updater(inputs)
        clearOptimizationArtifacts()
        goalSweepResult = null
        saveInputs()
    }

    fun updateChartWeight(newWeight: Double) {
        val (updatedInputs, updatedUiInputs) = applyChartWeightUpdateForTest(inputs, newWeight)
        inputs = updatedInputs
        uiInputs = updatedUiInputs
        saveInputs()
    }

    fun updateOptimizationMode(mode: OptimizationMode) {
        optimizationMode = mode
        optimizationResult = null
    }

    fun clearAnalysisState() {
        val clearedState = clearAnalysisStateForTest(
            AnalysisUiState(
                objectiveFunctionValue = objectiveFunctionValue,
                optimizationResult = optimizationResult,
                paretoFrontResult = paretoFrontResult,
                selectedParetoPoint = selectedParetoPoint,
                appliedParetoSnapshot = appliedParetoSnapshot,
                lastTrueScalarSnapshot = lastTrueScalarSnapshot,
                lastParetoCompromiseSnapshot = lastParetoCompromiseSnapshot,
                lastParetoReferenceSnapshot = lastParetoReferenceSnapshot,
                simulationResultsCount = simulationResults.size,
                sensitivityResultsCount = sensitivityResults?.size ?: 0,
                currentWeight = inputs.bonusStdWeight,
                goalSweepResult = goalSweepResult
            )
        )

        objectiveFunctionValue = clearedState.objectiveFunctionValue
        optimizationResult = clearedState.optimizationResult
        paretoFrontResult = clearedState.paretoFrontResult
        selectedParetoPoint = clearedState.selectedParetoPoint
        appliedParetoSnapshot = clearedState.appliedParetoSnapshot
        lastTrueScalarSnapshot = clearedState.lastTrueScalarSnapshot
        lastParetoCompromiseSnapshot = clearedState.lastParetoCompromiseSnapshot
        lastParetoReferenceSnapshot = clearedState.lastParetoReferenceSnapshot

        objectiveResults = null
        simulationResults = emptyList()
        sensitivityResults = null
        sensitivityMessageResId = null
        goalSweepResult = null

        profile2ObjectiveResults = null
        profile2SimulationResults = emptyList()
        profile2SensitivityResults = null
        deltaObjectiveResults = null
        deltaSimulationResults = emptyList()
        deltaSensitivityResults = null
    }

    fun runOptimization() {
        viewModelScope.launch {
            optimizing = true
            optimizationResult = null
            try {
                when (optimizationMode) {
                    OptimizationMode.TRUE_SCALAR -> runTrueScalarOptimization()
                    OptimizationMode.PARETO_KNEE -> runParetoKneeOptimization()
                    OptimizationMode.PARETO_FRONT -> runParetoFrontOptimization()
                }
            } finally {
                optimizing = false
            }
        }
    }

    fun onChartWeightChangeFinished() {
        viewModelScope.launch {
            when (optimizationMode) {
                OptimizationMode.TRUE_SCALAR -> runOptimization()
                OptimizationMode.PARETO_KNEE -> runOptimization()
                OptimizationMode.PARETO_FRONT -> runOptimization()
            }
        }
    }

    fun selectParetoPoint(point: ParetoPoint) {
        selectedParetoPoint = point
    }

    fun resetParetoSelectionToReference() {
        selectedParetoPoint = paretoFrontResult?.referencePoint
    }

    fun applySelectedParetoPoint() {
        val point = selectedParetoPoint ?: return
        viewModelScope.launch {
            inputs = applyOptimizationParams(inputs, point.params)
            saveInputs()
            uiInputs = FinancialInputUI.from(inputs)

            val (objective, years, objectives) = evaluateFinancialInput(inputs)
            publishSimulationResults(objective, years, objectives)

            appliedParetoSnapshot = point.toOptimizationMarkerSnapshot(
                mode = OptimizationMode.PARETO_FRONT,
                objectiveValue = objectives.fObjW,
                stabilityIndex = objectives.stabilityIndex,
                weightUsed = inputs.bonusStdWeight
            )
        }
    }

    fun runGoalSolver(stopWorkAge: Int, threshold: Double) {
        viewModelScope.launch {
            goalSolverRunning = true
            goalSweepResult = null
            try {
                goalSweepResult = withContext(Dispatchers.Default) {
                    GoalSolverLogic.solveCapitalVsSavingRatio(
                        baseInputs = inputs,
                        specificExpenses = specificExpenses,
                        surplusData = surplusData,
                        stopWorkAge = stopWorkAge,
                        threshold = threshold
                    )
                }
            } finally {
                goalSolverRunning = false
            }
        }
    }

    fun applyGoalSolverPlan(row: GoalSweepRow) {
        val sweep = goalSweepResult ?: return
        if (row.requiredCapital == null) return
        updateInputs(GoalSolverLogic.buildGoalApplyInputs(inputs, sweep.threshold, sweep.stopWorkAge, row))
        runSimulation()
    }

    fun runSimulation() {
        viewModelScope.launch {
            val (objective, results) = withContext(Dispatchers.Default) {
                calculateSimulationWithWeight(
                    inputs.copy(),
                    specificExpenses,
                    surplusData
                )
            }
            objectiveFunctionValue = objective
            simulationResults = results
            objectiveResults = calculateObjectivesFromYears(
                years = results,
                bonusStdWeight = inputs.bonusStdWeight,
                legacyTarget = inputs.soldiDaConservare
            )
            
            // If in compare mode, recompute deltas using updated Profile 1 results
            if (compareState.isComparing && profile2ObjectiveResults != null) {
                deltaObjectiveResults = objectiveResults?.let { p1Obj ->
                    DeltaCalculator.computeDeltaObjectives(p1Obj, profile2ObjectiveResults!!)
                }
                deltaSimulationResults = DeltaCalculator.computeDeltaSimulation(
                    simulationResults,
                    profile2SimulationResults
                )
            }
        }
    }

    fun runSensitivityAnalysis() {
        viewModelScope.launch {
            sensitivityMessageResId = R.string.analysis_in_progress
            sensitivityResults = null
            
            val results = withContext(Dispatchers.Default) {
                OptimizationLogic.runSensitivityAnalysis(
                    inputs,
                    specificExpenses,
                    surplusData
                )
            }
            sensitivityResults = results
            sensitivityMessageResId = null
            
            // If in compare mode, recompute sensitivity deltas
            if (compareState.isComparing && profile2SensitivityResults != null) {
                deltaSensitivityResults = DeltaCalculator.computeDeltaSensitivity(
                    results,
                    profile2SensitivityResults!!
                )
            }
        }
    }

    fun updateGaConfig(newConfig: GAConfigUI) {
        gaUI = newConfig
        clearOptimizationArtifacts()
        GaConfigRepository.saveConfig(context, newConfig)
        triggerRecalculation()
    }

    fun triggerRecalculation() { // Changed to public
        runSimulation()
    }

    fun updateSpecificExpenses(newExpenses: List<SpecificExpense>) {
        if (!expensesListsDiffer(specificExpenses, newExpenses)) return
        specificExpenses = newExpenses
        clearOptimizationArtifacts()
        goalSweepResult = null
        SpecificExpensesRepository.saveExpenses(context, specificExpenses)
        triggerRecalculation()
    }

    fun updateSurplusData(newData: SurplusInput) {
        surplusData = newData
        clearOptimizationArtifacts()
        SurplusDataRepository.saveInputs(context, newData)
        triggerRecalculation()
    }

    private fun saveInputs() {
        FinancialDataRepository.saveInputs(context, inputs)
    }

    private fun applyOptimizationParams(baseInputs: FinancialInput, params: ParamsCandidate): FinancialInput {
        return applyOptimizationParamsForTest(
            baseInputs = baseInputs,
            point = ParetoPoint(
                params = params,
                avgUtility = 0.0,
                stdDevUtility = 0.0,
                isFeasible = true,
                finalCapital = 0.0,
                legacyGap = 0.0
            )
        )
    }

    private suspend fun evaluateFinancialInput(financialInput: FinancialInput): Triple<Double, List<SimulationYear>, ObjectiveResults> {
        return withContext(Dispatchers.Default) {
            val years = calculateSimulation(financialInput, specificExpenses, surplusData)
            val objectives = calculateObjectivesFromYears(
                years = years,
                bonusStdWeight = financialInput.bonusStdWeight,
                legacyTarget = financialInput.soldiDaConservare
            )
            Triple(objectives.fObjW, years, objectives)
        }
    }

    private fun publishSimulationResults(
        objective: Double,
        years: List<SimulationYear>,
        objectives: ObjectiveResults
    ) {
        objectiveFunctionValue = objective
        simulationResults = years
        objectiveResults = objectives

        if (compareState.isComparing && profile2ObjectiveResults != null) {
            deltaObjectiveResults = DeltaCalculator.computeDeltaObjectives(objectives, profile2ObjectiveResults!!)
            deltaSimulationResults = DeltaCalculator.computeDeltaSimulation(
                years,
                profile2SimulationResults
            )
        }
    }

    private suspend fun runTrueScalarOptimization() {
        val scalarConfig = OptimizationLogic.parseGaConfig(gaUI, inputs).copy(maximize = true)
        val gaResult = withContext(Dispatchers.Default) {
            OptimizationLogic.optimizeParameters(
                baseInputs = inputs,
                config = scalarConfig,
                specificExpenses = specificExpenses,
                surplusData = surplusData,
                initialGuess = ParamsCandidate(
                    inputs.p1SavingRatioSurplus,
                    inputs.p2EtaFineRisparmioNoCapitale,
                    inputs.p3PercentualeCapitaleDaSpendereAnnualmente,
                    inputs.p4EtaAnticipataInizioSpesaCapitale
                )
            )
        }
        val refinedResult = withContext(Dispatchers.Default) {
            OptimizationLogic.refineScalarCandidate(
                baseInputs = inputs,
                start = gaResult.bestParams,
                config = scalarConfig,
                specificExpenses = specificExpenses,
                surplusData = surplusData
            )
        }
        val scalarResult = if (refinedResult.bestFitness >= gaResult.bestFitness) {
            refinedResult
        } else {
            gaResult
        }

        inputs = applyOptimizationParams(inputs, scalarResult.bestParams)
        saveInputs()
        uiInputs = FinancialInputUI.from(inputs)

        val (objective, years, objectives) = evaluateFinancialInput(inputs)
        publishSimulationResults(objective, years, objectives)

        lastTrueScalarSnapshot = OptimizationMarkerSnapshot(
            mode = OptimizationMode.TRUE_SCALAR,
            params = scalarResult.bestParams,
            objectiveValue = objectives.fObjW,
            avgUtility = objectives.avgUtilita,
            stdDevUtility = objectives.stdDev,
            stabilityIndex = objectives.stabilityIndex,
            weightUsed = inputs.bonusStdWeight
        )

        optimizationResult = OptimizationResult(
            mode = OptimizationMode.TRUE_SCALAR,
            gaFitness = scalarResult.bestFitness,
            bonusWeight = inputs.bonusStdWeight,
            finalFitness = objectives.fObjW,
            p1 = scalarResult.bestParams.p1,
            p2 = scalarResult.bestParams.p2,
            p3 = scalarResult.bestParams.p3,
            p4 = scalarResult.bestParams.p4,
            paretoPointCount = 0,
            kneeScore = null,
            selectedAvgUtility = objectives.avgUtilita,
            selectedStdDev = objectives.stdDev
        )
    }

    private suspend fun runParetoKneeOptimization() {
        val cfg = OptimizationLogic.parseGaConfig(gaUI, inputs)
        val front = withContext(Dispatchers.Default) {
            ParetoOptimizationLogic.optimizeParetoParameters(
                baseInputs = inputs,
                config = cfg,
                specificExpenses = specificExpenses,
                surplusData = surplusData
            )
        }
        val selectedKnee = withContext(Dispatchers.Default) {
            front.points.takeIf { it.isNotEmpty() }?.let {
                ParetoKneeSelectionLogic.selectKneePoint(it)
            }
        }

        paretoFrontResult = front.copy(referencePoint = selectedKnee)
        if (front.points.isEmpty() || selectedKnee == null) {
            optimizationResult = OptimizationResult(
                mode = OptimizationMode.PARETO_KNEE,
                gaFitness = 0.0,
                bonusWeight = inputs.bonusStdWeight,
                finalFitness = 0.0,
                p1 = inputs.p1SavingRatioSurplus,
                p2 = inputs.p2EtaFineRisparmioNoCapitale,
                p3 = inputs.p3PercentualeCapitaleDaSpendereAnnualmente,
                p4 = inputs.p4EtaAnticipataInizioSpesaCapitale,
                paretoPointCount = 0
            )
            return
        }

        inputs = applyOptimizationParams(inputs, selectedKnee.params)
        saveInputs()
        uiInputs = FinancialInputUI.from(inputs)

        val (objective, years, objectives) = evaluateFinancialInput(inputs)
        publishSimulationResults(objective, years, objectives)

        lastParetoCompromiseSnapshot = selectedKnee.toOptimizationMarkerSnapshot(
            mode = OptimizationMode.PARETO_KNEE,
            objectiveValue = objectives.fObjW,
            stabilityIndex = objectives.stabilityIndex,
            weightUsed = inputs.bonusStdWeight
        )

        optimizationResult = OptimizationResult(
            mode = OptimizationMode.PARETO_KNEE,
            gaFitness = front.points.size.toDouble(),
            bonusWeight = inputs.bonusStdWeight,
            finalFitness = selectedKnee.kneeScore ?: 0.0,
            p1 = selectedKnee.params.p1,
            p2 = selectedKnee.params.p2,
            p3 = selectedKnee.params.p3,
            p4 = selectedKnee.params.p4,
            paretoPointCount = front.points.size,
            kneeScore = selectedKnee.kneeScore,
            selectedAvgUtility = selectedKnee.avgUtility,
            selectedStdDev = selectedKnee.stdDevUtility
        )
    }

    private suspend fun runParetoFrontOptimization() {
        val cfg = OptimizationLogic.parseGaConfig(gaUI, inputs)
        val front = withContext(Dispatchers.Default) {
            ParetoOptimizationLogic.optimizeParetoParameters(
                baseInputs = inputs,
                config = cfg,
                specificExpenses = specificExpenses,
                surplusData = surplusData
            )
        }
        val referencePoint = withContext(Dispatchers.Default) {
            front.points.takeIf { it.isNotEmpty() }?.let {
                ParetoKneeSelectionLogic.selectKneePoint(it)
            }
        }

        paretoFrontResult = front.copy(referencePoint = referencePoint)
        if (front.points.isEmpty() || referencePoint == null) {
            selectedParetoPoint = null
            appliedParetoSnapshot = null
            lastParetoReferenceSnapshot = null
            optimizationResult = OptimizationResult(
                mode = OptimizationMode.PARETO_FRONT,
                gaFitness = 0.0,
                bonusWeight = inputs.bonusStdWeight,
                finalFitness = 0.0,
                p1 = inputs.p1SavingRatioSurplus,
                p2 = inputs.p2EtaFineRisparmioNoCapitale,
                p3 = inputs.p3PercentualeCapitaleDaSpendereAnnualmente,
                p4 = inputs.p4EtaAnticipataInizioSpesaCapitale,
                paretoPointCount = 0
            )
            return
        }

        inputs = applyOptimizationParams(inputs, referencePoint.params)
        saveInputs()
        uiInputs = FinancialInputUI.from(inputs)

        val (objective, years, objectives) = evaluateFinancialInput(inputs)
        publishSimulationResults(objective, years, objectives)

        val appliedSnapshot = referencePoint.toOptimizationMarkerSnapshot(
            mode = OptimizationMode.PARETO_FRONT,
            objectiveValue = objectives.fObjW,
            stabilityIndex = objectives.stabilityIndex,
            weightUsed = inputs.bonusStdWeight
        )
        selectedParetoPoint = referencePoint
        appliedParetoSnapshot = appliedSnapshot
        lastParetoReferenceSnapshot = appliedSnapshot

        optimizationResult = OptimizationResult(
            mode = OptimizationMode.PARETO_FRONT,
            gaFitness = front.points.size.toDouble(),
            bonusWeight = inputs.bonusStdWeight,
            finalFitness = referencePoint.kneeScore ?: referencePoint.avgUtility,
            p1 = referencePoint.params.p1,
            p2 = referencePoint.params.p2,
            p3 = referencePoint.params.p3,
            p4 = referencePoint.params.p4,
            paretoPointCount = front.points.size,
            kneeScore = referencePoint.kneeScore,
            selectedAvgUtility = referencePoint.avgUtility,
            selectedStdDev = referencePoint.stdDevUtility
        )
    }
    
    fun refreshSurplusData() {
        surplusLavorativaMedia = surplusData.calculateSurplusGiornalieroMedioLavorativa()
        surplusPensioneMedia = surplusData.calculateSurplusGiornalieroMedioPensione()
        mutuoFinoEta = surplusData.mutuoAffittoFinoEta
    }

    fun resetInputs() {
        val defaultFinancialInputs = FinancialInput().withDefaultAssumptionCurves()
        inputs = defaultFinancialInputs
        uiInputs = FinancialInputUI.from(defaultFinancialInputs)
        gaUI = gaUI.copy(
            minRange = "0.0;${defaultFinancialInputs.etaAttuale};0.0;${defaultFinancialInputs.etaAttuale}"
        )
        val defaultSurplus = SurplusInput()
        surplusData = defaultSurplus
        SurplusDataRepository.saveInputs(context, defaultSurplus)
        refreshSurplusData()
        SpecificExpensesRepository.saveExpenses(context, List(10) { SpecificExpense() })
        specificExpenses = SpecificExpensesRepository.loadExpenses(context)
        clearOptimizationArtifacts()
        clearOptimizationSnapshots()
        saveInputs()
        GaConfigRepository.saveConfig(context, gaUI)
    }

    fun loadProfile(profile: FullProfile) {
        val restoredState = ProfileStateMapper.restoreLoadedProfile(profile)
        FinancialDataRepository.saveInputs(context, restoredState.financialInput)
        SurplusDataRepository.saveInputs(context, restoredState.surplusInput)
        SpecificExpensesRepository.saveExpenses(context, restoredState.specificExpenses)
        GaConfigRepository.saveConfig(context, restoredState.gaConfig)

        inputs = restoredState.financialInput
        uiInputs = restoredState.uiInputs
        gaUI = restoredState.gaConfig
        specificExpenses = restoredState.specificExpenses
        surplusData = restoredState.surplusInput
        clearOptimizationArtifacts()
        clearOptimizationSnapshots()
        refreshSurplusData()
    }

    // Profiles Management
    private val _profileNames = mutableStateOf<List<String>>(emptyList())
    val profileNames: State<List<String>> = _profileNames

    fun fetchProfileNames() {
        _profileNames.value = ProfileRepository.getProfileNames(context)
    }

    fun saveProfile(name: String) {
        val fullProfile = ProfileStateMapper.createFullProfile(
            financialInput = inputs,
            surplusInput = surplusData,
            specificExpenses = specificExpenses,
            gaConfig = gaUI
        )
        ProfileRepository.saveProfile(context, name, fullProfile)
        fetchProfileNames()
    }

    fun loadProfileByName(name: String) {
        val profile = ProfileRepository.loadProfile(context, name)
        if (profile != null) {
            loadProfile(profile)
        }
    }

    fun deleteProfile(name: String) {
        ProfileRepository.deleteProfile(context, name)
        fetchProfileNames()
    }

    // ========== Compare Mode Functions ==========

    /**
     * Enter compare mode with two profiles.
     * Profile 1 values become the current inputs (read-only).
     * Profile 2 is used for delta computation.
     */
    fun enterCompareMode(profile1Name: String, profile2Name: String) {
        val p1 = ProfileRepository.loadProfile(context, profile1Name)
        val p2 = ProfileRepository.loadProfile(context, profile2Name)
        
        if (p1 == null || p2 == null) {
            return // Invalid profiles
        }

        // Load Profile 1 as the display baseline
        loadProfile(p1)

        val newState = CompareState(
            isComparing = true,
            profile1Name = profile1Name,
            profile2Name = profile2Name,
            profile1 = p1,
            profile2 = p2
        )
        compareState = newState
        CompareStateRepository.saveState(context, newState)

        // Compute Profile 2 results and deltas
        viewModelScope.launch {
            computeComparisonResults(p1, p2)
        }
    }

    /**
     * Exit compare mode and clear all comparison data.
     */
    fun exitCompareMode() {
        compareState = CompareState()
        CompareStateRepository.clearState(context)
        profile2ObjectiveResults = null
        profile2SimulationResults = emptyList()
        profile2SensitivityResults = null
        profile2Inputs = null
        profile2Expenses = emptyList()
        profile2SurplusData = null
        deltaObjectiveResults = null
        deltaSimulationResults = emptyList()
        deltaSensitivityResults = null
        clearOptimizationArtifacts()
    }

    /**
     * Compute simulation/sensitivity for both profiles and their deltas.
     */
    private suspend fun computeComparisonResults(p1: FullProfile, p2: FullProfile) {
        withContext(Dispatchers.Default) {
            // Profile 1 results (already computed when loadProfile was called, but we recompute for consistency)
            val p1Inputs = p1.financialInput.withDefaultAssumptionCurves()
            val p1Surplus = p1.surplusInput
            val p1Simulation = calculateSimulation(p1Inputs, p1.specificExpenses, p1Surplus)
            val p1Objectives = calculateObjectivesFromYears(
                years = p1Simulation,
                bonusStdWeight = p1Inputs.bonusStdWeight,
                legacyTarget = p1Inputs.soldiDaConservare
            )
            val p1Sensitivity = OptimizationLogic.runSensitivityAnalysis(p1Inputs, p1.specificExpenses, p1Surplus)

            // Profile 2 results
            val p2Inputs = p2.financialInput.withDefaultAssumptionCurves()
            val p2Surplus = p2.surplusInput
            val p2Simulation = calculateSimulation(p2Inputs, p2.specificExpenses, p2Surplus)
            val p2Objectives = calculateObjectivesFromYears(
                years = p2Simulation,
                bonusStdWeight = p2Inputs.bonusStdWeight,
                legacyTarget = p2Inputs.soldiDaConservare
            )
            val p2Sensitivity = OptimizationLogic.runSensitivityAnalysis(p2Inputs, p2.specificExpenses, p2Surplus)

            // Compute deltas (Profile 2 - Profile 1)
            val deltaObj = DeltaCalculator.computeDeltaObjectives(p1Objectives, p2Objectives)
            val deltaSim = DeltaCalculator.computeDeltaSimulation(p1Simulation, p2Simulation)
            val deltaSens = DeltaCalculator.computeDeltaSensitivity(p1Sensitivity, p2Sensitivity)

            withContext(Dispatchers.Main) {
                // Update Profile 1 results (display)
                objectiveResults = p1Objectives
                simulationResults = p1Simulation
                sensitivityResults = p1Sensitivity
                objectiveFunctionValue = p1Objectives.fObjW

                // Store Profile 2 results
                profile2ObjectiveResults = p2Objectives
                profile2SimulationResults = p2Simulation
                profile2SensitivityResults = p2Sensitivity
                profile2Inputs = p2Inputs
                profile2Expenses = p2.specificExpenses
                profile2SurplusData = p2Surplus

                // Store deltas
                deltaObjectiveResults = deltaObj
                deltaSimulationResults = deltaSim
                deltaSensitivityResults = deltaSens
            }
        }
    }

    private fun clearOptimizationArtifacts() {
        paretoFrontResult = null
        optimizationResult = null
        selectedParetoPoint = null
        appliedParetoSnapshot = null
    }

    private fun clearOptimizationSnapshots() {
        lastTrueScalarSnapshot = null
        lastParetoCompromiseSnapshot = null
        lastParetoReferenceSnapshot = null
    }
}
