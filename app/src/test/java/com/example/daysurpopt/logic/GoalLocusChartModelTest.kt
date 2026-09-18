// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the pure model behind the Goal Solver locus chart (C_I vs P1):
 * feasible sweep rows become the boundary curve (P1 in percent), the user's
 * CURRENT simulation position (its P1 and actual initial capital) becomes the
 * red marker; infeasible rows (no capital answer) are not plottable and are
 * skipped.
 */
class GoalLocusChartModelTest {

    private fun sweep(vararg rows: GoalSweepRow) = GoalSweepResult(
        threshold = 0.25,
        stopWorkAge = 55,
        maxAchievableUtility = 0.5,
        rows = rows.toList()
    )

    @Test
    fun build_maps_feasible_rows_to_percent_sorted_and_skips_infeasible() {
        val sweep = sweep(
            GoalSweepRow(0.4, 10000.0, true, false),
            GoalSweepRow(0.2, null, false, false),
            GoalSweepRow(0.0, 50000.0, true, false),
            GoalSweepRow(0.6, 0.0, true, false)
        )

        val model = GoalLocusChartModelBuilder.build(
            sweep = sweep,
            currentP1 = 0.2078,
            currentCapital = 100000.0
        )

        assertEquals(3, model.locusPoints.size)
        assertEquals(0.0, model.locusPoints[0].p1Percent, 1e-9)
        assertEquals(50000.0, model.locusPoints[0].requiredCapital, 1e-9)
        assertEquals(40.0, model.locusPoints[1].p1Percent, 1e-9)
        assertEquals(10000.0, model.locusPoints[1].requiredCapital, 1e-9)
        assertEquals(60.0, model.locusPoints[2].p1Percent, 1e-9)
        assertEquals(0.0, model.locusPoints[2].requiredCapital, 1e-9)
    }

    @Test
    fun build_current_simulation_marker_is_the_actual_position_with_coerced_p1() {
        val sweep = sweep(GoalSweepRow(0.3, 0.0, true, true))

        val model = GoalLocusChartModelBuilder.build(
            sweep = sweep,
            currentP1 = 0.2078,
            currentCapital = 100000.0
        )
        assertEquals(20.78, model.currentSimulationMarker.p1Percent, 1e-9)
        assertEquals(100000.0, model.currentSimulationMarker.requiredCapital, 1e-9)

        val high = GoalLocusChartModelBuilder.build(sweep, currentP1 = 1.5, currentCapital = 0.0)
        assertEquals(100.0, high.currentSimulationMarker.p1Percent, 1e-9)
        assertEquals(0.0, high.currentSimulationMarker.requiredCapital, 1e-9)

        val low = GoalLocusChartModelBuilder.build(sweep, currentP1 = -0.2, currentCapital = 1234.0)
        assertEquals(0.0, low.currentSimulationMarker.p1Percent, 1e-9)
        assertEquals(1234.0, low.currentSimulationMarker.requiredCapital, 1e-9)
    }

    @Test
    fun build_with_all_rows_infeasible_keeps_only_the_current_marker() {
        val sweep = sweep(
            GoalSweepRow(0.2, null, false, true),
            GoalSweepRow(0.4, null, false, false)
        )

        val model = GoalLocusChartModelBuilder.build(
            sweep = sweep,
            currentP1 = 0.2,
            currentCapital = 50000.0
        )

        assertEquals(0, model.locusPoints.size)
        assertEquals(20.0, model.currentSimulationMarker.p1Percent, 1e-9)
        assertEquals(50000.0, model.currentSimulationMarker.requiredCapital, 1e-9)
    }
}
