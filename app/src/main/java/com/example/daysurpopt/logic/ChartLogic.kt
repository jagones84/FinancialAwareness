// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.GAConfig
import com.example.daysurpopt.domain.SimulationYear
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.example.daysurpopt.ui.screens.SurfaceGrid
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Logic for generating data for the charts (surfaces and markers).
 */
object ChartLogic {

    /**
     * Generates a grid of objective values for P1 (Saving Ratio) and P2 (Retirement Age).
     *
     * @param inputsSnapshot Current [FinancialInput] for the baseline profile.
     * @param expensesSnapshot List of specific expenses for the baseline profile.
     * @param surplusData Surplus input data for the baseline profile.
     * @param cfg GA configuration for parameter ranges.
     * @param w Objective function weight.
     * @param isComparing Whether to compute a delta grid for comparison.
     * @param profile2Inputs Optional [FinancialInput] for the second profile.
     * @param profile2Expenses Optional list of specific expenses for the second profile.
     * @param profile2Surplus Optional surplus input data for the second profile.
     * @return A Pair containing the normal [SurfaceGrid] and an optional delta [SurfaceGrid].
     */
    suspend fun computeP1P2Grid(
        inputsSnapshot: FinancialInput,
        expensesSnapshot: List<SpecificExpense>,
        surplusData: SurplusInput,
        cfg: GAConfig,
        w: Double,
        isComparing: Boolean,
        profile2Inputs: FinancialInput?,
        profile2Expenses: List<SpecificExpense>?,
        profile2Surplus: SurplusInput?
    ): Pair<SurfaceGrid, SurfaceGrid?> {
        val p2Min = cfg.min.p2
        val p2Max = cfg.max.p2
        
        val fixedP3 = inputsSnapshot.p3PercentualeCapitaleDaSpendereAnnualmente
        val fixedP4 = inputsSnapshot.p4EtaAnticipataInizioSpesaCapitale

        val x = linspace(cfg.min.p1, cfg.max.p1, 22)
        val rawY = sampleIntRange(p2Min, p2Max, 22)
        val y = if (p2Min == p2Max) {
            listOf(p2Min.toDouble() - 0.001, p2Min.toDouble() + 0.001)
        } else if (rawY.size < 2 && p2Min != p2Max) {
            listOf(p2Min.toDouble(), p2Max.toDouble())
        } else {
            rawY.map { it.toDouble() }
        }
        
        val z = MutableList(y.size) { MutableList(x.size) { null as Double? } }
        val zDelta = MutableList(y.size) { MutableList(x.size) { null as Double? } }

        for (iy in y.indices) {
            val p2 = y[iy].roundToInt()
            for (ix in x.indices) {
                val p1 = x[ix]
                // Standard Profile 1
                val in1 = inputsSnapshot.copy(
                    p1SavingRatioSurplus = p1,
                    p2EtaFineRisparmioNoCapitale = p2,
                    p3PercentualeCapitaleDaSpendereAnnualmente = fixedP3,
                    p4EtaAnticipataInizioSpesaCapitale = fixedP4,
                    bonusStdWeight = w
                )
                val years1 = calculateSimulation(in1, expensesSnapshot, surplusData)
                val res1 = calculateObjectivesFromYears(years1, w, in1.soldiDaConservare)
                z[iy][ix] = res1.fObjW

                // Profile 2 Delta logic
                if (isComparing && profile2Inputs != null && profile2Surplus != null && profile2Expenses != null) {
                    val fixedP3_2 = profile2Inputs.p3PercentualeCapitaleDaSpendereAnnualmente
                    val fixedP4_2 = profile2Inputs.p4EtaAnticipataInizioSpesaCapitale
                    val in2 = profile2Inputs.copy(
                        p1SavingRatioSurplus = p1,
                        p2EtaFineRisparmioNoCapitale = p2,
                        p3PercentualeCapitaleDaSpendereAnnualmente = fixedP3_2,
                        p4EtaAnticipataInizioSpesaCapitale = fixedP4_2,
                        bonusStdWeight = w
                    )
                    val years2 = calculateSimulation(in2, profile2Expenses, profile2Surplus)
                    val res2 = calculateObjectivesFromYears(years2, w, in2.soldiDaConservare)
                    
                    val z1val = res1.fObjW
                    val z2val = res2.fObjW
                    
                    if (zDelta[iy][ix] == null) zDelta[iy][ix] = 0.0
                    if (z1val.isFinite() && z2val.isFinite()) {
                        zDelta[iy][ix] = z2val - z1val
                    } else {
                        zDelta[iy][ix] = null
                    }
                }
            }
        }
        
        val normalGrid = SurfaceGrid(x = x, y = y, z = z, anchorColorScaleOnFeasible = true)
        val deltaGrid = if (isComparing) SurfaceGrid(x = x, y = y, z = zDelta) else null

        return Pair(normalGrid, deltaGrid)
    }

