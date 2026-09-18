// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure axis geometry for the native Compose locus chart (C_I vs P1).
 * The y axis always starts at 0 (the solver's capital can be 0), uses "nice"
 * steps (1/2/2.5/5 x 10^n) targeted at ~5 divisions, and always ends with
 * headroom above the largest plotted value (locus max or current-plan marker).
 */
object GoalLocusChartGeometry {

    fun yAxisTicks(maxValue: Double): List<Double> {
        if (maxValue <= 0.0) return listOf(0.0, 1.0)
        val rawStep = maxValue / 5.0
        val magnitude = 10.0.pow(floor(log10(rawStep)))
        val step = listOf(1.0, 2.0, 2.5, 5.0).map { it * magnitude }.first { it >= rawStep }
        val topTicks = ceil(maxValue / step).roundToInt()
        return (0..topTicks).map { it * step }
    }

    /**
     * Chart probe (hover/tap): returns the plotted point whose P1 is closest to
     * [xPercent] — candidates are the locus points plus the current-plan marker
     * (null when there is nothing to probe).
     */
    fun nearestProbePoint(
        locusPoints: List<GoalLocusChartPoint>,
        currentMarker: GoalLocusChartPoint?,
        xPercent: Double
    ): GoalLocusChartPoint? {
        val candidates = buildList {
            addAll(locusPoints)
            currentMarker?.let { add(it) }
        }
        return candidates.minByOrNull { abs(it.p1Percent - xPercent) }
    }
}
