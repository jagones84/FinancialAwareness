// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.daysurpopt.R
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.OptimizationMode
import com.example.daysurpopt.domain.ParetoFrontResult
import com.example.daysurpopt.domain.ParetoPoint
import com.example.daysurpopt.logic.ChartMarkerBuilder
import com.example.daysurpopt.logic.ParetoChartModel
import com.example.daysurpopt.logic.ParetoChartModelBuilder
import com.example.daysurpopt.ui.theme.ChartP1Hex
import com.example.daysurpopt.ui.theme.ChartP2Hex
import com.example.daysurpopt.ui.theme.MutedText

internal fun chartSliderValueForWeight(weight: Double): Float {
    return weight.coerceIn(0.0, 1.0).toFloat()
}

internal fun chartWeightFromSliderValue(sliderValue: Float): Double {
    return sliderValue.toDouble().coerceIn(0.0, 1.0)
}

/**
 * Screen that displays 3D/2D charts for parameter sensitivity analysis.
 *
 * @param viewModel The shared [FinancialViewModel] containing simulation results and compare state.
 * @param chartsViewModel The specific [ChartsViewModel] for this screen's UI state and grid computation.
 * @param onBack Callback invoked when the user navigates back.
 */
@Composable
fun ChartsScreen(
    viewModel: FinancialViewModel,
    chartsViewModel: ChartsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val inputsSnapshot = viewModel.inputs
    val gaUiSnapshot = viewModel.gaUI
    val expensesSnapshot = viewModel.specificExpenses
    val isOptimizing = viewModel.optimizing
    val optimizationMode = viewModel.optimizationMode
    val paretoFrontResult = viewModel.paretoFrontResult
    val selectedParetoPoint = viewModel.selectedParetoPoint
    val appliedParetoSnapshot = viewModel.appliedParetoSnapshot
    
    // Compare Mode Data
    val isComparing = viewModel.compareState.isComparing
    val profile1Name = viewModel.compareState.profile1Name
    val profile2Name = viewModel.compareState.profile2Name
    val profile2Inputs = viewModel.profile2Inputs
    val profile2Expenses = viewModel.profile2Expenses
    val profile2Surplus = viewModel.profile2SurplusData

    // Trigger grid calculation when inputs change
    LaunchedEffect(inputsSnapshot, gaUiSnapshot, expensesSnapshot, isComparing, profile2Inputs, profile2Expenses, profile2Surplus) {
        chartsViewModel.refreshGrids(
            inputs = inputsSnapshot,
            gaConfigUI = gaUiSnapshot, // Updated parameter name
            expenses = expensesSnapshot,
            surplusData = viewModel.surplusData, // Assuming it's available in FinancialViewModel
            isComparing = isComparing,
            profile2Inputs = profile2Inputs,
            profile2Expenses = profile2Expenses,
            profile2Surplus = profile2Surplus
        )
    }

    // Construct Markers
    val labelCurrentInputs = stringResource(R.string.chart_marker_current_inputs)
    val labelTrueScalar = stringResource(R.string.chart_marker_true_scalar)
    val labelParetoCompromise = stringResource(R.string.chart_marker_pareto_knee)
    val labelParetoReference = stringResource(R.string.chart_marker_pareto_reference)
    val labelP2Optimal = stringResource(R.string.chart_p2_optimal)
    val labelP2OnP1 = stringResource(R.string.chart_p2_on_p1)

    val baseP1P2Markers = remember(
        inputsSnapshot,
        chartsViewModel.optimalObjW,
        viewModel.lastTrueScalarSnapshot,
        viewModel.lastParetoCompromiseSnapshot,
        viewModel.lastParetoReferenceSnapshot,
        labelCurrentInputs,
        labelTrueScalar,
        labelParetoCompromise,
        labelParetoReference
    ) {
        ChartMarkerBuilder.buildP1P2Markers(
            inputs = inputsSnapshot,
            currentObjective = chartsViewModel.optimalObjW,
            lastTrueScalar = viewModel.lastTrueScalarSnapshot,
            lastParetoCompromise = viewModel.lastParetoCompromiseSnapshot,
            lastParetoReference = viewModel.lastParetoReferenceSnapshot,
            currentLabel = labelCurrentInputs,
            trueScalarLabel = labelTrueScalar,
            paretoCompromiseLabel = labelParetoCompromise,
            paretoReferenceLabel = labelParetoReference
        )
    }

    val p1p2Markers = remember(
        baseP1P2Markers,
        isComparing,
        profile2Inputs,
        chartsViewModel.optimalObjW_2,
        chartsViewModel.optimalObjP2OnP1,
        labelP2Optimal,
        labelP2OnP1
    ) {
        val list = baseP1P2Markers.toMutableList()
        if (isComparing && profile2Inputs != null) {
            list.add(mapOf(
                "x" to listOf(profile2Inputs.p1SavingRatioSurplus),
                "y" to listOf(profile2Inputs.p2EtaFineRisparmioNoCapitale),
                "z" to listOf(chartsViewModel.optimalObjW_2),
                "name" to labelP2Optimal,
                "color" to ChartP2Hex
            ))
            list.add(mapOf(
                "x" to listOf(profile2Inputs.p1SavingRatioSurplus),
                "y" to listOf(profile2Inputs.p2EtaFineRisparmioNoCapitale),
                "z" to listOf(chartsViewModel.optimalObjP2OnP1),
                "name" to labelP2OnP1,
                "color" to "#FFA500" // Orange
            ))
        }
        list
    }

    val baseP3P4Markers = remember(
        inputsSnapshot,
        chartsViewModel.optimalObjW,
        viewModel.lastTrueScalarSnapshot,
        viewModel.lastParetoCompromiseSnapshot,
        viewModel.lastParetoReferenceSnapshot,
        labelCurrentInputs,
        labelTrueScalar,
        labelParetoCompromise,
        labelParetoReference
    ) {
        ChartMarkerBuilder.buildP3P4Markers(
            inputs = inputsSnapshot,
            currentObjective = chartsViewModel.optimalObjW,
            lastTrueScalar = viewModel.lastTrueScalarSnapshot,
            lastParetoCompromise = viewModel.lastParetoCompromiseSnapshot,
            lastParetoReference = viewModel.lastParetoReferenceSnapshot,
            currentLabel = labelCurrentInputs,
            trueScalarLabel = labelTrueScalar,
            paretoCompromiseLabel = labelParetoCompromise,
            paretoReferenceLabel = labelParetoReference
        )
    }

    val p3p4Markers = remember(
        baseP3P4Markers,
        isComparing,
        profile2Inputs,
        chartsViewModel.optimalObjW_2,
        chartsViewModel.optimalObjP1OnP2,
        labelP2Optimal,
        labelP2OnP1
    ) {
        val list = baseP3P4Markers.toMutableList()
        if (isComparing && profile2Inputs != null) {
            list.add(mapOf(
                "x" to listOf(profile2Inputs.p3PercentualeCapitaleDaSpendereAnnualmente),
                "y" to listOf(profile2Inputs.p4EtaAnticipataInizioSpesaCapitale),
                "z" to listOf(chartsViewModel.optimalObjW_2),
                "name" to labelP2Optimal,
                "color" to ChartP2Hex
            ))
            list.add(mapOf(
                "x" to listOf(inputsSnapshot.p3PercentualeCapitaleDaSpendereAnnualmente),
                "y" to listOf(inputsSnapshot.p4EtaAnticipataInizioSpesaCapitale),
                "z" to listOf(chartsViewModel.optimalObjP1OnP2),
                "name" to labelP2OnP1,
                "color" to "#FFA500"
            ))
        }
        list
    }

    val paretoChartModel = remember(
        paretoFrontResult,
        selectedParetoPoint,
        appliedParetoSnapshot
    ) {
        ParetoChartModelBuilder.build(
            points = paretoFrontResult?.points ?: emptyList(),
            referencePoint = paretoFrontResult?.referencePoint,
            appliedPoint = appliedParetoSnapshot?.let { snapshot ->
                paretoFrontResult?.points?.firstOrNull { it.params == snapshot.params }
            },
            selectedPoint = selectedParetoPoint
        )
    }

    ChartsContent(
        p1p2State = chartsViewModel.p1p2State,
        p3p4State = chartsViewModel.p3p4State,
        inputsSnapshot = inputsSnapshot,
        isComparing = isComparing,
        optimizationMode = optimizationMode,
        paretoFrontResult = paretoFrontResult,
        selectedParetoPoint = selectedParetoPoint,
        paretoChartModel = paretoChartModel,
        profile1Name = profile1Name,
        profile2Name = profile2Name,
        isOptimizing = isOptimizing,
        optimalObjW = chartsViewModel.optimalObjW,
        optimalObj0 = chartsViewModel.optimalObj0,
        optimalStabilityIndex = chartsViewModel.optimalStabilityIndex,
        showContours = chartsViewModel.showContours,
        showDeltaView = chartsViewModel.showDeltaView,
        useHeatmap = chartsViewModel.useHeatmap,
        isPerspective = chartsViewModel.isPerspective,
        p1p2Markers = p1p2Markers,
        p3p4Markers = p3p4Markers,
        onBack = onBack,
        onWeightChange = { newW -> viewModel.updateChartWeight(newW) },
        onWeightChangeFinished = { viewModel.onChartWeightChangeFinished() },
        onSelectParetoPoint = { viewModel.selectParetoPoint(it) },
        onApplySelectedParetoPoint = { viewModel.applySelectedParetoPoint() },
        onResetParetoSelection = { viewModel.resetParetoSelectionToReference() },
        onToggleShowContours = { chartsViewModel.toggleShowContours() },
        onToggleShowDeltaView = { chartsViewModel.toggleShowDeltaView() },
        onToggleUseHeatmap = { chartsViewModel.toggleUseHeatmap() },
        onToggleIsPerspective = { chartsViewModel.toggleIsPerspective() }
    )
}

