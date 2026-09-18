// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

data class FullProfile(
    val financialInput: FinancialInput,
    val surplusInput: SurplusInput,
    val specificExpenses: List<SpecificExpense>,
    val gaConfig: GAConfigUI
)

data class SimulationYear(
    val eta: Int,
    val capitaleInizioAnno: Double = 0.0,
    val spesaMensileCorrettaFinale: Double = 0.0,
    val funzioneUtilita: Double,
    val savingRatioEffettivo: Double = 0.0,
    val violazioneLascito: Boolean = false,
    val capitaleFineAnno: Double = 0.0,
    val utilityAtThreshold: Boolean = false,
    val debtAmount: Double = 0.0,
    val debtRepayment: Double = 0.0,
    val capitaleEroso: Double = 0.0,
    val monthlyUtilitySamples: List<Double> = emptyList()
)

data class SensitivityResult(
    val nameResId: Int,
    val scaledImpact: Double,
    val unitResId: Int
)

data class ParamsCandidate(val p1: Double, val p2: Int, val p3: Double, val p4: Int)

data class OptimizationResult(val bestParams: ParamsCandidate, val bestFitness: Double, val history: List<Pair<Int, Double>>)

data class ObjectiveResults(
    val fObjW: Double,
    val fObj0: Double,
    val stabilityIndex: Double,
    val stdDev: Double,
    val avgUtilita: Double,
    val isFeasible: Boolean = false,
    val finalCapital: Double = 0.0,
    val legacyGap: Double = 0.0
)
