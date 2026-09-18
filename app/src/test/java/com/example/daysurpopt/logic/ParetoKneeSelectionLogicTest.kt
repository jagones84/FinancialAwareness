// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.ParamsCandidate
import com.example.daysurpopt.domain.ParetoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParetoKneeSelectionLogicTest {

    @Test
    fun selectKneePoint_picks_balanced_point_farthest_from_extreme_chord() {
        val points = listOf(
            ParetoPoint(
                params = ParamsCandidate(0.10, 60, 0.10, 65),
                avgUtility = 0.30,
                stdDevUtility = 0.10,
                isFeasible = true,
                finalCapital = 50000.0,
                legacyGap = 10000.0
            ),
            ParetoPoint(
                params = ParamsCandidate(0.20, 60, 0.20, 65),
                avgUtility = 0.80,
                stdDevUtility = 0.25,
                isFeasible = true,
                finalCapital = 52000.0,
                legacyGap = 9000.0
            ),
            ParetoPoint(
                params = ParamsCandidate(0.30, 60, 0.30, 65),
                avgUtility = 1.00,
                stdDevUtility = 0.90,
                isFeasible = true,
                finalCapital = 54000.0,
                legacyGap = 8000.0
            )
        )

        val selected = ParetoKneeSelectionLogic.selectKneePoint(points)

        assertEquals(0.80, selected.avgUtility, 1e-9)
        assertTrue((selected.kneeScore ?: 0.0) > 0.0)
    }

    @Test
    fun selectKneePoint_returns_same_point_for_same_front_independent_of_scalar_weight() {
        val points = listOf(
            ParetoPoint(
                params = ParamsCandidate(0.10, 60, 0.10, 65),
                avgUtility = 0.25,
                stdDevUtility = 0.08,
                isFeasible = true,
                finalCapital = 50000.0,
                legacyGap = 10000.0
            ),
            ParetoPoint(
                params = ParamsCandidate(0.20, 60, 0.20, 65),
                avgUtility = 0.70,
                stdDevUtility = 0.20,
                isFeasible = true,
                finalCapital = 52000.0,
                legacyGap = 9000.0
            ),
            ParetoPoint(
                params = ParamsCandidate(0.30, 60, 0.30, 65),
                avgUtility = 0.90,
                stdDevUtility = 0.75,
                isFeasible = true,
                finalCapital = 54000.0,
                legacyGap = 8000.0
            )
        )

        val selected = ParetoKneeSelectionLogic.selectKneePoint(points)

        assertEquals(0.70, selected.avgUtility, 1e-9)
    }
}
