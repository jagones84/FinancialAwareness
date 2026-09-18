// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.logic

import com.example.daysurpopt.domain.GAConfig
import com.example.daysurpopt.domain.ParamsCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScalarOptimizationRefinementTest {

    @Test
    fun refineScalarCandidateForTest_never_worsens_start_fitness() {
        val start = ParamsCandidate(0.40, 60, 0.30, 65)

        val refined = refineScalarCandidateForTest(
            evaluator = { candidate ->
                if (candidate == start) 10.0 else 11.0
            },
            start = start,
            config = sampleConfig(),
            maximize = true
        )

        assertTrue(refined.bestFitness >= 10.0)
    }

    @Test
    fun refineScalarCandidateForTest_returns_local_search_result_when_better() {
        val start = ParamsCandidate(0.40, 60, 0.30, 65)

        val refined = refineScalarCandidateForTest(
            evaluator = { candidate ->
                if (candidate == start.copy(p1 = 0.45)) 12.0 else 10.0
            },
            start = start,
            config = sampleConfig(),
            maximize = true
        )

        assertEquals(0.45, refined.bestParams.p1, 1e-9)
        assertEquals(12.0, refined.bestFitness, 1e-9)
    }

    private fun sampleConfig(): GAConfig {
        return GAConfig(
            popSize = 10,
            generations = 5,
            pc = 0.7,
            pm = 0.08,
            min = ParamsCandidate(0.3, 55, 0.2, 60),
            max = ParamsCandidate(0.5, 70, 0.4, 75),
            maximize = true
        )
    }
}