/**
 * Main UI content for the Charts screen. Stateless and suitable for previews.
 *
 * @param p1p2State State for the P1/P2 parameter grid chart.
 * @param p3p4State State for the P3/P4 parameter grid chart.
 * @param inputsSnapshot Current [FinancialInput] for labels and weight control.
 * @param isComparing Whether compare mode is active.
 * @param profile1Name Name of the first profile.
 * @param profile2Name Name of the second profile.
 * @param isOptimizing Whether a parameter optimization is currently running.
 * @param optimalObjW Current optimal objective value (weighted).
 * @param optimalObj0 Current optimal objective value (base).
 * @param optimalStabilityIndex Current stability index.
 * @param showContours Whether to show contour lines on the 3D surface.
 * @param showDeltaView Whether to show the delta (P2 - P1) instead of absolute values.
 * @param useHeatmap Whether to use a 2D heatmap instead of a 3D surface.
 * @param isPerspective Whether to use perspective projection in 3D.
 * @param p1p2Markers List of markers to display on the P1/P2 chart.
 * @param p3p4Markers List of markers to display on the P3/P4 chart.
 * @param onBack Callback for back navigation.
 * @param onWeightChange Callback when the weight slider value changes.
 * @param onWeightChangeFinished Callback when the user finishes sliding the weight slider.
 * @param onToggleShowContours Callback to toggle contour lines.
 * @param onToggleShowDeltaView Callback to toggle between normal and delta view.
 * @param onToggleUseHeatmap Callback to toggle between 2D and 3D.
 * @param onToggleIsPerspective Callback to toggle 3D projection mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsContent(
    p1p2State: ChartUiState,
    p3p4State: ChartUiState,
    inputsSnapshot: FinancialInput,
    isComparing: Boolean,
    optimizationMode: OptimizationMode,
    paretoFrontResult: ParetoFrontResult?,
    selectedParetoPoint: ParetoPoint?,
    paretoChartModel: ParetoChartModel,
    profile1Name: String?,
    profile2Name: String?,
    isOptimizing: Boolean,
    optimalObjW: Double,
    optimalObj0: Double,
    optimalStabilityIndex: Double,
    showContours: Boolean,
    showDeltaView: Boolean,
    useHeatmap: Boolean,
    isPerspective: Boolean,
    p1p2Markers: List<Map<String, Any>>,
    p3p4Markers: List<Map<String, Any>>,
    onBack: () -> Unit,
    onWeightChange: (Double) -> Unit,
    onWeightChangeFinished: () -> Unit,
    onSelectParetoPoint: (ParetoPoint) -> Unit,
    onApplySelectedParetoPoint: () -> Unit,
    onResetParetoSelection: () -> Unit,
    onToggleShowContours: () -> Unit,
    onToggleShowDeltaView: () -> Unit,
    onToggleUseHeatmap: () -> Unit,
    onToggleIsPerspective: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_charts)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isComparing) {
                Text(
                    text = stringResource(R.string.compare_mode_active, profile1Name ?: "P1", profile2Name ?: "P2"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // INTERACTIVE WEIGHT SLIDER BOX
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.charts_interactive_weight, inputsSnapshot.bonusStdWeight),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val currentW = inputsSnapshot.bonusStdWeight
                    val sliderVal = chartSliderValueForWeight(currentW)

                    Slider(
                        value = sliderVal,
                        onValueChange = { 
                            val newW = chartWeightFromSliderValue(it)
                            if (newW != currentW) {
                                onWeightChange(newW)
                            }
                        },
                        onValueChangeFinished = {
                            onWeightChangeFinished()
                        },
                        valueRange = 0f..1f,
                        steps = 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.charts_weight_release_reopt_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    if (isOptimizing) {
                         Row(verticalAlignment = Alignment.CenterVertically) {
                             CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                             Spacer(modifier = Modifier.width(8.dp))
                             Text(stringResource(R.string.charts_optimizing_params), style = MaterialTheme.typography.labelSmall)
                         }
                    } else if (optimalObjW > 0.0) {
                         Column {
                             paretoFrontResult?.let { front ->
                                 if (optimizationMode == OptimizationMode.PARETO_KNEE && front.referencePoint != null) {
                                     Text(
                                         text = stringResource(
                                             R.string.charts_pareto_knee_summary,
                                             front.points.size,
                                             front.referencePoint!!.kneeScore ?: 0.0,
                                             front.referencePoint!!.avgUtility,
                                             front.referencePoint!!.stdDevUtility
                                         ),
                                         style = MaterialTheme.typography.labelMedium,
                                         color = MaterialTheme.colorScheme.primary
                                     )
                                 } else if (optimizationMode == OptimizationMode.PARETO_FRONT && front.points.isNotEmpty()) {
                                     Text(
                                         text = stringResource(
                                             R.string.charts_pareto_front_summary,
                                             front.points.size,
                                             front.idealAvgUtility,
                                             front.idealStdDevUtility
                                         ),
                                         style = MaterialTheme.typography.labelMedium,
                                         color = MaterialTheme.colorScheme.primary
                                     )
                                 }
                             }
                             Text(
                                 text = stringResource(R.string.charts_optimal_f_results, optimalObjW, optimalObj0),
                                 style = MaterialTheme.typography.labelMedium,
                                 color = MaterialTheme.colorScheme.primary
                             )
                             Text(
                                text = stringResource(R.string.charts_stability_index_value, optimalStabilityIndex),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = stringResource(R.string.charts_fobj_formula),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                         }
                    }
                }
            }

            paretoFrontResult?.takeIf { it.points.isNotEmpty() }?.let { front ->
                ParetoChartSection(
                    front = front,
                    chartModel = paretoChartModel,
                    selectedPoint = selectedParetoPoint,
                    onSelectPoint = onSelectParetoPoint,
                    onApplySelected = onApplySelectedParetoPoint,
                    onResetSelection = onResetParetoSelection
                )
            }

            // 2D/3D Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onToggleUseHeatmap,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (useHeatmap) stringResource(R.string.charts_switch_3d) else stringResource(R.string.charts_switch_2d))
                }
                
                if (!useHeatmap) {
                    Button(
                        onClick = onToggleIsPerspective,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isPerspective) stringResource(R.string.charts_toggle_perspective) else stringResource(R.string.charts_toggle_orthographic))
                    }
                }
            }

            Button(
                onClick = onToggleShowContours,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showContours) stringResource(R.string.charts_hide_contours) else stringResource(R.string.charts_show_contours))
            }
            
            if (isComparing) {
                Button(
                    onClick = onToggleShowDeltaView,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                         containerColor = if (showDeltaView) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (showDeltaView) stringResource(R.string.charts_toggle_normal_view) else stringResource(R.string.charts_toggle_delta_view))
                }
            }

            // --- SURFACE 1 (P1/P2) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (showDeltaView && isComparing) stringResource(R.string.charts_delta_p1p2_title) else stringResource(R.string.charts_p1p2_title), 
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(
                        R.string.charts_fixed_values_p3p4,
                        inputsSnapshot.p3PercentualeCapitaleDaSpendereAnnualmente,
                        inputsSnapshot.p4EtaAnticipataInizioSpesaCapitale
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText
                )
            }
            ChartBlock(
                state = p1p2State,
                axisXTitle = stringResource(R.string.charts_axis_p1),
                axisYTitle = stringResource(R.string.charts_axis_p2),
                axisZTitle = stringResource(R.string.charts_objective),
                chartHeight = 500.dp,
                useHeatmap = useHeatmap,
                showContours = showContours,
                isPerspective = isPerspective,
                extraMarkers = p1p2Markers
            )

            // --- SURFACE 2 (P3/P4) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (showDeltaView && isComparing) stringResource(R.string.charts_delta_p3p4_title) else stringResource(R.string.charts_p3p4_title), 
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(
                        R.string.charts_fixed_values_p1p2,
                        inputsSnapshot.p1SavingRatioSurplus,
                        inputsSnapshot.p2EtaFineRisparmioNoCapitale
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText
                )
            }
            ChartBlock(
                state = p3p4State,
                axisXTitle = stringResource(R.string.charts_axis_p3),
                axisYTitle = stringResource(R.string.charts_axis_p4),
                axisZTitle = stringResource(R.string.charts_objective),
                chartHeight = 500.dp,
                useHeatmap = useHeatmap,
                showContours = showContours,
                isPerspective = isPerspective,
                extraMarkers = p3p4Markers
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ParetoChartSection(
    front: ParetoFrontResult,
    chartModel: ParetoChartModel,
    selectedPoint: ParetoPoint?,
    onSelectPoint: (ParetoPoint) -> Unit,
    onApplySelected: () -> Unit,
    onResetSelection: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.pareto_chart_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.pareto_chart_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ParetoScatterPlot(
                chartModel = chartModel,
                onPointSelected = onSelectPoint
            )
            ParetoSelectedPointCard(
                selectedPoint = selectedPoint,
                front = front,
                chartModel = chartModel
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onApplySelected,
                    enabled = selectedPoint != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.pareto_apply_selected))
                }
                OutlinedButton(
                    onClick = onResetSelection,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.pareto_reset_selection))
                }
            }
        }
    }
}

@Composable
private fun ParetoScatterPlot(
    chartModel: ParetoChartModel,
    onPointSelected: (ParetoPoint) -> Unit
) {
    val frontTrace = stringResource(R.string.pareto_trace_front)
    val referenceTrace = stringResource(R.string.pareto_trace_reference)
    val appliedTrace = stringResource(R.string.pareto_trace_applied)
    val selectedTrace = stringResource(R.string.pareto_trace_selected)
    val axisStdDev = stringResource(R.string.pareto_axis_stddev)
    val axisAvgUtility = stringResource(R.string.pareto_axis_avg_utility)
    val specJson = remember(chartModel, frontTrace, referenceTrace, appliedTrace, selectedTrace, axisStdDev, axisAvgUtility) {
        val traces = buildList {
            add(
                PlotlySpecBuilder.LineTraceSpec(
                    name = frontTrace,
                    x = chartModel.basePoints.map { it.x },
                    y = chartModel.basePoints.map { it.y },
                    color = "#90A4AE",
                    pointColor = "#CFD8DC"
                )
            )
            chartModel.referenceMarker?.let {
                add(
                    PlotlySpecBuilder.LineTraceSpec(
                        name = referenceTrace,
                        x = listOf(it.x),
                        y = listOf(it.y),
                        color = "#7C4DFF",
                        pointColor = "#7C4DFF"
                    )
                )
            }
            chartModel.appliedMarker?.let {
                add(
                    PlotlySpecBuilder.LineTraceSpec(
                        name = appliedTrace,
                        x = listOf(it.x),
                        y = listOf(it.y),
                        color = "#FF9800",
                        pointColor = "#FF9800"
                    )
                )
            }
            chartModel.selectedMarker?.let {
                add(
                    PlotlySpecBuilder.LineTraceSpec(
                        name = selectedTrace,
                        x = listOf(it.x),
                        y = listOf(it.y),
                        color = ChartP1Hex,
                        pointColor = ChartP1Hex
                    )
                )
            }
        }

        PlotlySpecBuilder.buildMultiLineJson(
            traces = traces,
            title = "",
            axisXTitle = axisStdDev,
            axisYTitle = axisAvgUtility,
            xRange = null,
            yRange = null,
            fixedRange = false,
            meta = mapOf(
                "pointSelection" to true,
                "displayModeBar" to false
            )
        )
    }

    PlotlyWebView(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
        specJson = specJson,
        isPerspective = false,
        onPointSelected = { traceName, pointIndex ->
            val point = when (traceName) {
                frontTrace -> chartModel.basePoints.getOrNull(pointIndex)?.point
                referenceTrace -> chartModel.referenceMarker?.point
                appliedTrace -> chartModel.appliedMarker?.point
                selectedTrace -> chartModel.selectedMarker?.point
                else -> null
            }
            point?.let(onPointSelected)
        }
    )
}

@Composable
private fun ParetoSelectedPointCard(
    selectedPoint: ParetoPoint?,
    front: ParetoFrontResult,
    chartModel: ParetoChartModel
) {
    val point = selectedPoint ?: front.referencePoint ?: return
    val isReference = point.params == front.referencePoint?.params
    val isApplied = point.params == chartModel.appliedMarker?.point?.params

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(
                R.string.pareto_selected_point_params,
                point.params.p1,
                point.params.p2,
                point.params.p3,
                point.params.p4
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(
                R.string.pareto_selected_point_metrics,
                point.avgUtility,
                point.stdDevUtility,
                point.finalCapital,
                point.legacyGap
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                R.string.pareto_selected_point_flags,
                if (isReference) stringResource(R.string.pareto_flag_yes) else stringResource(R.string.pareto_flag_no),
                if (isApplied) stringResource(R.string.pareto_flag_yes) else stringResource(R.string.pareto_flag_no),
                if (selectedPoint?.params == point.params) stringResource(R.string.pareto_flag_yes) else stringResource(R.string.pareto_flag_no)
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

/**
 * A block containing a chart and its view controls.
 *
 * @param state The UI state of the chart (loading, error, or grid data).
 * @param axisXTitle Title for the X axis.
 * @param axisYTitle Title for the Y axis.
 * @param axisZTitle Title for the Z axis.
 * @param chartHeight The height of the chart container.
 * @param useHeatmap Whether to display as a 2D heatmap.
 * @param showContours Whether to display contour lines.
 * @param isPerspective Whether to use perspective projection in 3D.
 * @param extraMarkers Optional list of markers to overlay on the chart.
 */
