// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.ParetoPoint

data class ParetoScatterPoint(
    val x: Double,
    val y: Double,
    val point: ParetoPoint
)

data class ParetoChartModel(
    val basePoints: List<ParetoScatterPoint>,
    val referenceMarker: ParetoScatterPoint?,
    val appliedMarker: ParetoScatterPoint?,
    val selectedMarker: ParetoScatterPoint?
)

object ParetoChartModelBuilder {

    fun build(
        points: List<ParetoPoint>,
        referencePoint: ParetoPoint?,
        appliedPoint: ParetoPoint?,
        selectedPoint: ParetoPoint?
    ): ParetoChartModel {
        fun ParetoPoint.toScatter() = ParetoScatterPoint(
            x = stdDevUtility,
            y = avgUtility,
            point = this
        )

        return ParetoChartModel(
            basePoints = points.map { it.toScatter() },
            referenceMarker = referencePoint?.toScatter(),
            appliedMarker = appliedPoint?.toScatter(),
            selectedMarker = selectedPoint?.toScatter()
        )
    }
}
