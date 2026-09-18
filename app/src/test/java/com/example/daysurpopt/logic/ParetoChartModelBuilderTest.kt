// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.ParamsCandidate
import com.example.daysurpopt.domain.ParetoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class ParetoChartModelBuilderTest {

    @Test
    fun build_returns_front_reference_applied_and_selected_roles() {
        val applied = ParetoPoint(
            params = ParamsCandidate(0.20, 60, 0.20, 66),
            avgUtility = 0.20,
            stdDevUtility = 0.30,
            isFeasible = true,
            finalCapital = 70000.0,
            legacyGap = 5000.0
        )
        val reference = ParetoPoint(
            params = ParamsCandidate(0.35, 62, 0.25, 68),
            avgUtility = 0.30,
            stdDevUtility = 0.15,
            isFeasible = true,
            finalCapital = 76000.0,
            legacyGap = 9000.0
        )

        val model = ParetoChartModelBuilder.build(
            points = listOf(applied, reference),
            referencePoint = reference,
            appliedPoint = applied,
            selectedPoint = reference
        )

        assertEquals(2, model.basePoints.size)
        assertEquals(0.30, model.referenceMarker!!.y, 1e-9)
        assertEquals(0.20, model.appliedMarker!!.y, 1e-9)
        assertEquals(0.15, model.selectedMarker!!.x, 1e-9)
    }

    @Test
    fun build_keeps_objective_space_coordinates_stddev_on_x_avg_on_y() {
        val point = ParetoPoint(
            params = ParamsCandidate(0.20, 60, 0.20, 66),
            avgUtility = 0.25,
            stdDevUtility = 0.12,
            isFeasible = true,
            finalCapital = 70000.0,
            legacyGap = 5000.0
        )

        val model = ParetoChartModelBuilder.build(
            points = listOf(point),
            referencePoint = point,
            appliedPoint = point,
            selectedPoint = point
        )

        assertEquals(0.12, model.basePoints.first().x, 1e-9)
        assertEquals(0.25, model.basePoints.first().y, 1e-9)
    }
}
