// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.daysurpopt.R
import com.example.daysurpopt.domain.CurvePoint
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.logic.funzioneDegradoPerEta
import com.example.daysurpopt.logic.utilitaDaSpesa
import com.example.daysurpopt.ui.theme.ChartP1Hex
import com.example.daysurpopt.ui.theme.ChartP1PointHex
import com.example.daysurpopt.ui.theme.ChartP2Hex
import com.example.daysurpopt.ui.theme.ChartP2PointHex
import java.util.Locale
import kotlin.math.max

@Composable
fun AssumptionsScreen(
    navController: NavController,
    viewModel: FinancialViewModel
) {
    val inputsSnapshot = viewModel.inputs.withDefaultAssumptionCurves()
    val isComparing = viewModel.compareState.isComparing
    val profile2Inputs = viewModel.profile2Inputs

    AssumptionsContent(
        inputsSnapshot = inputsSnapshot,
        isComparing = isComparing,
        profile2Inputs = profile2Inputs,
        onUpdateParsedInput = { update -> viewModel.updateParsedInput(update) },
        onBack = { navController.popBackStack() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssumptionsContent(
    inputsSnapshot: FinancialInput,
    isComparing: Boolean,
    profile2Inputs: FinancialInput?,
    onUpdateParsedInput: ((FinancialInput) -> FinancialInput) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.assumptions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Button(
                onClick = {
                    onUpdateParsedInput { current ->
                        current
                            .copy(utilityCurvePoints = null, degradationCurvePoints = null)
                            .withDefaultAssumptionCurves()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.assumptions_reset_defaults))
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.utility_function_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.utility_function_desc),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Build Utility Traces
            val utilityCurvePoints = inputsSnapshot.utilityCurvePoints.orEmpty()
            val utilityTraces = mutableListOf<PlotlySpecBuilder.LineTraceSpec>()
            // P1 Trace
            utilityTraces.add(PlotlySpecBuilder.LineTraceSpec(
                name = if (isComparing) "P1" else context.getString(R.string.assumptions_chart_trace_utility),
                x = utilityCurvePoints.map { it.x },
                y = utilityCurvePoints.map { it.y },
                color = "#00E5FF",
                pointColor = "#FFD600"
            ))
            // P2 Trace
            if (isComparing && profile2Inputs != null) {
                val p2Points = profile2Inputs.withDefaultAssumptionCurves().utilityCurvePoints.orEmpty()
                utilityTraces.add(PlotlySpecBuilder.LineTraceSpec(
                    name = "P2",
                    x = p2Points.map { it.x },
                    y = p2Points.map { it.y },
                    color = "#FF4081", // Pinkish/Red for P2
                    pointColor = "#FFFFFF"
                ))
            }

            val utilitySpecJson = remember(utilityCurvePoints, inputsSnapshot.valoreSpesaGiornalieraMaxUtilita, isComparing, profile2Inputs) {
                PlotlySpecBuilder.buildMultiLineJson(
                    traces = utilityTraces,
                    title = context.getString(R.string.assumptions_chart_utility_title),
                    axisXTitle = context.getString(R.string.assumptions_chart_spending_x),
                    axisYTitle = context.getString(R.string.assumptions_chart_utility_y),
                    xRange = 0.0 to max(100.0, inputsSnapshot.valoreSpesaGiornalieraMaxUtilita),
                    yRange = 0.0 to 1.0,
                    fixedRange = true,
                    meta = mapOf(
                        "curveId" to "utility",
                        "draggablePoints" to true, // Note: Dragging might differ for P2? Usually only edit active profile? Logic below updates P1 only.
                        "lockXOrder" to true,
                        "scrollZoom" to false,
                        "doubleClick" to false,
                        "displayModeBar" to false,
                        "xMin" to 0.0,
                        "xMax" to max(100.0, inputsSnapshot.valoreSpesaGiornalieraMaxUtilita),
                        "yMin" to 0.0,
                        "yMax" to 1.0
                    )
                )
            }
            PlotlyWebView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                specJson = utilitySpecJson,
                onCurveChanged = { curveId, points ->
                    // Only update P1 (Active)
                    if (curveId != "utility") return@PlotlyWebView
                    val sanitized = sanitizeCurvePoints(
                        points = points,
                        xMin = 0.0,
                        xMax = max(100.0, inputsSnapshot.valoreSpesaGiornalieraMaxUtilita),
                        yMin = 0.0,
                        yMax = 1.0,
                        lockXOrder = true
                    )
                    onUpdateParsedInput { it.copy(utilityCurvePoints = sanitized) }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.age_degradation_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.age_degradation_desc),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Build Degradation Traces
            val degradationCurvePoints = inputsSnapshot.degradationCurvePoints.orEmpty()
            val degradationTraces = mutableListOf<PlotlySpecBuilder.LineTraceSpec>()
            degradationTraces.add(PlotlySpecBuilder.LineTraceSpec(
                name = if (isComparing) stringResource(R.string.chart_trace_p1) else context.getString(R.string.assumptions_chart_trace_degradation),
                x = degradationCurvePoints.map { it.x },
                y = degradationCurvePoints.map { it.y },
                color = ChartP1Hex,
                pointColor = ChartP1PointHex
            ))
            if (isComparing && profile2Inputs != null) {
                val p2Points = profile2Inputs.withDefaultAssumptionCurves().degradationCurvePoints.orEmpty()
                degradationTraces.add(PlotlySpecBuilder.LineTraceSpec(
                    name = stringResource(R.string.chart_trace_p2),
                    x = p2Points.map { it.x },
                    y = p2Points.map { it.y },
                    color = ChartP2Hex,
                    pointColor = ChartP2PointHex
                ))
            }

            val degradationSpecJson = remember(degradationCurvePoints, isComparing, profile2Inputs) {
                PlotlySpecBuilder.buildMultiLineJson(
                    traces = degradationTraces,
                    title = context.getString(R.string.assumptions_chart_degradation_title),
                    axisXTitle = context.getString(R.string.assumptions_chart_age_x),
                    axisYTitle = context.getString(R.string.assumptions_chart_degradation_y),
                    xRange = 30.0 to 90.0,
                    yRange = 0.0 to 1.0,
                    fixedRange = true,
                    meta = mapOf(
                        "curveId" to "degradation",
                        "draggablePoints" to true,
                        "lockXOrder" to true,
                        "scrollZoom" to false,
                        "doubleClick" to false,
                        "displayModeBar" to false,
                        "xMin" to 30.0,
                        "xMax" to 90.0,
                        "yMin" to 0.0,
                        "yMax" to 1.0
                    )
                )
            }
            PlotlyWebView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                specJson = degradationSpecJson,
                onCurveChanged = { curveId, points ->
                    if (curveId != "degradation") return@PlotlyWebView
                    val sanitized = sanitizeCurvePoints(
                        points = points,
                        xMin = 30.0,
                        xMax = 90.0,
                        yMin = 0.0,
                        yMax = 1.0,
                        lockXOrder = true
                    )
                    onUpdateParsedInput { it.copy(degradationCurvePoints = sanitized) }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun sanitizeCurvePoints(
    points: List<CurvePoint>,
    xMin: Double,
    xMax: Double,
    yMin: Double,
    yMax: Double,
    lockXOrder: Boolean
): List<CurvePoint> {
    if (points.size < 2) return points
    val eps = 1e-6
    val clamped = points.map { p ->
        CurvePoint(
            x = p.x.coerceIn(xMin, xMax),
            y = p.y.coerceIn(yMin, yMax)
        )
    }.toMutableList()

    if (lockXOrder) {
        for (i in clamped.indices) {
            val prevX = if (i > 0) clamped[i - 1].x else null
            val nextX = if (i < clamped.lastIndex) clamped[i + 1].x else null
            val lo = if (prevX != null) max(xMin, prevX + eps) else xMin
            val hi = if (nextX != null) java.lang.Math.min(xMax, nextX - eps) else xMax
            val x = clamped[i].x.coerceIn(lo, hi)
            clamped[i] = clamped[i].copy(x = x)
        }
    }

    return clamped
}

@Preview
@Composable
fun AssumptionsPreview() {
    AssumptionsContent(
        inputsSnapshot = FinancialInput().withDefaultAssumptionCurves(),
        isComparing = false,
        profile2Inputs = null,
        onUpdateParsedInput = {},
        onBack = {}
    )
}
