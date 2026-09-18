// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

data class ParetoPoint(
    val params: ParamsCandidate,
    val avgUtility: Double,
    val stdDevUtility: Double,
    val isFeasible: Boolean,
    val finalCapital: Double,
    val legacyGap: Double,
    val normalizedUtilityLoss: Double = 0.0,
    val normalizedStabilityLoss: Double = 0.0,
    val kneeScore: Double? = null,
    val rank: Int = 0,
    val crowdingDistance: Double = 0.0
) {
    fun dominates(other: ParetoPoint): Boolean {
        if (!isFeasible || !other.isFeasible) return false

        val noWorseUtility = avgUtility >= other.avgUtility
        val noWorseStability = stdDevUtility <= other.stdDevUtility
        val strictlyBetter = avgUtility > other.avgUtility || stdDevUtility < other.stdDevUtility

        return noWorseUtility && noWorseStability && strictlyBetter
    }

    fun constraintDominates(other: ParetoPoint): Boolean {
        return isFeasible && !other.isFeasible
    }
}

data class ParetoFrontResult(
    val points: List<ParetoPoint>,
    val referencePoint: ParetoPoint? = null,
    val idealAvgUtility: Double = 0.0,
    val idealStdDevUtility: Double = 0.0
)

data class OptimizationMarkerSnapshot(
    val mode: OptimizationMode,
    val params: ParamsCandidate,
    val objectiveValue: Double,
    val avgUtility: Double,
    val stdDevUtility: Double,
    val stabilityIndex: Double,
    val weightUsed: Double,
    val kneeScore: Double? = null
)

fun ParetoPoint.toOptimizationMarkerSnapshot(
    mode: OptimizationMode,
    objectiveValue: Double,
    stabilityIndex: Double,
    weightUsed: Double
): OptimizationMarkerSnapshot {
    return OptimizationMarkerSnapshot(
        mode = mode,
        params = params,
        objectiveValue = objectiveValue,
        avgUtility = avgUtility,
        stdDevUtility = stdDevUtility,
        stabilityIndex = stabilityIndex,
        weightUsed = weightUsed,
        kneeScore = kneeScore
    )
}

enum class OptimizationMode {
    TRUE_SCALAR,
    PARETO_KNEE,
    PARETO_FRONT
}
