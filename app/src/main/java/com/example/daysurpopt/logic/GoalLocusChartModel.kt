// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

/**
 * One plottable point of the Goal Solver locus chart (C_I vs P1): [p1Percent]
 * is the saving ratio in percent (0..100), [requiredCapital] the minimum initial
 * capital in EUR (0 when the goal is feasible with no capital at all).
 */
data class GoalLocusChartPoint(
    val p1Percent: Double,
    val requiredCapital: Double
)

/**
 * Pure model behind the Goal Solver chart:
 *  - [locusPoints]: the feasible boundary curve C*(P1), sorted by P1 (infeasible
 *    rows have no capital answer and cannot be plotted);
 *  - [currentSimulationMarker]: the user's CURRENT simulation position
 *    (its own P1 and actual initial capital) — rendered as the red marker so the
 *    chart reads "where am I relative to the boundary" at a glance.
 */
data class GoalLocusChartModel(
    val locusPoints: List<GoalLocusChartPoint>,
    val currentSimulationMarker: GoalLocusChartPoint
)

object GoalLocusChartModelBuilder {

    fun build(
        sweep: GoalSweepResult,
        currentP1: Double,
        currentCapital: Double
    ): GoalLocusChartModel {
        val locusPoints = sweep.rows
            .filter { it.isFeasible && it.requiredCapital != null }
            .sortedBy { it.p1 }
            .map { GoalLocusChartPoint(p1Percent = it.p1 * 100.0, requiredCapital = it.requiredCapital!!) }

        val currentSimulationMarker = GoalLocusChartPoint(
            p1Percent = currentP1.coerceIn(0.0, 1.0) * 100.0,
            requiredCapital = currentCapital
        )

        return GoalLocusChartModel(locusPoints, currentSimulationMarker)
    }
}