@Composable
private fun ChartBlock(
    state: ChartUiState,
    axisXTitle: String,
    axisYTitle: String,
    axisZTitle: String,
    chartHeight: androidx.compose.ui.unit.Dp,
    useHeatmap: Boolean,
    showContours: Boolean,
    isPerspective: Boolean,
    extraMarkers: List<Map<String, Any>> = emptyList()
) {
    var cameraView by remember { mutableStateOf<String?>(null) }
    var cameraClickCount by remember { mutableIntStateOf(0) } // To force update even if same view clicked

    Column {
        if (!useHeatmap) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { cameraView = "XY"; cameraClickCount++ }) {
                    Text("XY", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
                IconButton(onClick = { cameraView = "XZ"; cameraClickCount++ }) {
                    Text("XZ", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
                IconButton(onClick = { cameraView = "YZ"; cameraClickCount++ }) {
                    Text("YZ", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
                IconButton(onClick = { cameraView = "DEFAULT"; cameraClickCount++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset View", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
            contentAlignment = Alignment.Center
        ) {
            if (state.grid != null) {
                SurfaceWebView(
                    modifier = Modifier.fillMaxSize(),
                    grid = state.grid,
                    axisXTitle = axisXTitle,
                    axisYTitle = axisYTitle,
                    axisZTitle = axisZTitle,
                    useHeatmap = useHeatmap,
                    cameraView = cameraView,
                    cameraTrigger = cameraClickCount,
                    showContours = showContours,
                    isPerspective = isPerspective,
                    localized = PlotlySpecBuilder.LocalizedStrings(
                        objective = stringResource(R.string.charts_objective),
                        heatmapCpu = stringResource(R.string.charts_2d_heatmap_cpu),
                        saveImage = stringResource(R.string.charts_save_image),
                        resetScale = stringResource(R.string.charts_reset_scale)
                    ),
                    extraMarkers = extraMarkers
                )
            }

            if (state.error != null && state.grid == null) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (state.isLoading) {
                if (state.grid != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black.copy(alpha = 0.3f)
                    ) {}
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.charts_loading),
                        color = if (state.grid != null) Color.White else MutedText
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChartsContentPreview() {
    ChartsContent(
        p1p2State = ChartUiState(isLoading = false),
        p3p4State = ChartUiState(isLoading = false),
        inputsSnapshot = FinancialInput(),
        isComparing = false,
        optimizationMode = OptimizationMode.TRUE_SCALAR,
        paretoFrontResult = null,
        selectedParetoPoint = null,
        paretoChartModel = ParetoChartModelBuilder.build(emptyList(), null, null, null),
        profile1Name = "Profile 1",
        profile2Name = null,
        isOptimizing = false,
        optimalObjW = 0.5,
        optimalObj0 = 0.8,
        optimalStabilityIndex = 0.9,
        showContours = true,
        showDeltaView = false,
        useHeatmap = false,
        isPerspective = true,
        p1p2Markers = emptyList(),
        p3p4Markers = emptyList(),
        onBack = {},
        onWeightChange = {},
        onWeightChangeFinished = {},
        onSelectParetoPoint = {},
        onApplySelectedParetoPoint = {},
        onResetParetoSelection = {},
        onToggleShowContours = {},
        onToggleShowDeltaView = {},
        onToggleUseHeatmap = {},
        onToggleIsPerspective = {}
    )
}