    /**
     * Generates a grid of objective values for P3 (Spending Ratio) and P4 (Spending Start Age).
     *
     * @param inputsSnapshot Current [FinancialInput] for the baseline profile.
     * @param expensesSnapshot List of specific expenses for the baseline profile.
     * @param surplusData Surplus input data for the baseline profile.
     * @param cfg GA configuration for parameter ranges.
     * @param w Objective function weight.
     * @param fixedP1 Fixed value for P1 during this computation.
     * @param fixedP2 Fixed value for P2 during this computation.
     * @param isComparing Whether to compute a delta grid for comparison.
     * @param profile2Inputs Optional [FinancialInput] for the second profile.
     * @param profile2Expenses Optional list of specific expenses for the second profile.
     * @param profile2Surplus Optional surplus input data for the second profile.
     * @return A Pair containing the normal [SurfaceGrid] and an optional delta [SurfaceGrid].
     */
    suspend fun computeP3P4Grid(
        inputsSnapshot: FinancialInput,
        expensesSnapshot: List<SpecificExpense>,
        surplusData: SurplusInput,
        cfg: GAConfig,
        w: Double,
        fixedP1: Double,
        fixedP2: Int,
        isComparing: Boolean,
        profile2Inputs: FinancialInput?,
        profile2Expenses: List<SpecificExpense>?,
        profile2Surplus: SurplusInput?
    ): Pair<SurfaceGrid, SurfaceGrid?> {
        val p4Min = max(cfg.min.p4, fixedP2)
        val p4Max = max(cfg.max.p4, fixedP2)
        
        val x = linspace(cfg.min.p3, cfg.max.p3, 22)
        val rawY = sampleIntRange(p4Min, p4Max, 22)
        val y = if (p4Min == p4Max) {
            listOf(p4Min.toDouble() - 0.001, p4Min.toDouble() + 0.001)
        } else if (rawY.size < 2 && p4Min != p4Max) {
            listOf(p4Min.toDouble(), p4Max.toDouble())
        } else {
            rawY.map { it.toDouble() }
        }
        
        val z = MutableList(y.size) { MutableList(x.size) { null as Double? } }
        val zDelta = MutableList(y.size) { MutableList(x.size) { null as Double? } }

        for (iy in y.indices) {
            val p4 = y[iy].roundToInt()
            for (ix in x.indices) {
                val p3 = x[ix]
                val in1 = inputsSnapshot.copy(
                    p1SavingRatioSurplus = fixedP1,
                    p2EtaFineRisparmioNoCapitale = fixedP2,
                    p3PercentualeCapitaleDaSpendereAnnualmente = p3,
                    p4EtaAnticipataInizioSpesaCapitale = p4,
                    bonusStdWeight = w
                )
                val years1 = calculateSimulation(in1, expensesSnapshot, surplusData)
                val res1 = calculateObjectivesFromYears(years1, w, in1.soldiDaConservare)
                z[iy][ix] = res1.fObjW
                
                // Profile 2 Delta logic
                if (isComparing && profile2Inputs != null && profile2Surplus != null && profile2Expenses != null) {
                    val fixedP1_2 = profile2Inputs.p1SavingRatioSurplus
                    val fixedP2_2 = profile2Inputs.p2EtaFineRisparmioNoCapitale
                    val in2 = profile2Inputs.copy(
                        p1SavingRatioSurplus = fixedP1_2,
                        p2EtaFineRisparmioNoCapitale = fixedP2_2,
                        p3PercentualeCapitaleDaSpendereAnnualmente = p3,
                        p4EtaAnticipataInizioSpesaCapitale = p4,
                        bonusStdWeight = w
                    )
                    val years2 = calculateSimulation(in2, profile2Expenses, profile2Surplus)
                    val res2 = calculateObjectivesFromYears(years2, w, in2.soldiDaConservare)
                    
                    val z1val = res1.fObjW
                    val z2val = res2.fObjW
                    
                    if (zDelta[iy][ix] == null) zDelta[iy][ix] = 0.0
                    if (z1val.isFinite() && z2val.isFinite()) {
                        zDelta[iy][ix] = z2val - z1val
                    } else {
                        zDelta[iy][ix] = null
                    }
                }
            }
        }
        val normalGrid = SurfaceGrid(x = x, y = y, z = z, anchorColorScaleOnFeasible = true)
        val deltaGrid = if (isComparing) SurfaceGrid(x = x, y = y, z = zDelta) else null

        return Pair(normalGrid, deltaGrid)
    }

    fun linspace(start: Double, end: Double, count: Int): List<Double> {
        if (count <= 1) return listOf(start)
        if (start == end) return List(count) { start }
        val step = (end - start) / (count - 1)
        return List(count) { i -> start + i * step }
    }

    fun sampleIntRange(start: Int, end: Int, count: Int): List<Int> {
        if (count <= 1) return listOf(start)
        if (start == end) return List(count) { start }
        val span = end - start
        return (0 until count).map { i ->
            val t = i.toDouble() / (count - 1).toDouble()
            (start + (span * t)).toInt()
        }.distinct().let { sampled ->
            if (sampled.isNotEmpty()) sampled else listOf(start, end).distinct()
        }
    }
}
