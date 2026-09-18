// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

/**
 * State for compare mode functionality
 */
data class CompareState(
    val isComparing: Boolean = false,
    val profile1Name: String? = null,
    val profile2Name: String? = null,
    val profile1: FullProfile? = null,
    val profile2: FullProfile? = null
)

/**
 * Delta results for objective function comparison
 */
data class DeltaObjectiveResults(
    val deltaFObjW: Double,
    val deltaFObj0: Double,
    val deltaStabilityIndex: Double,
    val deltaStdDev: Double,
    val deltaAvgUtilita: Double,
    val deltaLegacyGap: Double = 0.0,
    val deltaFinalCapital: Double = 0.0
)

/**
 * Delta for a single simulation year (Profile 2 - Profile 1)
 */
data class DeltaSimulationYear(
    val eta: Int,
    val deltaCapitaleInizioAnno: Double,
    val deltaSpesaMensileCorrettaFinale: Double,
    val deltaFunzioneUtilita: Double,
    val deltaSavingRatioEffettivo: Double,
    val deltaCapitaleFineAnno: Double,
    val deltaDebtAmount: Double,
    val deltaDebtRepayment: Double,
    val deltaCapitaleEroso: Double
)

/**
 * Delta for sensitivity results
 */
data class DeltaSensitivityResult(
    val nameResId: Int,
    val deltaScaledImpact: Double,
    val unitResId: Int
)

/**
 * Utility object for computing deltas between profiles
 */
object DeltaCalculator {

    /**
     * Compute delta objective results (Profile 2 - Profile 1)
     */
    fun computeDeltaObjectives(
        results1: ObjectiveResults,
        results2: ObjectiveResults
    ): DeltaObjectiveResults {
        return DeltaObjectiveResults(
            deltaFObjW = results2.fObjW - results1.fObjW,
            deltaFObj0 = results2.fObj0 - results1.fObj0,
            deltaStabilityIndex = results2.stabilityIndex - results1.stabilityIndex,
            deltaStdDev = results2.stdDev - results1.stdDev,
            deltaAvgUtilita = results2.avgUtilita - results1.avgUtilita,
            deltaLegacyGap = results2.legacyGap - results1.legacyGap,
            deltaFinalCapital = results2.finalCapital - results1.finalCapital
        )
    }

    /**
     * Compute delta simulation years (Profile 2 - Profile 1)
     * Aligns by eta (age), returns deltas only for matching years
     */
    fun computeDeltaSimulation(
        years1: List<SimulationYear>,
        years2: List<SimulationYear>
    ): List<DeltaSimulationYear> {
        val map1 = years1.associateBy { it.eta }
        val map2 = years2.associateBy { it.eta }
        val allEtas = (map1.keys + map2.keys).sorted()

        return allEtas.map { eta ->
            val y1 = map1[eta]
            val y2 = map2[eta]
            DeltaSimulationYear(
                eta = eta,
                deltaCapitaleInizioAnno = (y2?.capitaleInizioAnno ?: 0.0) - (y1?.capitaleInizioAnno ?: 0.0),
                deltaSpesaMensileCorrettaFinale = (y2?.spesaMensileCorrettaFinale ?: 0.0) - (y1?.spesaMensileCorrettaFinale ?: 0.0),
                deltaFunzioneUtilita = (y2?.funzioneUtilita ?: 0.0) - (y1?.funzioneUtilita ?: 0.0),
                deltaSavingRatioEffettivo = (y2?.savingRatioEffettivo ?: 0.0) - (y1?.savingRatioEffettivo ?: 0.0),
                deltaCapitaleFineAnno = (y2?.capitaleFineAnno ?: 0.0) - (y1?.capitaleFineAnno ?: 0.0),
                deltaDebtAmount = (y2?.debtAmount ?: 0.0) - (y1?.debtAmount ?: 0.0),
                deltaDebtRepayment = (y2?.debtRepayment ?: 0.0) - (y1?.debtRepayment ?: 0.0),
                deltaCapitaleEroso = (y2?.capitaleEroso ?: 0.0) - (y1?.capitaleEroso ?: 0.0)
            )
        }
    }

    /**
     * Compute delta sensitivity (Profile 2 - Profile 1)
     * Aligns by nameResId
     */
    fun computeDeltaSensitivity(
        sens1: List<SensitivityResult>,
        sens2: List<SensitivityResult>
    ): List<DeltaSensitivityResult> {
        val map1 = sens1.associateBy { it.nameResId }
        val map2 = sens2.associateBy { it.nameResId }
        val allNames = (map1.keys + map2.keys).sorted()

        return allNames.map { nameResId ->
            val s1 = map1[nameResId]
            val s2 = map2[nameResId]
            DeltaSensitivityResult(
                nameResId = nameResId,
                deltaScaledImpact = (s2?.scaledImpact ?: 0.0) - (s1?.scaledImpact ?: 0.0),
                unitResId = s1?.unitResId ?: s2?.unitResId ?: 0
            )
        }
    }

    /**
     * Compute delta for a Double field (Profile 2 - Profile 1)
     * Returns null if values are equal (no delta to display)
     */
    fun deltaOrNull(value1: Double, value2: Double, tolerance: Double = 0.0001): Double? {
        val delta = value2 - value1
        return if (kotlin.math.abs(delta) < tolerance) null else delta
    }

    /**
     * Compute delta for an Int field (Profile 2 - Profile 1)
     * Returns null if values are equal
     */
    fun deltaOrNull(value1: Int, value2: Int): Int? {
        val delta = value2 - value1
        return if (delta == 0) null else delta
    }
}
