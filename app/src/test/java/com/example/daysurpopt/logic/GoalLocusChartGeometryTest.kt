// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Axis geometry for the native Compose locus chart: the y axis must always start
 * at 0 (rangemode tozero semantics), use "nice" step values (1/2/2.5/5 x 10^n)
 * targeted at ~5 divisions, and end with headroom above the largest value
 * (which includes the current-plan marker).
 */
class GoalLocusChartGeometryTest {

    @Test
    fun y_ticks_use_nice_steps_with_headroom_above_max() {
        assertEquals(listOf(0.0, 50000.0, 100000.0, 150000.0), GoalLocusChartGeometry.yAxisTicks(140000.0))
    }

    @Test
    fun y_ticks_land_on_a_round_top_for_typical_capitals() {
        assertEquals(
            listOf(0.0, 20000.0, 40000.0, 60000.0, 80000.0, 100000.0),
            GoalLocusChartGeometry.yAxisTicks(99609.0)
        )
        assertEquals(
            listOf(0.0, 20000.0, 40000.0, 60000.0, 80000.0),
            GoalLocusChartGeometry.yAxisTicks(67000.0)
        )
    }

    @Test
    fun y_ticks_degrade_safely_for_zero_or_degenerate_max() {
        assertEquals(listOf(0.0, 1.0), GoalLocusChartGeometry.yAxisTicks(0.0))
        assertEquals(listOf(0.0, 1.0), GoalLocusChartGeometry.yAxisTicks(-5.0))
    }

    @Test
    fun nearest_probe_point_snaps_to_the_closest_x_including_the_marker() {
        val locus = listOf(
            GoalLocusChartPoint(0.0, 140000.0),
            GoalLocusChartPoint(40.0, 100000.0),
            GoalLocusChartPoint(100.0, 99609.0)
        )
        val marker = GoalLocusChartPoint(20.78, 120000.0)

        assertEquals(GoalLocusChartPoint(20.78, 120000.0), GoalLocusChartGeometry.nearestProbePoint(locus, marker, 21.0))
        assertEquals(GoalLocusChartPoint(40.0, 100000.0), GoalLocusChartGeometry.nearestProbePoint(locus, marker, 45.0))
        assertEquals(GoalLocusChartPoint(0.0, 140000.0), GoalLocusChartGeometry.nearestProbePoint(locus, marker, 5.0))
        assertEquals(GoalLocusChartPoint(100.0, 99609.0), GoalLocusChartGeometry.nearestProbePoint(locus, marker, 90.0))
    }

    @Test
    fun nearest_probe_point_without_marker_and_on_empty_locus() {
        val locus = listOf(GoalLocusChartPoint(50.0, 30000.0))
        assertEquals(GoalLocusChartPoint(50.0, 30000.0), GoalLocusChartGeometry.nearestProbePoint(locus, null, 60.0))
        assertEquals(null, GoalLocusChartGeometry.nearestProbePoint(emptyList(), null, 50.0))
    }
}
