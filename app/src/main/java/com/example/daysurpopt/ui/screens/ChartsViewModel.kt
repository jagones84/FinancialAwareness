// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.daysurpopt.utils.AppDebugLog
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.GAConfig
import com.example.daysurpopt.domain.GAConfigUI
import com.example.daysurpopt.domain.SimulationYear
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.logic.ChartLogic
import com.example.daysurpopt.logic.OptimizationLogic
import com.example.daysurpopt.logic.calculateObjectivesFromYears
import com.example.daysurpopt.logic.calculateSimulation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for handling chart data generation and UI state.
 * Manages the calculation of 3D surface grids and handles UI toggles for chart display.
 *
 * @param application The application context.
 */
class ChartsViewModel(application: Application) : AndroidViewModel(application) {

    var p1p2State by mutableStateOf(ChartUiState(isLoading = true))
    var p3p4State by mutableStateOf(ChartUiState(isLoading = true))

    // Grid storage to avoid re-computation on toggle
    private var p1p2GridNormal: SurfaceGrid? = null
    private var p1p2GridDelta: SurfaceGrid? = null
    private var p3p4GridNormal: SurfaceGrid? = null
    private var p3p4GridDelta: SurfaceGrid? = null

    var optimalObjW by mutableStateOf(0.0)
    var optimalObj0 by mutableStateOf(0.0)
    var optimalStabilityIndex by mutableStateOf(0.0)

    // P2 & Cross-Profile Objectives
    var optimalObjW_2 by mutableStateOf(0.0)
    var optimalObjP1OnP2 by mutableStateOf(0.0)
    var optimalObjP2OnP1 by mutableStateOf(0.0)

    var showContours by mutableStateOf(true)
    var showDeltaView by mutableStateOf(false)
    var useHeatmap by mutableStateOf(false)
    var isPerspective by mutableStateOf(true)

    fun toggleShowContours() { showContours = !showContours }
    fun toggleShowDeltaView() { 
        showDeltaView = !showDeltaView
        updateGridsFromCache()
    }
    fun toggleUseHeatmap() { useHeatmap = !useHeatmap }
    fun toggleIsPerspective() { isPerspective = !isPerspective }

    private fun updateGridsFromCache() {
        p1p2State = p1p2State.copy(grid = if (showDeltaView) p1p2GridDelta else p1p2GridNormal)
        p3p4State = p3p4State.copy(grid = if (showDeltaView) p3p4GridDelta else p3p4GridNormal)
    }

    /**
     * Recomputes the chart grids based on the current financial inputs.
     *
     * @param inputs Current [FinancialInput] for the baseline profile.
     * @param gaConfigUI Genetic algorithm configuration (UI) for parameter ranges.
     * @param expenses List of specific expenses for the baseline profile.
     * @param surplusData Surplus input data for the baseline profile.
     * @param isComparing Whether to also compute delta grids for comparison.
     * @param profile2Inputs Optional [FinancialInput] for the second profile.
     * @param profile2Expenses Optional list of specific expenses for the second profile.
     * @param profile2Surplus Optional surplus input data for the second profile.
     */
    fun refreshGrids(
        inputs: FinancialInput,
        gaConfigUI: GAConfigUI,
        expenses: List<SpecificExpense>,
        surplusData: SurplusInput,
        isComparing: Boolean,
        profile2Inputs: FinancialInput?,
        profile2Expenses: List<SpecificExpense>?,
        profile2Surplus: SurplusInput?
    ) {
        viewModelScope.launch {
            AppDebugLog.add("Charts", "Refreshing grids in ViewModel...")
            p1p2State = p1p2State.copy(isLoading = true)
            p3p4State = p3p4State.copy(isLoading = true)

            val cfg = OptimizationLogic.parseGaConfig(gaConfigUI, inputs)
            val w = inputs.bonusStdWeight
            val fixedP1 = inputs.p1SavingRatioSurplus
            val fixedP2 = inputs.p2EtaFineRisparmioNoCapitale
            val fixedP3 = inputs.p3PercentualeCapitaleDaSpendereAnnualmente
            val fixedP4 = inputs.p4EtaAnticipataInizioSpesaCapitale

            // 1. Optimal objectives
            val yearsOpt = withContext(Dispatchers.Default) {
                calculateSimulation(inputs, expenses, surplusData)
            }
            val resultsOpt = calculateObjectivesFromYears(yearsOpt, w, inputs.soldiDaConservare)
            optimalObjW = resultsOpt.fObjW
            optimalObj0 = resultsOpt.fObj0
            optimalStabilityIndex = resultsOpt.stabilityIndex

            // 2. Cross-profile values
            if (isComparing && profile2Inputs != null && profile2Surplus != null && profile2Expenses != null) {
                val years2 = withContext(Dispatchers.Default) {
                    calculateSimulation(profile2Inputs, profile2Expenses, profile2Surplus)
                }
                optimalObjW_2 = calculateObjectivesFromYears(years2, w, profile2Inputs.soldiDaConservare).fObjW

                val in1on2 = profile2Inputs.copy(
                    p1SavingRatioSurplus = fixedP1,
                    p2EtaFineRisparmioNoCapitale = fixedP2,
                    p3PercentualeCapitaleDaSpendereAnnualmente = fixedP3,
                    p4EtaAnticipataInizioSpesaCapitale = fixedP4,
                    bonusStdWeight = w
                )
                val years1on2 = withContext(Dispatchers.Default) {
                    calculateSimulation(in1on2, profile2Expenses, profile2Surplus)
                }
                optimalObjP1OnP2 = calculateObjectivesFromYears(years1on2, w, in1on2.soldiDaConservare).fObjW

                val in2on1 = inputs.copy(
                    p1SavingRatioSurplus = profile2Inputs.p1SavingRatioSurplus,
                    p2EtaFineRisparmioNoCapitale = profile2Inputs.p2EtaFineRisparmioNoCapitale,
                    p3PercentualeCapitaleDaSpendereAnnualmente = profile2Inputs.p3PercentualeCapitaleDaSpendereAnnualmente,
                    p4EtaAnticipataInizioSpesaCapitale = profile2Inputs.p4EtaAnticipataInizioSpesaCapitale,
                    bonusStdWeight = w
                )
                val years2on1 = withContext(Dispatchers.Default) {
                    calculateSimulation(in2on1, expenses, surplusData)
                }
                optimalObjP2OnP1 = calculateObjectivesFromYears(years2on1, w, in2on1.soldiDaConservare).fObjW
            }

            // 3. Grids
            val (p1p2Normal, p1p2Delta) = withContext(Dispatchers.Default) {
                ChartLogic.computeP1P2Grid(
                    inputs, expenses, surplusData, cfg, w,
                    isComparing, profile2Inputs, profile2Expenses, profile2Surplus
                )
            }
            p1p2GridNormal = p1p2Normal
            p1p2GridDelta = p1p2Delta

            val (p3p4Normal, p3p4Delta) = withContext(Dispatchers.Default) {
                ChartLogic.computeP3P4Grid(
                    inputs, expenses, surplusData, cfg, w, fixedP1, fixedP2,
                    isComparing, profile2Inputs, profile2Expenses, profile2Surplus
                )
            }
            p3p4GridNormal = p3p4Normal
            p3p4GridDelta = p3p4Delta

            p1p2State = ChartUiState(grid = if (showDeltaView) p1p2GridDelta else p1p2GridNormal, isLoading = false)
            p3p4State = ChartUiState(grid = if (showDeltaView) p3p4GridDelta else p3p4GridNormal, isLoading = false)
        }
    }
}
