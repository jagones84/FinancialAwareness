// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.OptimizationMode
import com.example.daysurpopt.ui.screens.optimizationModeDisplayNameForTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OptimizationModeLabelTest {

    @Test
    fun optimizationMode_contains_true_scalar_pareto_knee_and_pareto_front() {
        val modes = OptimizationMode.entries.map { it.name }

        assertEquals(
            listOf("TRUE_SCALAR", "PARETO_KNEE", "PARETO_FRONT"),
            modes
        )
    }

    @Test
    fun optimizationModeLabelName_for_knee_uses_pareto_knee_wording() {
        assertEquals(
            "Pareto Knee",
            optimizationModeDisplayNameForTest(OptimizationMode.PARETO_KNEE)
        )
    }
}
