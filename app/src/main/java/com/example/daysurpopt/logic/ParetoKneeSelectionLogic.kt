// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.ParetoPoint
import kotlin.math.hypot

object ParetoKneeSelectionLogic {

    fun selectKneePoint(points: List<ParetoPoint>): ParetoPoint {
        require(points.isNotEmpty()) { "Pareto set cannot be empty" }
        if (points.size == 1) {
            return points.first().copy(kneeScore = 0.0)
        }

        val stdMin = points.minOf { it.stdDevUtility }
        val stdMax = points.maxOf { it.stdDevUtility }
        val avgMin = points.minOf { it.avgUtility }
        val avgMax = points.maxOf { it.avgUtility }

        val normalizedPoints = points.map { point ->
            val x = normalize(point.stdDevUtility, stdMin, stdMax)
            val y = normalize(point.avgUtility, avgMin, avgMax)
            Triple(point, x, y)
        }

        val leftExtreme = normalizedPoints.minBy { (_, x, _) -> x }
        val topExtreme = normalizedPoints.maxBy { (_, _, y) -> y }
        val dx = topExtreme.second - leftExtreme.second
        val dy = topExtreme.third - leftExtreme.third
        val denom = hypot(dx, dy).takeIf { it > 1e-12 } ?: 1.0

        return normalizedPoints
            .map { (point, x, y) ->
                val distance = kotlin.math.abs(
                    dy * x - dx * y + topExtreme.second * leftExtreme.third - topExtreme.third * leftExtreme.second
                ) / denom
                point.copy(kneeScore = distance)
            }
            .maxBy { it.kneeScore ?: Double.NEGATIVE_INFINITY }
    }

    private fun normalize(value: Double, min: Double, max: Double): Double {
        val range = max - min
        return if (range <= 1e-12) 0.0 else (value - min) / range
    }
}
