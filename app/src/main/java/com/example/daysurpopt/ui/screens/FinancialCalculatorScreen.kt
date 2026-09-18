// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.daysurpopt.R
import com.example.daysurpopt.data.LanguageRepository
import com.example.daysurpopt.domain.OptimizationMode
import com.example.daysurpopt.domain.ObjectiveResults
import com.example.daysurpopt.domain.ParetoFrontResult
import com.example.daysurpopt.domain.SimulationYear
import com.example.daysurpopt.ui.tables.SimulationResultTable
import com.example.daysurpopt.ui.dialogs.ProfilesDialog
import com.example.daysurpopt.ui.theme.NegativeDelta
import com.example.daysurpopt.ui.theme.PositiveDelta
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialCalculatorScreen(
    navController: NavController,
    viewModel: FinancialViewModel,
    onShowQuickStart: () -> Unit = {}
) {
    val context = LocalContext.current
    var showProfilesDialog by remember { mutableStateOf(false) }

    if (showProfilesDialog) {
        LaunchedEffect(Unit) { viewModel.fetchProfileNames() }
        ProfilesDialog(
            onDismiss = { showProfilesDialog = false },
            onLoadProfile = { name ->
                viewModel.loadProfileByName(name)
                showProfilesDialog = false
            },
            onSaveProfile = { name ->
                viewModel.saveProfile(name)
                showProfilesDialog = false
            },
            existingProfiles = viewModel.profileNames.value,
            onDeleteProfile = { name -> viewModel.deleteProfile(name) },
            onCompareProfiles = { p1, p2 ->
                viewModel.enterCompareMode(p1, p2)
                showProfilesDialog = false
            },
            isComparing = viewModel.compareState.isComparing,
            onExitCompare = {
                viewModel.exitCompareMode()
            }
        )
    }

    FinancialCalculatorContent(
        isComparing = viewModel.compareState.isComparing,
        profile1Name = viewModel.compareState.profile1Name,
        profile2Name = viewModel.compareState.profile2Name,
        optimizing = viewModel.optimizing,
        optimizationMode = viewModel.optimizationMode,
        objectiveFunctionValue = viewModel.objectiveFunctionValue,
        optimizationResult = viewModel.optimizationResult,
        paretoFrontResult = viewModel.paretoFrontResult,
        objectiveResults = viewModel.objectiveResults,
        deltaObjectiveResults = viewModel.deltaObjectiveResults,
        simulationResults = viewModel.simulationResults,
        profile2SimulationResults = viewModel.profile2SimulationResults,
        sensitivityResults = viewModel.sensitivityResults,
        deltaSensitivityResults = viewModel.deltaSensitivityResults,
        sensitivityMessageResId = viewModel.sensitivityMessageResId,
        inputs = viewModel.inputs,
        onManageProfilesClick = { showProfilesDialog = true },
        onNavigate = { route -> navController.navigate(route) },
        onUpdateOptimizationMode = { mode -> viewModel.updateOptimizationMode(mode) },
        onRunOptimization = { viewModel.runOptimization() },
        onRunSimulation = { viewModel.runSimulation() },
        onRunSensitivityAnalysis = { viewModel.runSensitivityAnalysis() },
        onClearAnalysisState = { viewModel.clearAnalysisState() },
        onResetInputs = { viewModel.resetInputs() },
        onExportPdf = { viewModel.exportPdf(context) },
        onExitCompareMode = { viewModel.exitCompareMode() },
        onLanguageChange = { lang ->
            LanguageRepository.saveLanguage(context, lang)
            (context as? android.app.Activity)?.recreate()
        },
        goalSolverRunning = viewModel.goalSolverRunning,
        goalSweepResult = viewModel.goalSweepResult,
        onRunGoalSolver = { stopAge, threshold -> viewModel.runGoalSolver(stopAge, threshold) },
        onApplyGoalSolver = { row -> viewModel.applyGoalSolverPlan(row) },
        onShowQuickStart = onShowQuickStart
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialCalculatorContent(
    isComparing: Boolean,
    profile1Name: String?,
    profile2Name: String?,
    optimizing: Boolean,
    optimizationMode: OptimizationMode,
    objectiveFunctionValue: Double?,
    optimizationResult: OptimizationResult?,
    paretoFrontResult: ParetoFrontResult?,
    objectiveResults: ObjectiveResults?,
    deltaObjectiveResults: com.example.daysurpopt.domain.DeltaObjectiveResults?,
    simulationResults: List<SimulationYear>,
    profile2SimulationResults: List<SimulationYear>?,
    sensitivityResults: List<com.example.daysurpopt.domain.SensitivityResult>?,
    deltaSensitivityResults: List<com.example.daysurpopt.domain.DeltaSensitivityResult>?,
    sensitivityMessageResId: Int?,
    inputs: com.example.daysurpopt.domain.FinancialInput,
    onManageProfilesClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onUpdateOptimizationMode: (OptimizationMode) -> Unit,
    onRunOptimization: () -> Unit,
    onRunSimulation: () -> Unit,
    onRunSensitivityAnalysis: () -> Unit,
    onClearAnalysisState: () -> Unit,
    onResetInputs: () -> Unit,
    onExportPdf: () -> Unit,
    onExitCompareMode: () -> Unit,
    onLanguageChange: (String) -> Unit,
    goalSolverRunning: Boolean = false,
    goalSweepResult: com.example.daysurpopt.logic.GoalSweepResult? = null,
    onRunGoalSolver: (Int, Double) -> Unit = { _, _ -> },
    onApplyGoalSolver: (com.example.daysurpopt.logic.GoalSweepRow) -> Unit = {},
    onShowQuickStart: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showGoalSolverDialog by remember { mutableStateOf(false) }
    var showEraseConfirmDialog by remember { mutableStateOf(false) }
    val displayedMode = optimizationResult?.mode ?: optimizationMode

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.financial_calculator_title)) },
                actions = {
                    IconButton(onClick = onShowQuickStart) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = stringResource(R.string.quick_start_guide)
                        )
                    }
                    IconButton(onClick = { showLanguageMenu = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.select_language))
                    }
                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("English") },
                            onClick = {
                                onLanguageChange("en")
                                showLanguageMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Italiano") },
                            onClick = {
                                onLanguageChange("it")
                                showLanguageMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Español") },
                            onClick = {
                                onLanguageChange("es")
                                showLanguageMenu = false
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Compare Mode Banner
            if (isComparing) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(
                                R.string.compare_mode_title,
                                profile1Name ?: "",
                                profile2Name ?: ""
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.compare_mode_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onExitCompareMode,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(stringResource(R.string.exit_compare_mode))
                        }
                    }
                }
            }
            
            // Section 0: Profile Management
            MainSectionCard(title = stringResource(R.string.manage_profiles_title)) {
                MenuButton(
                    onClick = onManageProfilesClick,
                    text = stringResource(R.string.manage_profiles)
                )
            }

            // Section 1: Data Input & Setup
            MainSectionCard(title = stringResource(R.string.section_data_input)) {
                MenuButton(
                    onClick = { onNavigate("surplusCalculator") },
                    text = stringResource(R.string.daily_surplus_calculator_title)
                )
                MenuButton(
                    onClick = { onNavigate("userData") },
                    text = stringResource(R.string.user_data_button)
                )
                MenuButton(
                    onClick = { onNavigate("specificExpenses") },
                    text = stringResource(R.string.add_edit_scheduled_expenses)
                )
            }

            // Section 2: Optimization Configuration
            MainSectionCard(title = stringResource(R.string.section_optimization_config)) {
                MenuButton(
                    onClick = { onNavigate("gaConfig") },
                    text = stringResource(R.string.genetic_algorithm_parameters)
                )
                Text(
                    text = stringResource(R.string.optimization_mode_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptimizationModeButton(
                        selected = optimizationMode == OptimizationMode.TRUE_SCALAR,
                        label = stringResource(R.string.optimization_mode_true_scalar),
                        onClick = { onUpdateOptimizationMode(OptimizationMode.TRUE_SCALAR) }
                    )
                    OptimizationModeButton(
                        selected = optimizationMode == OptimizationMode.PARETO_KNEE,
                        label = stringResource(R.string.optimization_mode_pareto_knee),
                        onClick = { onUpdateOptimizationMode(OptimizationMode.PARETO_KNEE) }
                    )
                    OptimizationModeButton(
                        selected = optimizationMode == OptimizationMode.PARETO_FRONT,
                        label = stringResource(R.string.optimization_mode_pareto_front),
                        onClick = { onUpdateOptimizationMode(OptimizationMode.PARETO_FRONT) }
                    )
                }
                Text(
                    text = when (optimizationMode) {
                        OptimizationMode.TRUE_SCALAR -> stringResource(R.string.optimization_mode_true_scalar_desc)
                        OptimizationMode.PARETO_KNEE -> stringResource(R.string.optimization_mode_pareto_knee_desc)
                        OptimizationMode.PARETO_FRONT -> stringResource(R.string.optimization_mode_pareto_front_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Section 3: Analysis & Actions
            MainSectionCard(title = stringResource(R.string.section_analysis_actions)) {

                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onRunOptimization,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !optimizing
                ) {
                    if (optimizing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.optimizing))
                    } else {
                        Text(stringResource(R.string.run_optimization))
                    }
                }

                MenuButton(
                    onClick = { onNavigate("optimizationParams") },
                    text = stringResource(R.string.optimization_parameters_title)
                )

                MenuButton(
                    onClick = { showGoalSolverDialog = true },
                    text = stringResource(R.string.goal_solver_button)
                )

                OutlinedButton(
                    onClick = onRunSimulation,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !optimizing
                ) {
                    Text(stringResource(R.string.calculate_simulation_with_inputs))
                }

                OutlinedButton(
                    onClick = onRunSensitivityAnalysis,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !optimizing
                ) {
                    Text(stringResource(R.string.calculate_parameter_sensitivity))
                }

                OutlinedButton(
                    onClick = { showEraseConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !optimizing
                ) {
                    Text(stringResource(R.string.clear_analysis_state))
                }

                OutlinedButton(
                    onClick = onResetInputs,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !optimizing
                ) {
                    Text(stringResource(R.string.reset_inputs))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Consolidated Results Section
                if (objectiveFunctionValue != null || optimizationResult != null) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isComparing) 
                                MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Title - show delta title in compare mode
                            if (isComparing) {
                                Text(
                                    text = stringResource(R.string.delta_results_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.results),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            optimizationResult?.let { res ->
                                val summaryText = when (res.mode) {
                                    OptimizationMode.TRUE_SCALAR -> stringResource(
                                        R.string.optimization_mode_true_scalar_summary,
                                        res.p1,
                                        res.p2,
                                        res.p3,
                                        res.p4,
                                        res.finalFitness,
                                        res.bonusWeight
                                    )
                                    OptimizationMode.PARETO_KNEE -> stringResource(
                                        R.string.optimization_mode_pareto_knee_summary,
                                        res.paretoPointCount,
                                        res.kneeScore ?: 0.0,
                                        res.p1,
                                        res.p2,
                                        res.p3,
                                        res.p4
                                    )
                                    OptimizationMode.PARETO_FRONT -> stringResource(
                                        R.string.optimization_mode_pareto_summary_applied,
                                        res.paretoPointCount,
                                        res.p1,
                                        res.p2,
                                        res.p3,
                                        res.p4
                                    )
                                }
                                Text(
                                    text = summaryText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (res.mode == OptimizationMode.PARETO_FRONT && paretoFrontResult?.referencePoint != null) {
                                    Text(
                                        text = stringResource(
                                            R.string.pareto_reference_knee,
                                            paretoFrontResult.referencePoint!!.kneeScore ?: 0.0,
                                            paretoFrontResult.referencePoint!!.params.p1,
                                            paretoFrontResult.referencePoint!!.params.p2,
                                            paretoFrontResult.referencePoint!!.params.p3,
                                            paretoFrontResult.referencePoint!!.params.p4
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                    )
                                }
                                paretoFrontResult?.let { front ->
                                    if (front.points.isNotEmpty()) {
                                        Text(
                                            text = stringResource(
                                                R.string.pareto_front_ideal_summary,
                                                front.idealAvgUtility,
                                                front.idealStdDevUtility
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
                            }

                            objectiveResults?.let { results ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.final_objective_function, results.fObjW),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        // Delta for fobj
                                        if (isComparing && deltaObjectiveResults != null) {
                                            val d = deltaObjectiveResults.deltaFObjW
                                            val color = if (d > 0.0001) PositiveDelta else if (d < -0.0001) NegativeDelta else Color.Unspecified
                                            Text(
                                                text = if (d >= 0) stringResource(R.string.delta_format_positive, String.format(Locale.US, "%.4f", d)) else stringResource(R.string.delta_format_negative, String.format(Locale.US, "%.4f", d)),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = color
                                            )
                                        }
                                    }

                                Text(
                                    text = when (displayedMode) {
                                        OptimizationMode.TRUE_SCALAR -> stringResource(R.string.optimization_mode_true_scalar_definition)
                                        OptimizationMode.PARETO_KNEE -> stringResource(R.string.optimization_mode_pareto_knee_definition)
                                        OptimizationMode.PARETO_FRONT -> stringResource(R.string.optimization_mode_pareto_front_definition)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

                                // Average Utility
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = stringResource(R.string.average_utility, 0.0).substringBefore(":"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Row {
                                        Text(
                                            text = String.format(Locale.getDefault(), "%.4f", results.avgUtilita),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        if (isComparing && deltaObjectiveResults != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            val d = deltaObjectiveResults.deltaAvgUtilita
                                            Text(
                                                text = if (d >= 0) stringResource(R.string.delta_val_positive, String.format(Locale.US, "%.4f", d)) else stringResource(R.string.delta_val_negative, String.format(Locale.US, "%.4f", d)),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = if (d >= 0) PositiveDelta else NegativeDelta
                                            )
                                        }
                                    }
                                }

                                // Standard Deviation
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = stringResource(R.string.std_dev, 0.0).substringBefore(":"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Row {
                                        Text(
                                            text = String.format(Locale.getDefault(), "%.4f", results.stdDev),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        if (isComparing && deltaObjectiveResults != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            val d = deltaObjectiveResults.deltaStdDev
                                            Text(
                                                text = if (d >= 0) stringResource(R.string.delta_val_positive, String.format(Locale.US, "%.4f", d)) else stringResource(R.string.delta_val_negative, String.format(Locale.US, "%.4f", d)),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = if (d < -0.0001) PositiveDelta else if (d > 0.0001) NegativeDelta else Color.Gray
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.definition_std_dev),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                // Stability Index
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = stringResource(R.string.charts_stability_index_value, 0.0).substringBefore(":"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Row {
                                        Text(
                                            text = String.format(Locale.getDefault(), "%.4f", results.stabilityIndex),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        if (isComparing && deltaObjectiveResults != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            val d = deltaObjectiveResults.deltaStabilityIndex
                                            Text(
                                                text = if (d >= 0) stringResource(R.string.delta_val_positive, String.format(Locale.US, "%.4f", d)) else stringResource(R.string.delta_val_negative, String.format(Locale.US, "%.4f", d)),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = if (d < -0.0001) PositiveDelta else if (d > 0.0001) NegativeDelta else Color.Gray
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.definition_stability_index),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = stringResource(R.string.result_feasibility),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = if (results.isFeasible) stringResource(R.string.result_feasible) else stringResource(R.string.result_infeasible),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = if (results.isFeasible) PositiveDelta else NegativeDelta
                                    )
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = stringResource(R.string.result_final_capital),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Row {
                                        Text(
                                            text = String.format(Locale.getDefault(), "%.2f", results.finalCapital),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        if (isComparing && deltaObjectiveResults != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            val d = deltaObjectiveResults.deltaFinalCapital
                                            Text(
                                                text = if (d >= 0) stringResource(R.string.delta_val_positive, String.format(Locale.US, "%.2f", d)) else stringResource(R.string.delta_val_negative, String.format(Locale.US, "%.2f", d)),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = if (d >= 0) PositiveDelta else NegativeDelta
                                            )
                                        }
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = stringResource(R.string.result_legacy_gap),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Row {
                                        Text(
                                            text = String.format(Locale.getDefault(), "%.2f", results.legacyGap),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = if (results.legacyGap >= 0.0) MaterialTheme.colorScheme.onPrimaryContainer else NegativeDelta
                                        )
                                        if (isComparing && deltaObjectiveResults != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            val d = deltaObjectiveResults.deltaLegacyGap
                                            Text(
                                                text = if (d >= 0) stringResource(R.string.delta_val_positive, String.format(Locale.US, "%.2f", d)) else stringResource(R.string.delta_val_negative, String.format(Locale.US, "%.2f", d)),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = if (d >= 0) PositiveDelta else NegativeDelta
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                if (optimizationResult == null) {
                                    Text(
                                        text = stringResource(R.string.results_parameters_used,
                                            inputs.p1SavingRatioSurplus,
                                            inputs.p2EtaFineRisparmioNoCapitale,
                                            inputs.p3PercentualeCapitaleDaSpendereAnnualmente,
                                            inputs.p4EtaAnticipataInizioSpesaCapitale
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))

                                if (simulationResults.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = onExportPdf,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Text(stringResource(R.string.export_pdf))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Simulation Results
            if (simulationResults.isNotEmpty()) {
                MainSectionCard(title = stringResource(R.string.simulation_results_title)) {
                    SimulationResultTable(
                        results = simulationResults,
                        isCompareMode = isComparing,
                        profile2Results = profile2SimulationResults ?: emptyList()
                    )
                }
            }
            
            // Sensitivity Results
            sensitivityResults?.let { results ->
                MainSectionCard(title = stringResource(R.string.calculate_parameter_sensitivity)) {
                    com.example.daysurpopt.ui.tables.SensitivityAnalysisTable(
                        results = results,
                        isCompareMode = isComparing,
                        deltaResults = deltaSensitivityResults ?: emptyList()
                    )
                }
            }
            
            sensitivityMessageResId?.let { resId ->
                Text(
                    text = stringResource(resId),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // App Info Section
            MainSectionCard(title = stringResource(R.string.about_title)) {
                MenuButton(
                    onClick = { onNavigate("about") },
                    text = stringResource(R.string.about_title)
                )
            }

            // Footer disclaimer
            Text(
                text = stringResource(R.string.disclaimer),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline
            )
            
            Text(
                text = stringResource(R.string.copyright_notice),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

    if (showGoalSolverDialog) {
        GoalSolverDialog(
            inputs = inputs,
            running = goalSolverRunning,
            sweep = goalSweepResult,
            onSolve = onRunGoalSolver,
            onApply = { row ->
                onApplyGoalSolver(row)
                showGoalSolverDialog = false
            },
            onDismiss = { showGoalSolverDialog = false }
        )
    }

    if (showEraseConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEraseConfirmDialog = false },
            title = { Text(stringResource(R.string.erase_analysis_confirm_title)) },
            text = {
                Text(
                    text = stringResource(R.string.erase_analysis_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAnalysisState()
                        showEraseConfirmDialog = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.erase),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun GoalSolverDialog(
    inputs: com.example.daysurpopt.domain.FinancialInput,
    running: Boolean,
    sweep: com.example.daysurpopt.logic.GoalSweepResult?,
    onSolve: (Int, Double) -> Unit,
    onApply: (com.example.daysurpopt.logic.GoalSweepRow) -> Unit,
    onDismiss: () -> Unit
) {
    var stopAgeText by remember { mutableStateOf(inputs.etaPensione.toString()) }
    var thresholdText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", inputs.sogliaMinimaFunzioneUtilita)) }
    var selectedRow by remember { mutableStateOf<com.example.daysurpopt.logic.GoalSweepRow?>(null) }

    LaunchedEffect(sweep) {
        selectedRow = sweep?.rows?.firstOrNull { it.isCurrentPlan && it.isFeasible }
            ?: sweep?.rows?.firstOrNull { it.isFeasible }
    }

    val stopAge = stopAgeText.trim().toIntOrNull()
    val threshold = thresholdText.replace(',', '.').toDoubleOrNull()
    val inputValid = stopAge != null && threshold != null &&
        isGoalSolverInputValid(inputs.etaAttuale, inputs.etaMorte, stopAge, threshold)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_solver_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.goal_solver_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = stopAgeText,
                    onValueChange = { stopAgeText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.goal_solver_stop_age)) },
                    isError = stopAge != null && !isGoalSolverInputValid(inputs.etaAttuale, inputs.etaMorte, stopAge, threshold ?: 0.5),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it },
                    label = { Text(stringResource(R.string.goal_solver_threshold)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (stopAge != null && !isGoalSolverInputValid(inputs.etaAttuale, inputs.etaMorte, stopAge, threshold ?: 0.5)) {
                    Text(
                        text = stringResource(R.string.goal_solver_invalid_age),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = { onSolve(stopAge!!, threshold!!) },
                    enabled = inputValid && !running,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (running) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.goal_solver_solving))
                    } else {
                        Text(stringResource(R.string.goal_solver_solve))
                    }
                }
                sweep?.let { sweepResult ->
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.goal_solver_max_utility) + ": " +
                            String.format(java.util.Locale.US, "%.4f", sweepResult.maxAchievableUtility),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (sweepResult.rows.any { it.isFeasible }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            Text(
                                text = stringResource(R.string.goal_solver_table_p1),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(R.string.goal_solver_table_capital),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            sweepResult.rows.forEach { row ->
                                val isSelected = selectedRow == row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = row.isFeasible) { selectedRow = row }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        enabled = row.isFeasible
                                    )
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.0f%%", row.p1 * 100.0) +
                                            if (row.isCurrentPlan) " " + stringResource(R.string.goal_solver_current_row) else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (row.isFeasible) {
                                            String.format(java.util.Locale.US, "%.0f €", row.requiredCapital)
                                        } else {
                                            stringResource(R.string.goal_solver_row_infeasible)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (row.isFeasible) {
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                }
                            }
                        }
                        val chartModel = com.example.daysurpopt.logic.GoalLocusChartModelBuilder.build(
                            sweep = sweepResult,
                            currentP1 = inputs.p1SavingRatioSurplus,
                            currentCapital = inputs.capitaleIniziale
                        )
                        if (chartModel.locusPoints.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.goal_solver_chart_title),
                                style = MaterialTheme.typography.bodySmall
                            )
                            GoalLocusChart(
                                model = chartModel,
                                axisXTitle = stringResource(R.string.goal_solver_chart_axis_p1),
                                axisYTitle = stringResource(R.string.goal_solver_chart_axis_capital),
                                locusLegend = stringResource(R.string.goal_solver_trace_locus),
                                currentLegend = stringResource(R.string.goal_solver_trace_current),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Button(
                            onClick = { selectedRow?.let(onApply) },
                            enabled = selectedRow != null && selectedRow!!.isFeasible,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.goal_solver_apply))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.goal_solver_infeasible),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.goal_solver_close)) }
        }
    )
}

@Composable
private fun GoalLocusChart(
    model: com.example.daysurpopt.logic.GoalLocusChartModel,
    axisXTitle: String,
    axisYTitle: String,
    locusLegend: String,
    currentLegend: String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val locusColor = Color(0xFF00E5FF)
    val pointColor = Color(0xFFFFD600)
    val markerColor = Color(0xFFFF5252)
    val gridColor = Color(0xFF444444)
    val axisColor = Color(0xFFE0E0E0)
    val labelColor = Color(0xFFE0E0E0)

    val yTicks = remember(model) {
        val maxCapital = (model.locusPoints.maxOfOrNull { it.requiredCapital } ?: 0.0)
            .coerceAtLeast(model.currentSimulationMarker.requiredCapital)
        com.example.daysurpopt.logic.GoalLocusChartGeometry.yAxisTicks(maxCapital)
    }
    val labelPaint = remember(density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#E0E0E0")
            textSize = with(density) { 10.sp.toPx() }
        }
    }
    val titlePaint = remember(density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#E0E0E0")
            textSize = with(density) { 10.sp.toPx() }
        }
    }
    val gridPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#444444")
            strokeWidth = 1f
        }
    }
    var probe by remember(model) { mutableStateOf<com.example.daysurpopt.logic.GoalLocusChartPoint?>(null) }
    val probeBgPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#DD1E1E1E")
        }
    }
    val probeTextPaint = remember(density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = with(density) { 10.sp.toPx() }
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 18.dp, height = 3.dp)
                    .background(locusColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(locusLegend, style = MaterialTheme.typography.labelSmall, color = labelColor)
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(markerColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(currentLegend, style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .pointerInput(model, yTicks) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            val pos = changes.firstOrNull()?.position ?: continue
                            val pressed = changes.any { it.pressed }
                            val isHover = changes.any { it.type == PointerType.Mouse }
                            if (!pressed && !isHover) continue
                            val leftPadPx = with(density) { 46.dp.toPx() }
                            val rightPadPx = with(density) { 10.dp.toPx() }
                            val plotWidth = size.width - leftPadPx - rightPadPx
                            if (plotWidth <= 0f) continue
                            val xPct = ((pos.x - leftPadPx) / plotWidth * 100.0).coerceIn(0.0, 100.0)
                            probe = com.example.daysurpopt.logic.GoalLocusChartGeometry.nearestProbePoint(
                                model.locusPoints, model.currentSimulationMarker, xPct
                            )
                            if (pressed) changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            val labelPx = with(density) { 10.sp.toPx() }
            val leftPad = with(density) { 46.dp.toPx() }
            val bottomPad = with(density) { 38.dp.toPx() }
            val topPad = with(density) { 8.dp.toPx() }
            val rightPad = with(density) { 10.dp.toPx() }

            val plotLeft = leftPad
            val plotTop = topPad
            val plotRight = size.width - rightPad
            val plotBottom = size.height - bottomPad
            val plotW = plotRight - plotLeft
            val plotH = plotBottom - plotTop

            val yMax = yTicks.last()
            fun xPx(p1Percent: Double) = plotLeft + (p1Percent / 100.0).toFloat() * plotW
            fun yPx(capital: Double) = plotBottom - (capital / yMax).toFloat() * plotH

            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                yTicks.forEach { tick ->
                    val y = yPx(tick)
                    nc.drawLine(plotLeft, y, plotRight, y, gridPaint)
                    nc.drawText(formatCapital(tick), plotLeft - 6f, y + labelPx / 3f, labelPaint)
                }
                labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                listOf(0, 25, 50, 75, 100).forEach { pct ->
                    val x = xPx(pct.toDouble())
                    nc.drawLine(x, plotTop, x, plotBottom, gridPaint)
                    nc.drawText("$pct%", x, plotBottom + labelPx + 2f, labelPaint)
                }
                titlePaint.textAlign = android.graphics.Paint.Align.CENTER
                val yTitleX = plotLeft - 36f
                val yTitleY = (plotTop + plotBottom) / 2f
                nc.save()
                nc.rotate(-90f, yTitleX, yTitleY)
                nc.drawText(axisYTitle, yTitleX, yTitleY + titlePaint.textSize / 3f, titlePaint)
                nc.restore()
                nc.drawText(axisXTitle, (plotLeft + plotRight) / 2f, size.height - 3f, titlePaint)
            }
            drawLine(axisColor, Offset(plotLeft, plotTop), Offset(plotLeft, plotBottom), strokeWidth = 2f)
            drawLine(axisColor, Offset(plotLeft, plotBottom), Offset(plotRight, plotBottom), strokeWidth = 2f)

            val path = Path()
            model.locusPoints.forEachIndexed { index, p ->
                val o = Offset(xPx(p.p1Percent), yPx(p.requiredCapital))
                if (index == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, locusColor, style = Stroke(width = 3f))
            val dotRadius = with(density) { 2.5.dp.toPx() }
            model.locusPoints.forEach { p ->
                drawCircle(pointColor, radius = dotRadius, center = Offset(xPx(p.p1Percent), yPx(p.requiredCapital)))
            }
            drawCircle(
                markerColor,
                radius = with(density) { 5.5.dp.toPx() },
                center = Offset(
                    xPx(model.currentSimulationMarker.p1Percent),
                    yPx(model.currentSimulationMarker.requiredCapital)
                )
            )
            probe?.let { p ->
                val px = xPx(p.p1Percent)
                val py = yPx(p.requiredCapital)
                drawLine(
                    labelColor,
                    Offset(px, plotTop),
                    Offset(px, plotBottom),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                )
                drawCircle(
                    Color.White,
                    radius = dotRadius + 2f,
                    center = Offset(px, py),
                    style = Stroke(width = 2f)
                )
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    val text = "P1 " + String.format(java.util.Locale.US, "%.0f", p.p1Percent) +
                        "% · " + String.format(java.util.Locale.US, "%.0f", p.requiredCapital) + " €"
                    val textW = probeTextPaint.measureText(text)
                    val boxW = textW + 16f
                    val boxH = labelPx + 12f
                    val bx = (px - boxW / 2f).coerceIn(plotLeft, plotRight - boxW)
                    val by = (py - boxH - 12f).coerceAtLeast(plotTop)
                    nc.drawRoundRect(android.graphics.RectF(bx, by, bx + boxW, by + boxH), 8f, 8f, probeBgPaint)
                    probeTextPaint.textAlign = android.graphics.Paint.Align.LEFT
                    nc.drawText(text, bx + 8f, by + boxH - 7f, probeTextPaint)
                }
            }
        }
    }
}

private fun formatCapital(v: Double): String =
    if (v >= 1000.0) {
        val k = v / 1000.0
        if (k % 1.0 == 0.0) "${k.toInt()}k" else String.format(java.util.Locale.US, "%.1fk", k)
    } else {
        if (v % 1.0 == 0.0) "${v.toInt()}" else String.format(java.util.Locale.US, "%.1f", v)
    }

@Composable
private fun OptimizationModeButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (selected) {
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            ButtonDefaults.outlinedButtonColors()
        }
    ) {
        Text(label)
    }
}

@Composable
private fun MainSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun MenuButton(
    onClick: () -> Unit,
    text: String,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Preview
@Composable
fun FinancialCalculatorPreview() {
    FinancialCalculatorContent(
        isComparing = false,
        profile1Name = "User",
        profile2Name = null,
        optimizing = false,
        optimizationMode = OptimizationMode.TRUE_SCALAR,
        objectiveFunctionValue = 0.5,
        optimizationResult = null,
        paretoFrontResult = null,
        objectiveResults = ObjectiveResults(0.5, 0.4, 0.8, 0.1, 0.6, true, 50000.0, 1000.0),
        deltaObjectiveResults = null,
        simulationResults = emptyList(),
        profile2SimulationResults = null,
        sensitivityResults = null,
        deltaSensitivityResults = null,
        sensitivityMessageResId = null,
        inputs = com.example.daysurpopt.domain.FinancialInput(),
        onManageProfilesClick = {},
        onNavigate = {},
        onUpdateOptimizationMode = {},
        onRunOptimization = {},
        onRunSimulation = {},
        onRunSensitivityAnalysis = {},
        onClearAnalysisState = {},
        onResetInputs = {},
        onExportPdf = {},
        onExitCompareMode = {},
        onLanguageChange = {}
    )
}
